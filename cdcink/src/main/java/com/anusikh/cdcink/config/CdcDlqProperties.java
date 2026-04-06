package com.anusikh.cdcink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdc.dlq")
public class CdcDlqProperties {

    private String appTopic = "cdc.dlq.app";
    private int maxAttempts = 3;
    private long initialIntervalMs = 1000L;
    private double multiplier = 2.0d;
    private long maxIntervalMs = 30000L;

    public String getAppTopic() {
        return appTopic;
    }

    public void setAppTopic(String appTopic) {
        this.appTopic = appTopic;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialIntervalMs() {
        return initialIntervalMs;
    }

    public void setInitialIntervalMs(long initialIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }
}
