package wss;

import at.rocworks.oa4j.base.*;
import at.rocworks.oa4j.var.DynVar;

import at.rocworks.oa4j.var.Variable;
import com.google.gson.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerSocket implements WebSocketListener
{
    private Session session;
    private ArrayList<JDpConnect> dpConnects = new ArrayList<>();
    private ArrayList<JDpQueryConnect> dpQueryConnects = new ArrayList<>();

    public void onWebSocketConnect(Session session)
    {
        this.session = session;
        JDebug.out.info("WebSocket Connect: {"+session+"}");
        Map<String, List<String>> parameter = session.getUpgradeRequest().getParameterMap();
        JDebug.out.info("parameter: "+parameter.toString());
        if (!parameter.containsKey("username") || parameter.get("username").size()==0 ||
            !parameter.containsKey("password") || parameter.get("password").size()==0)
            session.close();

        String username=parameter.get("username").get(0);
        String password=parameter.get("password").get(0);
        if (JManager.getInstance().checkPassword(username, password)==0 &&
            JManager.getInstance().setUserId(username, password)) {
            JDebug.out.info("connected as "+username);
        } else {
            JDebug.out.info("invalid username and/or password!");
            session.close();
        }
    }

    public void onWebSocketClose(int statusCode, String reason)
    {
        this.session = null;
        JDebug.out.info("WebSocket Close: {"+statusCode+"} - {"+reason+"}");
        dpQueryConnects.forEach((c)->c.disconnect());
    }

    public void onWebSocketError(Throwable cause)
    {
        JDebug.out.warning("WebSocket Error: "+cause);
    }

    Gson onWebSocketTextGson = new GsonBuilder().setDateFormat(Messages.DATEFORMAT).create();
    public void onWebSocketText(String message)
    {
        if ((session != null) && (session.isOpen()))
        {
            //JDebug.out.info("Got onMessage: "+onMessage);

            Messages.Message msg = onWebSocketTextGson.fromJson(message, Messages.Message.class);

            if (msg.DpSet!=null) {
                cmdDpSet(msg.DpSet);
            }

            if (msg.DpConnect!=null) {
                cmdDpConnect(msg.DpConnect);
            }

            if (msg.DpQueryConnect!=null) {
                cmdDpQueryConnect(msg.DpQueryConnect);
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] arg0, int arg1, int arg2)
    {
        /* ignore */
    }

    Gson sendMessageGson = new GsonBuilder().setDateFormat(Messages.DATEFORMAT).create();
    private void sendMessage(Messages.Message message) {
        if (session !=null) {
            session.getRemote().sendString(sendMessageGson.toJson(message),null);
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpSet(Messages.DpSet cmd) {
        JDpSet dpSet = JClient.dpSet();
        cmd.Values.forEach((value)->dpSet.add(value.Dp, value.Value));
        if (cmd.Timestamp!=null) {
            dpSet.timed(cmd.Timestamp);
        }
        if (cmd.Wait!=null && cmd.Wait) {
            int ret = dpSet.send().await().getRetCode();
            sendMessage(new Messages.Message().Response(cmd.Id, ret, ""));
        } else {
            dpSet.send();
        }
    }

    private void cmdDpConnect(Messages.DpConnect cmd) {
        JDpConnect connect = JClient.dpConnect();
        cmd.Dps.forEach((dp)->connect.add(dp));
        connect.action((JDpHLGroup hl)-> dpConnectHotlink(cmd.Id, hl));
        if (cmd.Answer!=null && cmd.Answer) connect.action((JDpMsgAnswer hl)->dpConnectHotlink(cmd.Id, hl));
        int ret = connect.connect().getRetCode();
        if (ret==0) dpConnects.add(connect);
        sendMessage(new Messages.Message().Response(cmd.Id, ret, ""));
    }

    private void cmdDpQueryConnect(Messages.DpQueryConnect cmd) {
        JDpQueryConnect connect = JClient.dpQueryConnectSingle(cmd.Query);
        connect.action((JDpHLGroup hl)-> dpQueryConnectHotlink(cmd.Id, hl));
        if (cmd.Answer!=null && cmd.Answer) connect.action((JDpMsgAnswer hl)-> dpQueryConnectHotlink(cmd.Id, hl));
        int ret = connect.connect().getRetCode();
        if (ret==0) dpQueryConnects.add(connect);
        sendMessage(new Messages.Message().Response(cmd.Id, ret, ""));
    }

    //------------------------------------------------------------------------------------------------------------------
    private JsonElement var2json(Variable var) {
        switch (var.isA()) {
            case BitVar:
                return new JsonPrimitive(var.getBitVar().getValue());
            case IntegerVar:
            case UIntegerVar:
            case LongVar:
            case ULongVar:
            case FloatVar:
                return new JsonPrimitive(var.getFloatVar().getValue());
            case Bit32Var:
            case Bit64Var:
            case BlobVar:
            case TextVar:
                return new JsonPrimitive(var.getTextVar().getValue());
            case TimeVar:
                return new JsonPrimitive(var.getTimeVar().getValue().getTime());
            case DpIdentifierVar:
                return new JsonPrimitive(var.getDpIdentifierVar().getName());
            case DynVar:
                JsonArray arr = new JsonArray();
                var.getDynVar().forEach((el)->arr.add(var2json(el)));
                return arr;
            default:
                return null;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    private void dpConnectHotlink(Long Id, JDpVCGroup hotlink) {
        // first item is the header, it is a dyn of the selected attributes
        JsonObject jsonValues = new JsonObject();
        hotlink.forEach((item)->jsonValues.add(item.getDpName(), var2json(item.getVariable())));
        sendMessage(new Messages.Message().DpConnectResult(Id, jsonValues));
    }

    private void dpQueryConnectHotlink(Long Id, JDpVCGroup hotlink) {
        // first item is the header, it is a dyn of the selected attributes
        if ( hotlink.getNumberOfItems() > 1 ) {
            JsonArray jsonHeader = null;
            JsonArray jsonValues = new JsonArray();

            // second item contains the result data
            JDpVCItem data = hotlink.getItem(1);

            // the data item is a list of list
            // row 1: dpname | column-1 | column-2 | ...
            // row 2: dpname | column-1 | column-2 | ...
            // .....
            // row n: dpname | column-1 | column-2 | ...
            DynVar list = (DynVar)data.getVariable();
            for ( int i=0; i<list.size(); i++ ) {
                // one data item in the list is also a list
                DynVar row = (DynVar)list.get(i);
                if ( row.size() == 0 ) continue;

                // the row contains the selected columns/values in a list
                JsonArray jsonArray = new JsonArray();
                for ( int j=0; j<row.size(); j++ ) {
                    jsonArray.add(var2json(row.get(j)));
                }
                if (i==0) jsonHeader=jsonArray;
                else jsonValues.add(jsonArray);
            }
            sendMessage(new Messages.Message().DpQueryConnectResult(Id, jsonHeader, jsonValues));
        }
    }


}