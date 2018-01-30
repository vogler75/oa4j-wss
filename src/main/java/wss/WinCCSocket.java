package wss;

import at.rocworks.oa4j.base.*;
import at.rocworks.oa4j.var.DynVar;

import com.google.gson.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WinCCSocket implements WebSocketListener
{
    private Session outbound;
    private ArrayList<JDpQueryConnect> dpqc = new ArrayList<>();

    public void onWebSocketConnect(Session session)
    {
        this.outbound = session;
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
        this.outbound = null;
        JDebug.out.info("WebSocket Close: {"+statusCode+"} - {"+reason+"}");
        dpqc.forEach((c)->c.disconnect());
    }

    public void onWebSocketError(Throwable cause)
    {
        JDebug.out.warning("WebSocket Error: "+cause);
    }

    public void onWebSocketText(String message)
    {
        if ((outbound != null) && (outbound.isOpen()))
        {
            //JDebug.out.info("Got message: "+message);

            JsonElement jelement = new JsonParser().parse(message);
            JsonObject jobject = jelement.getAsJsonObject();
            JsonElement jcmd;

            //----------------------------------------------------------------------------------------------------------
            jcmd=jobject.get("dpSet");
            if (jcmd!=null && jcmd.isJsonObject()) {
                cmdDpSet(jcmd);
            }

            //----------------------------------------------------------------------------------------------------------
            jcmd=jobject.get("dpQueryConnect");
            if (jcmd!=null && jcmd.isJsonObject()) {
                cmdDpQueryConnect(jcmd);
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] arg0, int arg1, int arg2)
    {
        /* ignore */
    }

    public static class IODpVCItem {
        String Dp;
        Object Value;
    }

    public static class IOCmdDpSet {
        Long Id;
        List<IODpVCItem> Values;
    }

    public static class IOCmdDpQueryConnect {
        Long Id;
        String Query;
    }

    public static class IOResDpQueryConnect {
        Long Id;
        JsonArray Header;
        JsonArray Result;
        public IOResDpQueryConnect(Long Id, JsonArray Header, JsonArray Result) {
            this.Id=Id;
            this.Header=Header;
            this.Result=Result;
        }
    }

    public static class IORet {
        Long Id;
        Integer Code;
        String Message;
        public IORet(Long Id, Integer Code) {
            this(Id, Code, null);
        }
        public IORet(Long Id, Integer Code, String Message) {
            this.Id=Id;
            this.Code=Code;
            this.Message=Message;
        }
    }

    private void cmdDpSet(JsonElement jcmd) {
        Gson gson = new GsonBuilder().create();
        IOCmdDpSet cmd = gson.fromJson(jcmd, IOCmdDpSet.class);
        JDpSet dpSet = JClient.dpSet();
        cmd.Values.forEach((value)->dpSet.add(value.Dp, value.Value));
        int ret = dpSet.await().getRetCode();
        //outbound.getRemote().sendString(gson.toJson(new IORet(cmd.Id, ret)),null);
    }

    private void cmdDpQueryConnect(JsonElement jcmd) {
        Gson gson = new GsonBuilder().create();
        IOCmdDpQueryConnect cmd = gson.fromJson(jcmd, IOCmdDpQueryConnect.class);
        JDpQueryConnect connect = JClient.dpQueryConnectSingle(cmd.Query);
        connect.action((JDpMsgAnswer hl)->handleHotLink(cmd.Id, hl));
        connect.action((JDpHLGroup hl)->handleHotLink(cmd.Id, hl));
        int ret = connect.connect().getRetCode();
        if (ret==0) dpqc.add(connect);
        outbound.getRemote().sendString(gson.toJson(new IORet(cmd.Id, ret)),null);
    }

    private void handleHotLink(Long Id, JDpVCGroup hotlink) {
        Gson gson = new GsonBuilder().create();
        // first item is the header, it is a dyn of the selected attributes
        if ( hotlink.getNumberOfItems() > 0 ) {
            JsonArray jsonHeader = null;
            JsonArray jsonResult = new JsonArray();

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
                    switch (row.get(j).isA()) {
                        case BitVar: jsonArray.add(row.get(j).getBitVar().getValue()); break;
                        case IntegerVar:
                        case UIntegerVar:
                        case LongVar:
                        case ULongVar:
                        case FloatVar: jsonArray.add(row.get(j).getFloatVar().getValue()); break;
                        case Bit32Var:
                        case Bit64Var:
                        case BlobVar:
                        case TextVar: jsonArray.add(row.get(j).getTextVar().getValue()); break;
                        case TimeVar: jsonArray.add(row.get(j).getTimeVar().getValue().getTime()); break;
                        case DpIdentifierVar: jsonArray.add(row.get(j).getDpIdentifierVar().getName()); break;
                    }
                }
                if (i==0) jsonHeader=jsonArray;
                else jsonResult.add(jsonArray);
            }
            if (outbound!=null)
                outbound.getRemote().sendString(gson.toJson(new IOResDpQueryConnect(Id, jsonHeader, jsonResult)),null);
        }
    }
}