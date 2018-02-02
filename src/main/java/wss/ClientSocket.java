package wss;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
@WebSocket(maxTextMessageSize = 64 * 1024)
public class ClientSocket {
    private final CountDownLatch connectedLatch;
    private final CountDownLatch closeLatch;


    private WebSocketClient client = new WebSocketClient();
    private ClientUpgradeRequest request = new ClientUpgradeRequest();
    private Session session;

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
        this.session = null;
        this.closeLatch.countDown();
    }

    @OnWebSocketConnect
    public void onWebSocketConnect(Session session) {
        System.out.printf("Got connect: %s%n", session);
        this.session = session;
        this.connectedLatch.countDown();
        onConnected();
    }

    Gson onMessageGson = new GsonBuilder().setDateFormat(Messages.DATEFORMAT).create();

    @OnWebSocketMessage
    public void onWebSocketMessage(String message) {
        //System.out.printf("Got msg: %s%n", message);
        Messages.Message msg = onMessageGson.fromJson(message, Messages.Message.class);
        if (msg.DpConnectResult!=null) {
            Tuple<Messages.Message, Callback> x = dpConnects.get(msg.DpConnectResult.Id);
            if (x!=null) x._2.callback(msg);
        }
        else if (msg.DpQueryConnectResult!=null) {
            Tuple<Messages.Message, Callback> x = dpQueryConnects.get(msg.DpQueryConnectResult.Id);
            if (x!=null) x._2.callback(msg);
        }
        onMessage(msg);
    }

    Gson sendMessageGson = new GsonBuilder().setDateFormat(Messages.DATEFORMAT).create();
    public boolean sendMessage(Messages.Message message) throws IOException {
        if (session!=null) {
            session.getRemote().sendString(sendMessageGson.toJson(message));
            return true;
        } else {
            return false;
        }
    }

    public void close() throws Exception {
        if (session!=null)
            session.close(StatusCode.NORMAL,"");
         client.stop();
    }

    //------------------------------------------------------------------------------------------------------------------
    public boolean dpConnect(List<String> dps, Callback callback) {
        return dpConnect(dps, null, callback);
    }
    public boolean dpConnect(List<String> dps, Boolean answer, Callback callback) {
        Messages.Message msg = new Messages.Message().DpConnect(dps, answer);
        try {
            if (sendMessage(msg)) {
                dpConnects.put(msg.DpConnect.Id, new Tuple(msg, callback));
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public boolean dpQueryConnect(String query, Callback callback) {
        return dpQueryConnect(query, null, callback);
    }
    public boolean dpQueryConnect(String query, Boolean answer, Callback callback) {
        Messages.Message msg = new Messages.Message().DpQueryConnect(query, answer);
        try {
            if (sendMessage(msg)) {
                dpQueryConnects.put(msg.DpQueryConnect.Id, new Tuple(msg, callback));
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public boolean dpSet(List<Messages.DpVCItem> values) {
        return dpSet(values, null, null);
    }
    public boolean dpSet(List<Messages.DpVCItem> values, Date timestamp, Boolean wait) {
        Messages.Message msg = new Messages.Message().DpSet(values, timestamp, wait);
        try {
            return sendMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean dpSet(String dp, Object value) {
        return dpSet(dp, value, null, null);
    }
    public boolean dpSet(String dp, Object value, Date timestamp, Boolean wait) {

        Messages.Message msg = new Messages.Message().DpSet(Arrays.asList(new Messages.DpVCItem(dp, value)), timestamp, wait);
        try {
            return sendMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}