package wss;

import java.io.IOException;
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
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * Basic Echo Client Socket
 */
// maxTextMessageSize = 64 * 1024
@WebSocket(maxTextMessageSize = 128 * 1024)
public class ClientSocket {
    private final CountDownLatch connectedLatch;
    private final CountDownLatch closeLatch;

    private WebSocketClient client = new WebSocketClient();
    private ClientUpgradeRequest request = new ClientUpgradeRequest();
    private Session session;

    final LinkedBlockingQueue<Messages.Message> mailbox = new LinkedBlockingQueue<>();

    private Gson onMessageGson =  Messages.Gson();
    private Gson sendMessageGson =  Messages.Gson();
    private Gson mailboxThreadGson =  Messages.Gson();


    private static class Tuple<X, Y> {
        public final X _1;
        public final Y _2;
        public Tuple(X _1, Y _2) {
            this._1 = _1;
            this._2 = _2;
        }
    }

    public static interface Callback {
        public void callback(Messages.Message message);
    }

    private HashMap<Long, Tuple<Messages.Message, Callback>> dpConnects = new HashMap<>();
    private HashMap<Long, Tuple<Messages.Message, Callback>> dpQueryConnects = new HashMap<>();

    private HashMap<Long, Tuple<Messages.Message, Callback>> dpGets = new HashMap<>();
    private HashMap<Long, Tuple<Messages.Message, Callback>> dpGetPeriods = new HashMap<>();

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

    public void onConnected() {}
    public void onMessage(Messages.Message message) {}

    @OnWebSocketClose
    public void onWebSocketClose(int statusCode, String reason) {
        System.out.printf("Connection closed: %d - %s%n", statusCode, reason);
        this.closeLatch.countDown();
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        System.out.printf("Got connect: %s%n", session);
        this.session = session;
        this.connectedLatch.countDown();
        new Thread(()-> mailboxThread()).start();
        onConnected();
    }

    @OnWebSocketMessage
    public void onWebSocketMessage(String message) {
        System.out.printf("Got msg: %s%n", message);
        Messages.Message msg = onMessageGson.fromJson(message, Messages.Message.class);
        if (msg == null) {
        } else if (msg.dpConnectResult != null) {
            Tuple<Messages.Message, Callback> x = dpConnects.get(msg.dpConnectResult.id);
            if (x != null) x._2.callback(msg);
            if (msg.dpConnectResult.error!=0) dpConnects.remove(msg.dpConnectResult.id);
        } else if (msg.dpQueryConnectResult != null) {
            Tuple<Messages.Message, Callback> x = dpQueryConnects.get(msg.dpQueryConnectResult.id);
            if (x != null) x._2.callback(msg);
            if (msg.dpQueryConnectResult.error!=0) dpQueryConnects.remove(msg.dpQueryConnect.id);
        } else if (msg.dpGetResult != null) {
            Tuple<Messages.Message, Callback> x = dpGets.get(msg.dpGetResult.id);
            if (x != null) x._2.callback(msg);
            dpGets.remove(msg.dpGetResult.id);
        } else if (msg.dpGetPeriodResult != null) {
            Tuple<Messages.Message, Callback> x = dpGetPeriods.get(msg.dpGetPeriodResult.id);
            if (x != null) x._2.callback(msg);
            dpGetPeriods.remove(msg.dpGetPeriodResult.id);
        }
        onMessage(msg);
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
        dpConnects.put(msg.dpConnect.id, new Tuple(msg, callback));
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
        dpQueryConnects.put(msg.dpQueryConnect.id, new Tuple(msg, callback));
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
        dpGets.put(msg.dpGet.id, new Tuple(msg, callback));
    }

    //------------------------------------------------------------------------------------------------------------------
    public void dpGetPeriod(List<String> dps, Date t1, Date t2, Integer count, Callback callback) {
        Messages.Message msg = new Messages.Message().DpGetPeriod(dps, t1, t2, count);
        mailbox.add(msg);
        dpGetPeriods.put(msg.dpGetPeriod.id, new Tuple(msg, callback));
    }
}