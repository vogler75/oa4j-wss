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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;

public class Client {

    public static void main(String[] args)
    {
        final String url = (args.length > 0) ? args[0] : "ws://localhost:8080/winccoa?username=demo&password=demo";
        final Double val = (args.length > 1) ? Double.valueOf(args[1]) : null;

        ClientSocket client = new ClientSocket() {
            @Override
            public void onWebSocketClose(int statusCode, String reason) {
                super.onWebSocketClose(statusCode, reason);
                if (statusCode!=4000 /* wrong username/password */) {
                }
            }
        };

        try
        {
            System.out.printf("Connecting to : %s%n",url);
            client.open(url);
            client.awaitConnected();

            Messages.DpConnect dpc = client.dpConnect(Arrays.asList("System1:pump_00001.value.speed", "System1:pump_00002.value.speed"), true, (message)->{
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

            new Thread(()->{
                int i=0;
                while (client.isOpen()) {
                    i++;
                    client.dpSet("System1:ExampleDP_Trend1.", val!=null ? val : i);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            /*
            for (int i=1;i<=1000;i++) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:SS");
                client.dpGetPeriod(Arrays.asList("System1:ExampleDP_Trend1.:_offline.._value"),
                        new Date(new Date().getTime()-1000*60*60), //sdf.parse("2018.02.04 00:00:00"),
                        new Date(new Date().getTime()), //sdf.parse("2018.02.05 00:00:00"),
                        0, (message) -> {
                            //System.out.println("dpGetPeriod: "+message.dpGetPeriodResult.values.toString());
                            JsonElement obj = message.dpGetPeriodResult.values.get("System1:ExampleDP_Trend1.:_offline.._value");
                            if (obj!=null) {
                                System.out.println("dpGetPeriod: " + obj.getAsJsonArray().size());
                            }
                        });
                Thread.sleep(1000);
            }
            */

            client.awaitClose();

            client.dpQueryDisconnect(dpqc);
            client.dpDisconnect(dpc);

            System.out.println("end.");
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