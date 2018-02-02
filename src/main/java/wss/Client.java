package wss;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import at.rocworks.oa4j.base.JDebug;
import com.google.gson.JsonArray;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

public class Client {

    public static void main(String[] args)
    {
        String url = "ws://localhost:8080/winccoa?username=demo&password=demo";
        if (args.length > 0) url = args[0];

        ClientSocket client = new ClientSocket();
        try
        {
            System.out.printf("Connecting to : %s%n",url);
            client.open(url);
            client.awaitConnected();
            client.dpConnect(Arrays.asList("System1:pump_00001.value.speed"), true, (message)->{
                //System.out.println("dpConnect: "+message.DpConnectResult.Values.toString());
                message.DpConnectResult.Values.getAsJsonObject().keySet().forEach((dp)->{
                    String dpset = dp.split(":")[1];
                    Double value = message.DpConnectResult.Values.get(dp).getAsDouble();
                    //System.out.println(dpset+" => "+value);
                    client.dpSet(dpset, value+1.0);
                });

            });
            /*
            client.dpQueryConnect("SELECT '_online.._value' FROM 'pump_000*.value.speed'", true, (message)->{
                System.out.println("dpQueryConnect: "+message.DpQueryConnectResult.Values.toString());
            });
            */

            //Future<Void> f = session.getRemote().sendStringByFuture(gson.toJson(msg1));
            //f.get(2,TimeUnit.SECONDS); // wait for send to complete.

            // wait for closed socket connection.
            client.awaitClose();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        finally
        {
            try
            {
                client.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}