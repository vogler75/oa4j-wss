package wss;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class Messages {
    public static String DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ss.S";

    private static Long serialnr = 0L;

    public static Long nextId() {
        synchronized (serialnr) {
            return ++serialnr;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class Message {
        DpSet DpSet;

        DpConnect DpConnect;
        DpConnectResult DpConnectResult;

        DpQueryConnect DpQueryConnect;
        DpQueryConnectResult DpQueryConnectResult;

        Response Response;

        public Message DpSet(List<DpVCItem> Values, Date Timestamp, Boolean Wait) {
            this.DpSet=new DpSet(Values, Timestamp, Wait);
            return this;
        }

        public Message DpConnect(List<String> Dps, Boolean Answer) {
            this.DpConnect=new DpConnect(Dps, Answer);
            return this;
        }

        public Message DpConnectResult(Long Id, JsonObject Values) {
            this.DpConnectResult=new DpConnectResult(Id, Values);
            return this;
        }

        public Message DpQueryConnect(String Query, Boolean Answer) {
            this.DpQueryConnect=new DpQueryConnect(Query, Answer);
            return this;
        }

        public Message DpQueryConnectResult(Long Id, JsonArray Header, JsonArray Values) {
            this.DpQueryConnectResult=new DpQueryConnectResult(Id, Header, Values);
            return this;
        }

        public Message Response(Long Id, Integer Code, String Message) {
            this.Response=new Response(Id, Code, Message);
            return this;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class SerialId {
        Long Id;
        public SerialId() {
            Id=nextId();
        }
        public SerialId(Long Id) {
            this.Id=Id;
        }
    }

    public static class DpVCItem {
        String Dp;
        Object Value;
        public DpVCItem(String Dp, Object Value) {
            this.Dp=Dp;
            this.Value=Value;
        }
    }

    public static class Response extends SerialId {
        Integer Code;
        String Message;
        public Response(Long Id, Integer Code, String Message) {
            super(Id);
            this.Code=Code;
            this.Message=Message;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpSet extends SerialId {
        List<DpVCItem> Values;
        Date Timestamp; // Optional for dpSetTimed
        Boolean Wait; // Optional for dpSetWait
        public DpSet(List<DpVCItem> Values, Date Timestamp, Boolean Wait) {
            super();
            this.Values=Values;
            this.Timestamp=Timestamp;
            this.Wait=Wait;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpQueryConnect extends SerialId {
        String Query;
        Boolean Answer; // Optional: get initial values
        public DpQueryConnect(String Query, Boolean Answer) {
            super();
            this.Query=Query;
            this.Answer=Answer;
        }
    }

    public static class DpQueryConnectResult extends SerialId {
        JsonArray Header;
        JsonArray Values;
        public DpQueryConnectResult(Long Id, JsonArray Header, JsonArray Values) {
            super(Id);
            this.Header=Header;
            this.Values=Values;
        }
    }

    //------------------------------------------------------------------------------------------------------------------
    public static class DpConnect extends SerialId {
        List<String> Dps;
        Boolean Answer; // Optional: get initial values
        public DpConnect(List<String> Dps, Boolean Answer) {
            super();
            this.Dps=Dps;
            this.Answer=Answer;
        }
    }

    public static class DpConnectResult extends SerialId {
        JsonObject Values;
        public DpConnectResult(Long Id, JsonObject Values) {
            super(Id);
            this.Values=Values;
        }
    }
}
