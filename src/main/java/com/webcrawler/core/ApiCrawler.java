package com.webcrawler.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.model.CrawlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main API crawler class that handles HTTP requests to APIs
 */
public class ApiCrawler {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiCrawler.class);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Map<String, String> defaultHeaders;
    private final AtomicLong requestCount;
    private final long rateLimitDelayMs;
    
    // Configuration
    private String userAgent = "ApiWebCrawler/1.0";
    
    public ApiCrawler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(10);
        this.defaultHeaders = new HashMap<>();
        this.requestCount = new AtomicLong(0);
        this.rateLimitDelayMs = 1000; // 1 second between requests
        initializeDefaultHeaders();
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.defaultHeaders = new HashMap<>();
        this.requestCount = new AtomicLong(0);
        this.rateLimitDelayMs = rateLimitDelayMs;
        initializeDefaultHeaders();
    }
    
    private void initializeDefaultHeaders() {
        defaultHeaders.put("User-Agent", userAgent);
        defaultHeaders.put("Accept", "application/json, text/plain, */*");
        defaultHeaders.put("Accept-Language", "en-US,en;q=0.9");
        defaultHeaders.put("Cache-Control", "no-cache");
    }
    
    /**
     * Simple rate limiting - wait between requests
     */
    private void enforceRateLimit() {
        try {
            Thread.sleep(rateLimitDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Rate limiting interrupted");
        }
    }
    
    /**
     * Crawl a single API endpoint
     */
    public CrawlResult crawl(String url) {
        return crawl(url, null);
    }
    
    /**
     * Crawl a single API endpoint with custom headers
     */
    public CrawlResult crawl(String url, Map<String, String> customHeaders) {
        CrawlResult result = new CrawlResult(url);
        long startTime = System.currentTimeMillis();
        
        try {
            // Simple rate limiting
            if (requestCount.get() > 0) {
                enforceRateLimit();
            }
            requestCount.incrementAndGet();
            
            logger.info("Crawling URL: {}", url);
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            
            // Add default headers
            for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            
            // Add custom headers
            if (customHeaders != null) {
                for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            result.setStatusCode(response.statusCode());
            
            // Extract response headers
            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((key, values) -> {
                responseHeaders.put(key, String.join(", ", values));
            });
            result.setHeaders(responseHeaders);
            
            String responseBody = response.body();
            
            // Parse JSON response
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    Map<String, Object> data = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
                    result.setData(data);
                } catch (Exception e) {
                    logger.warn("Failed to parse JSON response for URL: {}, treating as plain text", url);
                    Map<String, Object> data = new HashMap<>();
                    data.put("raw_response", responseBody);
                    result.setData(data);
                }
            }
            
            logger.info("Successfully crawled URL: {} with status code: {}", url, result.getStatusCode());
            
        } catch (IOException | InterruptedException e) {
            logger.error("Error crawling URL: {}", url, e);
            result.setErrorMessage(e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            result.setCrawlDurationMs(System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    /**
     * Crawl multiple URLs asynchronously
     */
    public CompletableFuture<Map<String, CrawlResult>> crawlAsync(java.util.List<String> urls) {
        return crawlAsync(urls, null);
    }
    
    /**
     * Crawl multiple URLs asynchronously with custom headers
     */
    public CompletableFuture<Map<String, CrawlResult>> crawlAsync(java.util.List<String> urls, Map<String, String> customHeaders) {
        Map<String, CompletableFuture<CrawlResult>> futures = new HashMap<>();
        
        for (String url : urls) {
            CompletableFuture<CrawlResult> future = CompletableFuture.supplyAsync(() -> crawl(url, customHeaders), executorService);
            futures.put(url, future);
        }
        
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, CrawlResult> results = new HashMap<>();
                    futures.forEach((url, future) -> {
                        try {
                            results.put(url, future.get());
                        } catch (Exception e) {
                            logger.error("Error getting result for URL: {}", url, e);
                            CrawlResult errorResult = new CrawlResult(url);
                            errorResult.setErrorMessage("Async execution error: " + e.getMessage());
                            results.put(url, errorResult);
                        }
                    });
                    return results;
                });
    }
    
    /**
     * Make a POST request to an API
     */
    public CrawlResult postData(String url, String jsonBody) {
        return postData(url, jsonBody, null);
    }
    
    /**
     * Make a POST request to an API with custom headers
     */
    public CrawlResult postData(String url, String jsonBody, Map<String, String> customHeaders) {
        CrawlResult result = new CrawlResult(url);
        long startTime = System.currentTimeMillis();
        
        try {
            // Simple rate limiting
            if (requestCount.get() > 0) {
                enforceRateLimit();
            }
            requestCount.incrementAndGet();
            
            logger.info("Posting to URL: {}", url);
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            
            // Add default headers
            for (Map.Entry<String, String> header : defaultHeaders.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }
            
            // Add custom headers
            if (customHeaders != null) {
                for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            result.setStatusCode(response.statusCode());
            
            // Extract response headers
            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((key, values) -> {
                responseHeaders.put(key, String.join(", ", values));
            });
            result.setHeaders(responseHeaders);
            
            String responseBody = response.body();
            
            // Parse JSON response
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    Map<String, Object> data = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
                    result.setData(data);
                } catch (Exception e) {
                    logger.warn("Failed to parse JSON response for URL: {}, treating as plain text", url);
                    Map<String, Object> data = new HashMap<>();
                    data.put("raw_response", responseBody);
                    result.setData(data);
                }
            }
            
            logger.info("Successfully posted to URL: {} with status code: {}", url, result.getStatusCode());
            
        } catch (IOException | InterruptedException e) {
            logger.error("Error posting to URL: {}", url, e);
            result.setErrorMessage(e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            result.setCrawlDurationMs(System.currentTimeMillis() - startTime);
        }
        
        return result;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        this.defaultHeaders.put("User-Agent", userAgent);
    }
    
    public void addDefaultHeader(String name, String value) {
        this.defaultHeaders.put(name, value);
    }
    
    public long getRequestCount() {
        return requestCount.get();
    }
    
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 