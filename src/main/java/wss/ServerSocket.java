/*
    OA4J - WinCC Open Architecture for Java
    Copyright (C) 2017 Andreas Vogler

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package wss;

import at.rocworks.oa4j.base.*;
import at.rocworks.oa4j.var.DynVar;

import at.rocworks.oa4j.var.Variable;
import com.google.gson.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ServerSocket implements WebSocketListener
{
    public static Integer mutex = 0;

    private Session session;

    private String username, password;

    private HashMap<Long, JDpConnect> dpConnects = new HashMap<>();
    private HashMap<Long, JDpQueryConnect> dpQueryConnects = new HashMap<>();

    final LinkedBlockingQueue<Messages.Message> mailbox = new LinkedBlockingQueue<>();
    //private PublishSubject<Messages.message> mailbox = PublishSubject.create();

    Gson onWebSocketTextGson = Messages.Gson();
    Gson mailboxThreadGson =  Messages.Gson();

    public void onWebSocketConnect(Session session)
    {
        this.session=session;
        JDebug.out.info("WebSocket Connect: {"+session+"}");
        Map<String, List<String>> parameter = session.getUpgradeRequest().getParameterMap();
        JDebug.out.info("parameter: "+parameter.toString());
        if (!parameter.containsKey("username") || parameter.get("username").size()==0 ||
            !parameter.containsKey("password") || parameter.get("password").size()==0)
            session.close(4000, "no username and/or password!");

        String username=parameter.get("username").get(0);
        String password=parameter.get("password").get(0);
        int chk1;
        boolean chk2=false;
        if ((chk1=JClient.checkPassword(username, password))==0 &&
            (chk2=JClient.setUserId(username, password))) {
            new Thread(()-> mailboxThread()).start();
            this.username=username;
            this.password=password;
            JDebug.out.info("connected as "+username);
            //mailbox.asObservable().subscribe(this::sendMessage);
        } else {
            JDebug.out.info("invalid username and/or password! "+chk1+"/"+(chk2?"true":"false"));
            session.close(4000, "invalid username and/or password!");
        }
    }

    public void onWebSocketClose(int statusCode, String reason)
    {
        JDebug.out.info("WebSocket Close: {"+statusCode+"} - {"+reason+"}");
        dpConnects.forEach((k, c)->c.disconnect());
        dpQueryConnects.forEach((k, c)->c.disconnect());
    }

    public void onWebSocketError(Throwable cause)
    {
        JDebug.out.warning("WebSocket Error: "+cause.getMessage());
        /*
        StringWriter sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        String sStackTrace = sw.toString();
        JDebug.out.warning("WebSocket Error: "+sStackTrace);
        */
    }

    public void onWebSocketText(String message)
    {
        if (session.isOpen()) {
            //JDebug.out.info("Got onMessage: "+message);
            Messages.Message msg = onWebSocketTextGson.fromJson(message, Messages.Message.class);
            synchronized (ServerSocket.mutex) {
                JManager.getInstance().enqueueTask(()->JManager.getInstance().setUserId(username, password));
                if (msg.dpSet != null)
                    cmdDpSet(msg.dpSet);
                else if (msg.dpGet != null)
                    cmdDpGet(msg.dpGet);
                else if (msg.dpConnect != null)
                    cmdDpConnect(msg.dpConnect);
                else if (msg.dpDisconnect != null)
                    cmdDpDisconnect(msg.dpDisconnect);
                else if (msg.dpQueryConnect != null)
                    cmdDpQueryConnect(msg.dpQueryConnect);
                else if (msg.dpQueryDisconnect != null)
                    cmdDpQueryDisconnect(msg.dpQueryDisconnect);
                else if (msg.dpGetPeriod != null)
                    cmdDpGetPeriod(msg.dpGetPeriod);
                else {
                    JDebug.out.warning("unknown message: " + message);
                }
            }
        }
    }

    @Override
    public void onWebSocketBinary(byte[] arg0, int arg1, int arg2)
    {
        /* ignore */
    }

    //------------------------------------------------------------------------------------------------------------------
    private void mailboxThread() {
        while (session.isOpen()) {
            try {
                Messages.Message message = mailbox.poll(100, TimeUnit.MILLISECONDS);
                if (message!=null) {
                    String json = mailboxThreadGson.toJson(message);
                    //synchronized (session) {
                        session.getRemote().sendString(json, null);
                    //}
                }
            } catch (InterruptedException e) {
                JDebug.StackTrace(Level.SEVERE, e);
            }
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpGet(Messages.DpGet cmd) {
        JDpGet dpGet = JClient.dpGet();
        dpGet.async();
        cmd.dps.forEach((dp)->dpGet.add(dp));
        dpGet.action((JDpMsgAnswer hl)-> dpGetHotlink(cmd.id, hl));
        int ret = dpGet.send().await().getRetCode();
        if (ret!=0) mailbox.add(new Messages.Message().DpGetResult(cmd.id, ret));
    }

    private void dpGetHotlink(Long Id, JDpMsgAnswer hotlink) {
        JsonObject jsonValues = new JsonObject();
        hotlink.forEach((item)->jsonValues.add(item.getDpName(), var2json(item.getVariable())));
        mailbox.add(new Messages.Message().DpGetResult(Id, jsonValues));
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpSet(Messages.DpSet cmd) {
        JDpSet dpSet = JClient.dpSet();
        cmd.values.forEach((value)->dpSet.add(value.dp, value.value));
        if (cmd.timestamp !=null) {
            dpSet.timed(cmd.timestamp);
        }
        if (cmd.wait !=null && cmd.wait) {
            int ret = dpSet.send().await().getRetCode();
            mailbox.add(new Messages.Message().Response(cmd.id, ret, ""));
        } else {
            dpSet.send();
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpConnect(Messages.DpConnect cmd) {
        JDpConnect connect = JClient.dpConnect();
        connect.async();
        cmd.dps.forEach((dp)->connect.add(dp));
        connect.action((JDpHLGroup hl)-> dpConnectHotlink(cmd.id, hl));
        if (cmd.answer !=null && cmd.answer) connect.action((JDpMsgAnswer hl)->dpConnectHotlink(cmd.id, hl));
        int ret = connect.connect().getRetCode();
        if (ret==0) dpConnects.put(cmd.id, connect);
        else mailbox.add(new Messages.Message().DpConnectResult(cmd.id, ret));
    }

    private void dpConnectHotlink(Long Id, JDpVCGroup hotlink) {
        //JDebug.out.info(hotlink.toString());
        JsonObject jsonValues = new JsonObject();
        hotlink.forEach((item)->jsonValues.add(item.getDpName(), var2json(item.getVariable())));
        mailbox.add(new Messages.Message().DpConnectResult(Id, jsonValues));
    }

    public void cmdDpDisconnect(Messages.DpDisconnect cmd) {
        JDpConnect connect = dpConnects.get(cmd.id);
        if (connect!=null) connect.disconnect();
        dpConnects.remove(cmd.id);
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpQueryConnect(Messages.DpQueryConnect cmd) {
        JDpQueryConnect connect = JClient.dpQueryConnectSingle(cmd.query);
        connect.async(true);
        connect.action((JDpHLGroup hl)-> dpQueryConnectHotlink(cmd.id, hl));
        if (cmd.answer !=null && cmd.answer) connect.action((JDpMsgAnswer hl)-> dpQueryConnectHotlink(cmd.id, hl));
        int ret = connect.connect().getRetCode();
        if (ret==0) dpQueryConnects.put(cmd.id, connect);
        else mailbox.add(new Messages.Message().DpQueryConnectResult(cmd.id, ret));
    }

    private void dpQueryConnectHotlink(Long Id, JDpVCGroup hotlink) {
        //JDebug.out.info(hotlink.toString());
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
            mailbox.add(new Messages.Message().DpQueryConnectResult(Id, jsonHeader, jsonValues));
        }
    }

    public void cmdDpQueryDisconnect(Messages.DpQueryDisconnect cmd) {
        JDebug.out.info("dpQueryDisconnect: "+cmd.id);
        JDpQueryConnect connect = dpQueryConnects.get(cmd.id);
        if (connect!=null) connect.disconnect();
        dpQueryConnects.remove(cmd.id);
    }

    //------------------------------------------------------------------------------------------------------------------
    private void cmdDpGetPeriod(Messages.DpGetPeriod cmd) {
        JDpGetPeriod dpGetPeriod = JClient.dpGetPeriod(cmd.t1, cmd.t2, cmd.count!=null ? cmd.count : 0);
        dpGetPeriod.async();
        cmd.dps.forEach((dp)->dpGetPeriod.add(dp));
        dpGetPeriod.action((JDpMsgAnswer hl)-> dpGetPeriodHotlink(cmd.id, cmd.ts, hl));
        int ret = dpGetPeriod.send().await().getRetCode();
        if (ret!=0) mailbox.add(new Messages.Message().DpGetPeriodResult(cmd.id, ret));
    }

    private void dpGetPeriodHotlink(Long id, Integer ts, JDpMsgAnswer hotlink) {
        //JDebug.out.info(hotlink.toString());
        SimpleDateFormat sdf = new SimpleDateFormat(Messages.DATEFORMAT);
        JsonObject jsonValues = new JsonObject();
        hotlink.forEach((item)->{
            JsonArray jval;
            if (jsonValues.has(item.getDpName())) { // already exists
                jval=jsonValues.get(item.getDpName()).getAsJsonArray();
            } else { // add (empty) array for dp
                jval=new JsonArray();
                jsonValues.add(item.getDpName(), jval);
                if (ts==3||ts==4) {
                    jval.add(new JsonArray()); // times
                    jval.add(new JsonArray()); // values
                }
            }
            JsonElement val=var2json(item.getVariable());
            if (ts==null || ts==0) { // no ts
                jval.add(val);
            } else if (ts==1) { // [[t,v][t,v]...]
                JsonArray jrec = new JsonArray();
                jrec.add(sdf.format(item.getDate()));
                jrec.add(val);
                jval.add(jrec);
            } else if (ts==2) { // [[t,v][t,v]...]
                JsonArray jrec = new JsonArray();
                jrec.add(item.getTime());
                jrec.add(val);
                jval.add(jrec);
            } else if (ts==3) { // [[t,t,t,t,...][v,v,v,v,...]]
                jval.get(0).getAsJsonArray().add(item.getTime()); // ms
                jval.get(1).getAsJsonArray().add(val);
            } else if (ts==4) { // [[t,t,t,t,...][v,v,v,v,...]]
                jval.get(0).getAsJsonArray().add(sdf.format(item.getDate())); // ISO8601
                jval.get(1).getAsJsonArray().add(val);
            }
        });
        mailbox.add(new Messages.Message().DpGetPeriodResult(id, jsonValues));
    }

    //------------------------------------------------------------------------------------------------------------------
    private JsonElement var2json(Variable var) {
        if (var==null) return JsonNull.INSTANCE;
        switch (var.isA()) {
            case BitVar:
                return new JsonPrimitive(var.getBitVar().getValue());
            case IntegerVar:
                return new JsonPrimitive(var.getIntegerVar().getValue());
            case UIntegerVar:
                return new JsonPrimitive(var.getUIntegerVar().getValue());
            case LongVar:
                return new JsonPrimitive(var.getLongVar().getValue());
            case ULongVar:
                return new JsonPrimitive(var.getUIntegerVar().getValue());
            case FloatVar:
                return new JsonPrimitive(var.getFloatVar().getValue());
            case Bit32Var:
                return new JsonPrimitive(var.getBit32Var().getValue());
            case Bit64Var:
                return new JsonPrimitive(var.getBit64Var().getValue());
            case BlobVar:
                return JsonNull.INSTANCE;
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
}