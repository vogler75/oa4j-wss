package wss;

import java.text.SimpleDateFormat;
import java.util.Arrays;

import com.google.gson.JsonArray;

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


            client.dpConnect(Arrays.asList("System1:pump_00001.value.speed", "System1:Test1."), true, (message)->{
                //System.out.println("dpConnect: "+message.DpConnectResult.values.toString());
                message.dpConnectResult.values.getAsJsonObject().keySet().forEach((dp)->{
                    String dpname = dp.split(":")[1];
                    Double value = message.dpConnectResult.values.get(dp).getAsDouble();
                    System.out.println("dpConnect: "+dpname+" => "+value);
                    //client.dpSet(dpname, value+1.0);
                });
            });


            Messages.DpQueryConnect dpqc = client.dpQueryConnect("SELECT '_online.._value' FROM 'pump_000*.value.speed'", true, (message)->{
                //System.out.println("dpQueryConnect: "+message.DpQueryConnectResult.values.toString());
                message.dpQueryConnectResult.values.getAsJsonArray().forEach((row)->{
                    JsonArray arr = row.getAsJsonArray();
                    String dpname = arr.get(0).getAsString().split(":")[1];
                    Double value = arr.get(1).getAsDouble();
                    //client.dpSet(dpname, value+1.0);
                    //System.out.println("dpQueryConnect: "+dpname);
                });
            });


            System.out.println("dpGet...");
            client.dpGet(Arrays.asList("System1:pump_00001.value.speed"), (message)->{
                if (message.dpGetResult.values!=null)
                    System.out.println("dpGet: "+message.dpGetResult.values.toString());
                else
                    System.out.println("dpGet: "+message.dpGetResult.error);
            });

            /*
            new Thread(()->{
                int i=0;
                while (true) {
                    i++;
                    client.dpSet("System1:ExampleDP_Trend1.", i);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            for (int i=1;i<=10000;i++) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:SS");
                client.dpGetPeriod(Arrays.asList("System1:ExampleDP_Trend1.:_offline.._value"),
                        sdf.parse("2018.02.04 00:00:00"),
                        sdf.parse("2018.02.05 00:00:00"),
                        0, (message) -> {
                            //System.out.println("dpGetPeriod: "+message.dpGetPeriodResult.values.toString());
                            JsonArray arr = message.dpGetPeriodResult.values.get("System1:ExampleDP_Trend1.:_offline.._value").getAsJsonArray();
                            System.out.println("dpGetPeriod: " + arr.size());
                        });
                Thread.sleep(500);
            }
            */

            //client.dpQueryDisconnect(dpqc);

            // wait for closed socket connection.
            client.awaitClose();

            //Future<Void> f = session.getRemote().sendStringByFuture(gson.toJson(msg1));
            //f.get(2,TimeUnit.SECONDS); // wait for send to complete.
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