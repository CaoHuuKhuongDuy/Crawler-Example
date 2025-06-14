package com.webcrawler.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents the result of a crawling operation
 */
public class CrawlResult {
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("data")
    private Map<String, Object> data;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    @JsonProperty("status_code")
    private int statusCode;
    
    @JsonProperty("headers")
    private Map<String, String> headers;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    @JsonProperty("crawl_duration_ms")
    private long crawlDurationMs;
    
    public CrawlResult() {
        this.timestamp = LocalDateTime.now();
    }
    
    public CrawlResult(String url) {
        this();
        this.url = url;
    }
    
    // Getters and Setters
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public long getCrawlDurationMs() {
        return crawlDurationMs;
    }
    
    public void setCrawlDurationMs(long crawlDurationMs) {
        this.crawlDurationMs = crawlDurationMs;
    }
    
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300 && errorMessage == null;
    }
    
    @Override
    public String toString() {
        return String.format("CrawlResult{url='%s', statusCode=%d, timestamp=%s, successful=%s}", 
                           url, statusCode, timestamp, isSuccessful());
    }
} 