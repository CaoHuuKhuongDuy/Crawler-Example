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
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main API crawler class that handles HTTP requests to APIs with fault tolerance
 */
public class ApiCrawler {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiCrawler.class);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Map<String, String> defaultHeaders;
    private final AtomicLong requestCount;
    private final long rateLimitDelayMs;
    
    // Retry configuration
    private final int maxRetries;
    private final long baseRetryDelayMs;
    private final double backoffMultiplier;
    private final Set<Integer> retryableStatusCodes;
    
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
        
        // Default retry configuration
        this.maxRetries = 3;
        this.baseRetryDelayMs = 1000; // Start with 1 second
        this.backoffMultiplier = 2.0; // Double the delay each retry
        this.retryableStatusCodes = initializeRetryableStatusCodes();
        
        initializeDefaultHeaders();
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs) {
        this(threadPoolSize, rateLimitDelayMs, 3, 1000, 2.0);
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs, int maxRetries, long baseRetryDelayMs, double backoffMultiplier) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.defaultHeaders = new HashMap<>();
        this.requestCount = new AtomicLong(0);
        this.rateLimitDelayMs = rateLimitDelayMs;
        
        // Retry configuration
        this.maxRetries = maxRetries;
        this.baseRetryDelayMs = baseRetryDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.retryableStatusCodes = initializeRetryableStatusCodes();
        
        initializeDefaultHeaders();
    }
    
    private Set<Integer> initializeRetryableStatusCodes() {
        Set<Integer> codes = new HashSet<>();
        // Server errors (5xx)
        codes.add(500); // Internal Server Error
        codes.add(502); // Bad Gateway
        codes.add(503); // Service Unavailable
        codes.add(504); // Gateway Timeout
        codes.add(507); // Insufficient Storage
        codes.add(508); // Loop Detected
        codes.add(510); // Not Extended
        codes.add(511); // Network Authentication Required
        
        // Rate limiting
        codes.add(429); // Too Many Requests
        
        // Request timeout
        codes.add(408); // Request Timeout
        
        return codes;
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
     * Wait with exponential backoff for retries
     */
    private void waitForRetry(int attemptNumber) {
        long delay = (long) (baseRetryDelayMs * Math.pow(backoffMultiplier, attemptNumber));
        logger.info("Waiting {}ms before retry attempt #{}", delay, attemptNumber + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry wait interrupted");
        }
    }
    
    /**
     * Determine if a status code should trigger a retry
     */
    private boolean shouldRetry(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }
    
    /**
     * Determine if an exception should trigger a retry
     */
    private boolean shouldRetryException(Exception e) {
        if (e instanceof IOException) {
            String message = e.getMessage().toLowerCase();
            // Network-related errors that might be temporary
            return message.contains("timeout") ||
                   message.contains("connection reset") ||
                   message.contains("connection refused") ||
                   message.contains("no route to host") ||
                   message.contains("host unreachable") ||
                   message.contains("network unreachable") ||
                   message.contains("connection timed out");
        }
        return false;
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
        return crawlWithRetry(url, customHeaders);
    }
    
    /**
     * Crawl with automatic retry and exponential backoff
     */
    private CrawlResult crawlWithRetry(String url, Map<String, String> customHeaders) {
        CrawlResult lastResult = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            lastResult = attemptCrawl(url, customHeaders, attempt);
            
            // If successful, return immediately
            if (lastResult.isSuccessful()) {
                if (attempt > 0) {
                    logger.info("✅ Successfully crawled URL: {} after {} retries", url, attempt);
                }
                return lastResult;
            }
            
            // If this was the last attempt, don't retry
            if (attempt == maxRetries) {
                logger.error("❌ Failed to crawl URL: {} after {} attempts. Final error: {}", 
                           url, maxRetries + 1, lastResult.getErrorMessage());
                break;
            }
            
            // Determine if we should retry
            boolean shouldRetryThis = false;
            
            // Retry on specific HTTP status codes
            if (lastResult.getStatusCode() > 0 && shouldRetry(lastResult.getStatusCode())) {
                shouldRetryThis = true;
                logger.warn("⚠️ Retryable HTTP status {} for URL: {} (attempt {}/{})", 
                          lastResult.getStatusCode(), url, attempt + 1, maxRetries + 1);
            }
            // Retry on network exceptions
            else if (lastResult.getErrorMessage() != null && shouldRetryException(new IOException(lastResult.getErrorMessage()))) {
                shouldRetryThis = true;
                logger.warn("⚠️ Retryable network error for URL: {} - {} (attempt {}/{})", 
                          url, lastResult.getErrorMessage(), attempt + 1, maxRetries + 1);
            }
            
            if (!shouldRetryThis) {
                logger.warn("❌ Non-retryable error for URL: {} - Status: {}, Error: {}", 
                          url, lastResult.getStatusCode(), lastResult.getErrorMessage());
                break;
            }
            
            // Wait before retry (with exponential backoff)
            waitForRetry(attempt);
        }
        
        return lastResult;
    }
    
    /**
     * Single crawl attempt without retry logic
     */
    private CrawlResult attemptCrawl(String url, Map<String, String> customHeaders, int attemptNumber) {
        CrawlResult result = new CrawlResult(url);
        long startTime = System.currentTimeMillis();
        
        try {
            // Simple rate limiting (only on first attempt to avoid double delay)
            if (attemptNumber == 0 && requestCount.get() > 0) {
                enforceRateLimit();
            }
            
            if (attemptNumber == 0) {
                requestCount.incrementAndGet();
            }
            
            if (attemptNumber == 0) {
                logger.info("🔍 Crawling URL: {}", url);
            } else {
                logger.info("🔁 Retry attempt #{} for URL: {}", attemptNumber + 1, url);
            }
            
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
            
            // Check if status code indicates success
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("✅ Successfully crawled URL: {} with status code: {}", url, result.getStatusCode());
            } else {
                logger.warn("⚠️ Non-success status code {} for URL: {}", response.statusCode(), url);
            }
            
        } catch (IOException | InterruptedException e) {
            logger.warn("🔧 Network error crawling URL: {} - {}", url, e.getMessage());
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
        return postDataWithRetry(url, jsonBody, customHeaders);
    }
    
    /**
     * POST with automatic retry and exponential backoff
     */
    private CrawlResult postDataWithRetry(String url, String jsonBody, Map<String, String> customHeaders) {
        CrawlResult lastResult = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            lastResult = attemptPost(url, jsonBody, customHeaders, attempt);
            
            // If successful, return immediately
            if (lastResult.isSuccessful()) {
                if (attempt > 0) {
                    logger.info("✅ Successfully posted to URL: {} after {} retries", url, attempt);
                }
                return lastResult;
            }
            
            // If this was the last attempt, don't retry
            if (attempt == maxRetries) {
                logger.error("❌ Failed to post to URL: {} after {} attempts. Final error: {}", 
                           url, maxRetries + 1, lastResult.getErrorMessage());
                break;
            }
            
            // Determine if we should retry
            boolean shouldRetryThis = false;
            
            // Retry on specific HTTP status codes
            if (lastResult.getStatusCode() > 0 && shouldRetry(lastResult.getStatusCode())) {
                shouldRetryThis = true;
                logger.warn("⚠️ Retryable HTTP status {} for POST to URL: {} (attempt {}/{})", 
                          lastResult.getStatusCode(), url, attempt + 1, maxRetries + 1);
            }
            // Retry on network exceptions
            else if (lastResult.getErrorMessage() != null && shouldRetryException(new IOException(lastResult.getErrorMessage()))) {
                shouldRetryThis = true;
                logger.warn("⚠️ Retryable network error for POST to URL: {} - {} (attempt {}/{})", 
                          url, lastResult.getErrorMessage(), attempt + 1, maxRetries + 1);
            }
            
            if (!shouldRetryThis) {
                logger.warn("❌ Non-retryable error for POST to URL: {} - Status: {}, Error: {}", 
                          url, lastResult.getStatusCode(), lastResult.getErrorMessage());
                break;
            }
            
            // Wait before retry (with exponential backoff)
            waitForRetry(attempt);
        }
        
        return lastResult;
    }
    
    /**
     * Single POST attempt without retry logic
     */
    private CrawlResult attemptPost(String url, String jsonBody, Map<String, String> customHeaders, int attemptNumber) {
        CrawlResult result = new CrawlResult(url);
        long startTime = System.currentTimeMillis();

        try {
            // Simple rate limiting (only on first attempt to avoid double delay)
            if (attemptNumber == 0 && requestCount.get() > 0) {
                enforceRateLimit();
            }
            
            if (attemptNumber == 0) {
                requestCount.incrementAndGet();
            }

            if (attemptNumber == 0) {
                logger.info("📤 Posting to URL: {}", url);
            } else {
                logger.info("🔁 POST retry attempt #{} for URL: {}", attemptNumber + 1, url);
            }

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

            // Check if status code indicates success
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("✅ Successfully posted to URL: {} with status code: {}", url, result.getStatusCode());
            } else {
                logger.warn("⚠️ Non-success status code {} for POST to URL: {}", response.statusCode(), url);
            }

        } catch (IOException | InterruptedException e) {
            logger.warn("🔧 Network error posting to URL: {} - {}", url, e.getMessage());
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
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public long getBaseRetryDelayMs() {
        return baseRetryDelayMs;
    }
    
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
    
    public Set<Integer> getRetryableStatusCodes() {
        return new HashSet<>(retryableStatusCodes);
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