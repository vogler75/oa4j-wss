package wss;

import com.google.gson.*;

import java.util.Date;
import java.util.List;

public class Messages {
    public static String DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.S";

    private static Long serialnr = 0L;
    public static Long nextId() {
        synchronized (serialnr) {
            return ++serialnr;
        }
    }

    public static Gson Gson() {
        return new GsonBuilder()
                .setDateFormat(Messages.DATEFORMAT)
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .create();
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class Message {
        DpSet dpSet;

        DpGet dpGet;
        DpGetResult dpGetResult;

        DpConnect dpConnect;
        DpConnectResult dpConnectResult;

        DpQueryConnect dpQueryConnect;
        DpQueryConnectResult dpQueryConnectResult;

        Response response;

        public Message DpSet(List<DpValue> Values, Date Timestamp, Boolean Wait) {
            this.dpSet =new DpSet(Values, Timestamp, Wait);
            return this;
        }

        public Message DpGet(List<String> Dps) {
            this.dpGet =new DpGet(Dps);
            return this;
        }

        public Message DpGetResult(Long Id, JsonObject Values) {
            this.dpGetResult =new DpGetResult(Id, Values);
            return this;
        }

        public Message DpConnect(List<String> Dps, Boolean Answer) {
            this.dpConnect =new DpConnect(Dps, Answer);
            return this;
        }

        public Message DpConnectResult(Long Id, JsonObject Values) {
            this.dpConnectResult=new DpConnectResult(Id, Values);
            return this;
        }

        public Message DpQueryConnect(String Query, Boolean Answer) {
            this.dpQueryConnect =new DpQueryConnect(Query, Answer);
            return this;
        }

        public Message DpQueryConnectResult(Long Id, JsonArray Header, JsonArray Values) {
            this.dpQueryConnectResult=new DpQueryConnectResult(Id, Header, Values);
            return this;
        }

        public Message Response(Long Id, Integer Code, String Message) {
            this.response =new Response(Id, Code, Message);
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

    //------------------------------------------------------------------------------------------------------------------
    public static class DpGet extends SerialId {
        List<String> dps;
        public DpGet(List<String> dps) {
            super();
            this.dps =dps;
        }
    }

    public static class DpGetResult extends SerialId {
        JsonObject values;
        public DpGetResult(Long id, JsonObject values) {
            super(id);
            this.values =values;
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

    public static class DpQueryConnectResult extends SerialId {
        JsonArray header;
        JsonArray values;
        public DpQueryConnectResult(Long id, JsonArray header, JsonArray values) {
            super(id);
            this.header =header;
            this.values =values;
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

    public static class DpConnectResult extends SerialId {
        JsonObject values;
        public DpConnectResult(Long id, JsonObject values) {
            super(id);
            this.values =values;
        }
    }
}
