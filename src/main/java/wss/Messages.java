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

import com.google.gson.*;

import java.util.Date;
import java.util.List;

public class Messages {
    public static String DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.S";

    public static Gson Gson() {
        return new GsonBuilder()
                .setDateFormat(Messages.DATEFORMAT)
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
    }

    private static Long serialNr = 0L;
    public static Long nextId() {
        synchronized (serialNr) {
            return ++serialNr;
        }
    }

    public static class Tuple<X, Y> {
        public final X _1;
        public final Y _2;
        public Tuple(X _1, Y _2) {
            this._1 = _1;
            this._2 = _2;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class Message {
        Connection connection;

        DpSet dpSet;
        DpSetResult dpSetResult;

        DpGet dpGet;
        DpGetResult dpGetResult;

        DpConnect dpConnect;
        DpConnectResult dpConnectResult;
        DpDisconnect dpDisconnect;

        DpQueryConnect dpQueryConnect;
        DpQueryConnectResult dpQueryConnectResult;
        DpQueryDisconnect dpQueryDisconnect;

        DpGetPeriod dpGetPeriod;
        DpGetPeriodResult dpGetPeriodResult;

        public Message Connection(Long id, Integer code, String message) {
            this.connection =new Connection(id, code, message);
            return this;
        }

        public Message DpSet(List<DpValue> values, Date timestamp, Boolean wait) {
            this.dpSet =new DpSet(values, timestamp, wait);
            return this;
        }

        public Message DpSetResult(Long id, Integer error) {
            this.dpSetResult =new DpSetResult(id, error);
            return this;
        }

        public Message DpGet(List<String> dps) {
            this.dpGet =new DpGet(dps);
            return this;
        }

        public Message DpGetResult(Long id, JsonObject values) {
            this.dpGetResult =new DpGetResult(id, values);
            return this;
        }

        public Message DpGetResult(Long id, Integer error) {
            this.dpGetResult =new DpGetResult(id, error);
            return this;
        }

        public Message DpConnect(List<String> dps, Boolean answer) {
            this.dpConnect =new DpConnect(dps, answer);
            return this;
        }

        public Message DpConnectResult(Long id, JsonObject values) {
            this.dpConnectResult=new DpConnectResult(id, values);
            return this;
        }

        public Message DpConnectResult(Long id, Integer error) {
            this.dpConnectResult=new DpConnectResult(id, error);
            return this;
        }

        public Message DpDisconnect(Long id) {
            this.dpDisconnect=new DpDisconnect(id);
            return this;
        }

        public Message DpQueryConnect(String query, Boolean answer) {
            this.dpQueryConnect =new DpQueryConnect(query, answer);
            return this;
        }

        public Message DpQueryConnectResult(Long id, JsonArray header, JsonArray values) {
            this.dpQueryConnectResult=new DpQueryConnectResult(id, header, values);
            return this;
        }

        public Message DpQueryConnectResult(Long id, Integer error) {
            this.dpQueryConnectResult=new DpQueryConnectResult(id, error);
            return this;
        }

        public Message DpQueryDisconnect(Long id) {
            this.dpQueryDisconnect=new DpQueryDisconnect(id);
            return this;
        }

        public Message DpGetPeriod(List<String>dps, Date t1, Date t2, Integer count, Integer ts) {
            this.dpGetPeriod =new DpGetPeriod(dps, t1, t2, count, ts);
            return this;
        }

        public Message DpGetPeriodResult(Long id, JsonObject values) {
            this.dpGetPeriodResult = new DpGetPeriodResult(id, values);
            return this;
        }

        public Message DpGetPeriodResult(Long id, Integer error) {
            this.dpGetPeriodResult = new DpGetPeriodResult(id, error);
            return this;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class SerialId {
        Long id;
        public SerialId() {
            id =nextId();
        }
        public SerialId(Long Id) {
            this.id =Id;
        }
    }

    public static class Result extends SerialId {
        public Integer error;
        public Result(Long id, Integer error) {
            super(id);
            this.error=error;
        }
        public Result(Long id) {
            super(id);
            this.error=0;
        }
    }

    public static class DpValue {
        String dp;
        Object value;
        public DpValue(String Dp, Object Value) {
            this.dp =Dp;
            this.value =Value;
        }
    }

    public static class Response extends SerialId {
        Integer code;
        String message;
        public Response(Long id, Integer code, String message) {
            super(id);
            this.code=code;
            this.message=message;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class Connection extends Response {
        public Connection(Long id, Integer code, String message) {
            super(id, code, message);
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpSet extends SerialId {
        List<DpValue> values;
        Date timestamp; // Optional for dpSetTimed
        Boolean wait; // Optional for dpSetWait
        public DpSet(List<DpValue> values, Date timestamp, Boolean wait) {
            super();
            this.values=values;
            this.timestamp=timestamp;
            this.wait=wait;
        }
    }

    public static class DpSetResult extends Result {
        public DpSetResult(Long id, Integer error) {
            super(id, error);
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpConnect extends SerialId {
        List<String> dps;
        Boolean answer; // Optional: get initial values
        public DpConnect(List<String> dps, Boolean answer) {
            super();
            this.dps =dps;
            this.answer =answer;
        }
    }

    public static class DpConnectResult extends Result {
        JsonObject values;
        public DpConnectResult(Long id, JsonObject values) {
            super(id);
            this.values =values;
        }
        public DpConnectResult(Long id, Integer error) {
            super(id, error);
        }
    }

    public static class DpDisconnect extends SerialId {
        public DpDisconnect(Long id) {
            this.id=id;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpQueryConnect extends SerialId {
        String query;
        Boolean answer; // Optional: get initial values
        public DpQueryConnect(String query, Boolean answer) {
            super();
            this.query =query;
            this.answer =answer;
        }
    }

    public static class DpQueryConnectResult extends Result {
        JsonArray header;
        JsonArray values;
        public DpQueryConnectResult(Long id, JsonArray header, JsonArray values) {
            super(id);
            this.header =header;
            this.values =values;
        }
        public DpQueryConnectResult(Long id, Integer error) {
            super(id, error);
        }
    }

    public static class DpQueryDisconnect extends SerialId {
        public DpQueryDisconnect(Long id) {
            this.id=id;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpGet extends SerialId {
        List<String> dps;
        public DpGet(List<String> dps) {
            super();
            this.dps =dps;
        }
    }

    public static class DpGetResult extends Result {
        JsonObject values;
        public DpGetResult(Long id, JsonObject values) {
            super(id);
            this.values =values;
        }
        public DpGetResult(Long id, Integer error) {
            super(id, error);
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpGetPeriod extends SerialId {
        List<String> dps;
        Date t1;
        Date t2;
        Integer count;
        Integer ts; // 0... no ts in result, 1... ts as ms since epoch, 2...ts as ISO8601

        public DpGetPeriod(List<String> dps, Date t1, Date t2, Integer count, Integer ts) {
            super();
            this.dps =dps;
            this.t1 =t1;
            this.t2 =t2;
            this.count =count;
            this.ts = ts;
        }
    }

    public static class DpGetPeriodResult extends Result {
        JsonObject values;
        public DpGetPeriodResult(Long id, JsonObject values) {
            super(id);
            this.values =values;
        }
        public DpGetPeriodResult(Long id, Integer error) {
            super(id, error);
        }
    }
}
