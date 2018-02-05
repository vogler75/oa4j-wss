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

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import at.rocworks.oa4j.base.JDebug;
import com.google.gson.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

//@WebSocket(maxTextMessageSize = 128 * 1024)
public class ClientSocket implements WebSocketListener {
    private final CountDownLatch connectedLatch;
    private final CountDownLatch closeLatch;

    private WebSocketClient client = new WebSocketClient();
    private ClientUpgradeRequest request = new ClientUpgradeRequest();
    protected Session session;

    final LinkedBlockingQueue<Messages.Message> mailbox = new LinkedBlockingQueue<>();

    private Gson onMessageGson =  Messages.Gson();
    private Gson mailboxThreadGson =  Messages.Gson();

    public static interface Callback {
        public void callback(Messages.Message message);
    }

    private HashMap<Long, Messages.Tuple<Messages.Message, Callback>> dpConnects = new HashMap<>();
    private HashMap<Long, Messages.Tuple<Messages.Message, Callback>> dpQueryConnects = new HashMap<>();

    private HashMap<Long, Messages.Tuple<Messages.Message, Callback>> dpGets = new HashMap<>();
    private HashMap<Long, Messages.Tuple<Messages.Message, Callback>> dpGetPeriods = new HashMap<>();

    public ClientSocket() {
        this.closeLatch = new CountDownLatch(1);
        this.connectedLatch = new CountDownLatch(1);
    }

    public void open(String url) throws Exception {
        URI uri = new URI(url);
        client.start();
        client.connect(this, uri, request);
    }

    public void awaitConnected() throws InterruptedException {
        this.connectedLatch.await();
    }

    public void awaitClose() throws InterruptedException {
        this.closeLatch.await();
    }

    public void onWebSocketClose(int statusCode, String reason) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
        this.closeLatch.countDown();
    }

    public void onWebSocketConnect(Session session) {
        System.out.printf("Got connect: %s%n", session);
        this.session = session;
        this.connectedLatch.countDown();
        new Thread(()-> mailboxThread()).start();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        System.out.println("WebSocket Error: "+cause.getMessage());
    }

    @Override
    public void onWebSocketText(String message) {
        //System.out.printf("Got msg: %s%n", message);
        Messages.Message msg = onMessageGson.fromJson(message, Messages.Message.class);
        if (msg == null) {
        } else if (msg.dpConnectResult != null) {
            Messages.Tuple<Messages.Message, Callback> x = dpConnects.get(msg.dpConnectResult.id);
            if (x != null) x._2.callback(msg);
            if (msg.dpConnectResult.error!=0) dpConnects.remove(msg.dpConnectResult.id);
        } else if (msg.dpQueryConnectResult != null) {
            Messages.Tuple<Messages.Message, Callback> x = dpQueryConnects.get(msg.dpQueryConnectResult.id);
            if (x != null) x._2.callback(msg);
            if (msg.dpQueryConnectResult.error!=0) dpQueryConnects.remove(msg.dpQueryConnect.id);
        } else if (msg.dpGetResult != null) {
            Messages.Tuple<Messages.Message, Callback> x = dpGets.get(msg.dpGetResult.id);
            if (x != null) x._2.callback(msg);
            dpGets.remove(msg.dpGetResult.id);
        } else if (msg.dpGetPeriodResult != null) {
            Messages.Tuple<Messages.Message, Callback> x = dpGetPeriods.get(msg.dpGetPeriodResult.id);
            if (x != null) x._2.callback(msg);
            dpGetPeriods.remove(msg.dpGetPeriodResult.id);
        }
    }

    @Override
    public void onWebSocketBinary(byte[] bytes, int i, int i1) {

    }

    //------------------------------------------------------------------------------------------------------------------
    private void mailboxThread() {
        while (session.isOpen()) {
            try {
                Messages.Message message = mailbox.poll(100, TimeUnit.MILLISECONDS);
                if (message!=null) {
                    String json = mailboxThreadGson.toJson(message);
                    session.getRemote().sendString(json, null);
                }
            } catch (InterruptedException e) {
                JDebug.StackTrace(Level.SEVERE, e);
            }
        }
    }

    public void close() throws Exception {
        if (session.isOpen())
            session.close(StatusCode.NORMAL,"");
         client.stop();
    }

    //------------------------------------------------------------------------------------------------------------------
    public void dpSet(List<Messages.DpValue> values) {
        dpSet(values, null, null);
    }
    public void dpSet(List<Messages.DpValue> values, Date timestamp, Boolean wait) {
        Messages.Message msg = new Messages.Message().DpSet(values, timestamp, wait);
        mailbox.add(msg);
    }

    public void dpSet(String dp, Object value) {
        dpSet(dp, value, null, null);
    }
    public void dpSet(String dp, Object value, Date timestamp, Boolean wait) {
        Messages.Message msg = new Messages.Message().DpSet(Arrays.asList(new Messages.DpValue(dp, value)), timestamp, wait);
        mailbox.add(msg);
    }

    //------------------------------------------------------------------------------------------------------------------
    public void dpConnect(List<String> dps, Callback callback) {
        dpConnect(dps, null, callback);
    }
    public Messages.DpConnect dpConnect(List<String> dps, Boolean answer, Callback callback) {
        Messages.Message msg = new Messages.Message().DpConnect(dps, answer);
        mailbox.add(msg);
        dpConnects.put(msg.dpConnect.id, new Messages.Tuple(msg, callback));
        return msg.dpConnect;
    }

    public void dpDisconnect(Messages.DpConnect message) {
        Messages.Message msg = new Messages.Message().DpDisconnect(message.id);
        if (msg.dpDisconnect.id!=null) mailbox.add(msg);
    }

    //------------------------------------------------------------------------------------------------------------------
    public Messages.DpQueryConnect dpQueryConnect(String query, Callback callback) {
        return dpQueryConnect(query, null, callback);
    }
    public Messages.DpQueryConnect dpQueryConnect(String query, Boolean answer, Callback callback) {
        Messages.Message msg = new Messages.Message().DpQueryConnect(query, answer);
        mailbox.add(msg);
        dpQueryConnects.put(msg.dpQueryConnect.id, new Messages.Tuple(msg, callback));
        return msg.dpQueryConnect;
    }

    public void dpQueryDisconnect(Messages.DpQueryConnect message) {
        Messages.Message msg = new Messages.Message().DpQueryDisconnect(message.id);
        if (msg.dpQueryDisconnect.id!=null) mailbox.add(msg);
    }

    //------------------------------------------------------------------------------------------------------------------
    public void dpGet(List<String> dps, Callback callback) {
        Messages.Message msg = new Messages.Message().DpGet(dps);
        mailbox.add(msg);
        dpGets.put(msg.dpGet.id, new Messages.Tuple(msg, callback));
    }

    //------------------------------------------------------------------------------------------------------------------
    public void dpGetPeriod(List<String> dps, Date t1, Date t2, Integer count, Callback callback) {
        Messages.Message msg = new Messages.Message().DpGetPeriod(dps, t1, t2, count);
        mailbox.add(msg);
        dpGetPeriods.put(msg.dpGetPeriod.id, new Messages.Tuple(msg, callback));
    }
}