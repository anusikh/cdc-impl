package com.anusikh.cdcink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class DebeziumEnvelope {
    private Payload payload;
    private Schema schema;

    @Data
    public static class Payload {
        private String op;
        private Long tsMs;
        private Map<String, Object> before;
        private Map<String, Object> after;
        private Source source;

        @JsonProperty("ts_us")
        private Long tsUs;
    }

    @Data
    public static class Source {
        private String version;
        private String connector;
        private String name;
        private String db;
        private String table;
        private String snapshot;
        private Long tsMs;
        private String schema;
        private String topic;
        private Integer lsn;
        private Integer txId;
    }

    @Data
    public static class Schema {
        private String type;
        private String name;
        private String version;
    }
}
