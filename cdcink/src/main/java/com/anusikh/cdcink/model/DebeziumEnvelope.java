package com.anusikh.cdcink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumEnvelope {
    private Payload payload;
    private Schema schema;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String op;
        
        @JsonProperty("ts_ms")
        private Long tsMs;
        
        private Map<String, Object> before;
        private Map<String, Object> after;
        private Source source;

        @JsonProperty("ts_us")
        private Long tsUs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private String version;
        private String connector;
        private String name;
        
        @JsonProperty("ts_ms")
        private Long tsMs;
        
        private String db;
        private String table;
        private String snapshot;
        private String schema;
        private String topic;
        private Long lsn;
        private Long txId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Schema {
        private String type;
        private String name;
        private String version;
    }
}
