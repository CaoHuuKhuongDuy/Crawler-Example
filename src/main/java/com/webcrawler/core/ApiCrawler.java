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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

/**
 * Main API crawler class with advanced scalability and concurrency features
 */
public class ApiCrawler {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiCrawler.class);
    
    private final HttpClient httpClient;
    private final HttpClient http2Client; // Dedicated HTTP/2 client
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor executorService;
    private final ForkJoinPool processingPool; // For parallel response processing
    private final ScheduledExecutorService monitoringService;
    private final Map<String, String> defaultHeaders;
    private final AtomicLong requestCount;
    private final long rateLimitDelayMs;
    
    // Advanced scalability settings
    private final int maxConnectionsPerHost;
    private final boolean enableHttp2;
    private final boolean enableConcurrentProcessing;
    private final int processingParallelism;
    
    // Thread monitoring
    private final AtomicInteger threadsCreated;
    private final AtomicInteger threadsReplaced;
    private final AtomicLong lastHealthCheck;
    private final int originalThreadPoolSize;
    
    // Retry configuration
    private final int maxRetries;
    private final long baseRetryDelayMs;
    private final double backoffMultiplier;
    private final Set<Integer> retryableStatusCodes;
    
    // Configuration
    private String userAgent = "ApiWebCrawler/1.0";
    
    public ApiCrawler() {
        this(10, 1000, 3, 1000, 2.0, true, true, 4);
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs) {
        this(threadPoolSize, rateLimitDelayMs, 3, 1000, 2.0, true, true, 4);
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs, int maxRetries, long baseRetryDelayMs, double backoffMultiplier) {
        this(threadPoolSize, rateLimitDelayMs, maxRetries, baseRetryDelayMs, backoffMultiplier, true, true, 4);
    }
    
    public ApiCrawler(int threadPoolSize, long rateLimitDelayMs, int maxRetries, long baseRetryDelayMs, 
                     double backoffMultiplier, boolean enableHttp2, boolean enableConcurrentProcessing, 
                     int maxConnectionsPerHost) {
        
        this.originalThreadPoolSize = threadPoolSize;
        this.rateLimitDelayMs = rateLimitDelayMs;
        this.maxRetries = maxRetries;
        this.baseRetryDelayMs = baseRetryDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.enableHttp2 = enableHttp2;
        this.enableConcurrentProcessing = enableConcurrentProcessing;
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        this.processingParallelism = Math.max(2, Runtime.getRuntime().availableProcessors());
        
        // Initialize HTTP clients with advanced features
        this.httpClient = createAdvancedHttpClient(false);
        this.http2Client = enableHttp2 ? createAdvancedHttpClient(true) : this.httpClient;
        
        this.objectMapper = new ObjectMapper();
        this.executorService = createRobustThreadPool(threadPoolSize);
        this.processingPool = new ForkJoinPool(processingParallelism);
        this.monitoringService = Executors.newScheduledThreadPool(1, new MonitoringThreadFactory());
        this.defaultHeaders = new HashMap<>();
        this.requestCount = new AtomicLong(0);
        
        // Thread monitoring
        this.threadsCreated = new AtomicInteger(threadPoolSize);
        this.threadsReplaced = new AtomicInteger(0);
        this.lastHealthCheck = new AtomicLong(System.currentTimeMillis());
        
        this.retryableStatusCodes = initializeRetryableStatusCodes();
        
        initializeDefaultHeaders();
        startThreadPoolMonitoring();
        
        logger.info("🚀 Enhanced ApiCrawler initialized:");
        logger.info("   Thread Pool Size: {}", threadPoolSize);
        logger.info("   HTTP/2 Enabled: {}", enableHttp2);
        logger.info("   Concurrent Processing: {}", enableConcurrentProcessing);
        logger.info("   Max Connections per Host: {}", maxConnectionsPerHost);
        logger.info("   Processing Parallelism: {}", processingParallelism);
    }
    
    /**
     * Create advanced HTTP client with connection pooling and HTTP/2 support
     */
    private HttpClient createAdvancedHttpClient(boolean forceHttp2) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);
        
        if (forceHttp2) {
            builder.version(HttpClient.Version.HTTP_2);
            logger.info("🔄 HTTP/2 client created for enhanced multiplexing");
        } else {
            builder.version(HttpClient.Version.HTTP_1_1);
        }
        
        return builder.build();
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
     * Single crawl attempt with enhanced concurrency and HTTP/2 support
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
                logger.info("🔍 Crawling URL: {} (HTTP/2: {})", url, enableHttp2);
            } else {
                logger.info("🔁 Retry attempt #{} for URL: {}", attemptNumber + 1, url);
            }
            
            // Use advanced crawling with concurrent processing
            if (enableConcurrentProcessing) {
                return attemptConcurrentCrawl(url, customHeaders, result, startTime);
            } else {
                return attemptStandardCrawl(url, customHeaders, result, startTime);
            }
            
        } catch (Exception e) {
            logger.warn("🔧 Error in crawl attempt for URL: {} - {}", url, e.getMessage());
            result.setErrorMessage(e.getMessage());
            result.setCrawlDurationMs(System.currentTimeMillis() - startTime);
            return result;
        }
    }
    
    /**
     * Standard sequential crawling approach
     */
    private CrawlResult attemptStandardCrawl(String url, Map<String, String> customHeaders, CrawlResult result, long startTime) {
        try {
            HttpRequest request = buildHttpRequest(url, customHeaders);
            HttpClient clientToUse = enableHttp2 ? http2Client : httpClient;
            
            HttpResponse<String> response = clientToUse.send(request, HttpResponse.BodyHandlers.ofString());
            
            processResponse(response, result);
            
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
     * Enhanced concurrent crawling with parallel processing
     */
    private CrawlResult attemptConcurrentCrawl(String url, Map<String, String> customHeaders, CrawlResult result, long startTime) {
        try {
            HttpRequest request = buildHttpRequest(url, customHeaders);
            HttpClient clientToUse = enableHttp2 ? http2Client : httpClient;
            
            // Concurrent download and processing
            CompletableFuture<HttpResponse<String>> downloadFuture = 
                clientToUse.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            // Process response when download completes
            CompletableFuture<Void> processingFuture = downloadFuture.thenAcceptAsync(response -> {
                try {
                    processResponse(response, result);
                    logger.debug("⚡ Concurrent processing completed for URL: {}", url);
                } catch (Exception e) {
                    logger.warn("🔧 Error in concurrent processing for URL: {} - {}", url, e.getMessage());
                    result.setErrorMessage("Processing error: " + e.getMessage());
                }
            }, processingPool);
            
            // Wait for both download and processing to complete
            processingFuture.get(45, TimeUnit.SECONDS); // Slightly longer timeout for concurrent operations
            
        } catch (Exception e) {
            logger.warn("🔧 Error in concurrent crawl for URL: {} - {}", url, e.getMessage());
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
     * Build HTTP request with optimizations
     */
    private HttpRequest buildHttpRequest(String url, Map<String, String> customHeaders) {
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
        
        // Note: HttpClient automatically handles compression (gzip, deflate, br)
        // No need to explicitly set Accept-Encoding as it can cause decompression issues
        
        return requestBuilder.build();
    }
    
    /**
     * Process HTTP response with optional parallel JSON parsing
     */
    private void processResponse(HttpResponse<String> response, CrawlResult result) {
        result.setStatusCode(response.statusCode());
        
        // Extract response headers
        Map<String, String> responseHeaders = new HashMap<>();
        response.headers().map().forEach((key, values) -> {
            responseHeaders.put(key, String.join(", ", values));
        });
        result.setHeaders(responseHeaders);
        
        String responseBody = response.body();
        
        // Enhanced JSON parsing
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            if (enableConcurrentProcessing && responseBody.length() > 10000) {
                // Use parallel processing for large responses
                processLargeJsonResponse(responseBody, result);
            } else {
                // Standard processing for smaller responses
                processStandardJsonResponse(responseBody, result);
            }
        }
        
        // Log success/failure
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            logger.debug("✅ Successfully processed response for status: {}", response.statusCode());
        } else {
            logger.warn("⚠️ Non-success status code: {}", response.statusCode());
        }
    }
    
    /**
     * Standard JSON processing with compression detection
     */
    private void processStandardJsonResponse(String responseBody, CrawlResult result) {
        try {
            // Check if response appears to be compressed binary data
            if (responseBody.length() > 0 && (responseBody.charAt(0) == 0x1F || responseBody.contains("\u001F"))) {
                logger.error("❌ Response appears to be compressed binary data - decompression failed");
                Map<String, Object> data = new HashMap<>();
                data.put("parsing_error", "Response appears to be compressed (GZIP/deflate) but was not properly decompressed by HttpClient");
                data.put("raw_response", responseBody.substring(0, Math.min(200, responseBody.length())) + "...[truncated]");
                result.setData(data);
                return;
            }
            
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            Map<String, Object> data = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
            result.setData(data);
        } catch (Exception e) {
            logger.warn("Failed to parse JSON response: {}", e.getMessage());
            Map<String, Object> data = new HashMap<>();
            data.put("parsing_error", e.getMessage());
            data.put("raw_response", responseBody.substring(0, Math.min(500, responseBody.length())) + 
                                   (responseBody.length() > 500 ? "...[truncated " + (responseBody.length() - 500) + " chars]" : ""));
            result.setData(data);
        }
    }
    
    /**
     * Parallel processing for large JSON responses
     */
    private void processLargeJsonResponse(String responseBody, CrawlResult result) {
        try {
            // Submit JSON parsing to processing pool
            CompletableFuture<Map<String, Object>> parsingFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    logger.debug("JSON parsing failed in parallel processor, falling back to raw text");
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("raw_response", responseBody);
                    fallback.put("parsing_error", e.getMessage());
                    return fallback;
                }
            }, processingPool);
            
            // Get result with timeout
            Map<String, Object> data = parsingFuture.get(10, TimeUnit.SECONDS);
            result.setData(data);
            logger.debug("⚡ Large JSON response processed in parallel");
            
        } catch (Exception e) {
            logger.warn("Parallel JSON processing failed, falling back to standard processing");
            processStandardJsonResponse(responseBody, result);
        }
    }
    
    /**
     * Crawl multiple URLs asynchronously
     */
    public CompletableFuture<Map<String, CrawlResult>> crawlAsync(java.util.List<String> urls) {
        return crawlAsync(urls, null);
    }
    
    /**
     * Crawl multiple URLs asynchronously with enhanced batching and load balancing
     */
    public CompletableFuture<Map<String, CrawlResult>> crawlAsync(java.util.List<String> urls, Map<String, String> customHeaders) {
        if (urls.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        
        logger.info("🚀 Starting batch crawl of {} URLs with enhanced concurrency", urls.size());
        
        // Enhanced batch processing with load balancing
        if (enableConcurrentProcessing && urls.size() > 1) {
            return crawlBatchEnhanced(urls, customHeaders);
        } else {
            return crawlBatchStandard(urls, customHeaders);
        }
    }
    
    /**
     * Standard batch crawling
     */
    private CompletableFuture<Map<String, CrawlResult>> crawlBatchStandard(List<String> urls, Map<String, String> customHeaders) {
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
     * Enhanced batch crawling with intelligent load balancing and HTTP/2 connection reuse
     */
    private CompletableFuture<Map<String, CrawlResult>> crawlBatchEnhanced(List<String> urls, Map<String, String> customHeaders) {
        // Group URLs by host for optimal connection reuse
        Map<String, List<String>> urlsByHost = groupUrlsByHost(urls);
        Map<String, CompletableFuture<Map<String, CrawlResult>>> hostFutures = new HashMap<>();
        
        logger.info("⚡ Enhanced batch crawling: {} hosts, {} total URLs", urlsByHost.size(), urls.size());
        
        // Process each host's URLs concurrently but with connection reuse
        for (Map.Entry<String, List<String>> entry : urlsByHost.entrySet()) {
            String host = entry.getKey();
            List<String> hostUrls = entry.getValue();
            
            CompletableFuture<Map<String, CrawlResult>> hostFuture = CompletableFuture.supplyAsync(() -> {
                return crawlHostUrlsConcurrently(host, hostUrls, customHeaders);
            }, executorService);
            
            hostFutures.put(host, hostFuture);
        }
        
        // Combine all host results
        return CompletableFuture.allOf(hostFutures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, CrawlResult> allResults = new HashMap<>();
                    hostFutures.forEach((host, future) -> {
                        try {
                            Map<String, CrawlResult> hostResults = future.get();
                            allResults.putAll(hostResults);
                            logger.debug("✅ Completed crawling {} URLs for host: {}", hostResults.size(), host);
                        } catch (Exception e) {
                            logger.error("❌ Error crawling host {}: {}", host, e.getMessage());
                        }
                    });
                    logger.info("🎯 Enhanced batch crawl completed: {}/{} URLs successful", 
                              allResults.values().stream().mapToInt(r -> r.isSuccessful() ? 1 : 0).sum(),
                              allResults.size());
                    return allResults;
                });
    }
    
    /**
     * Group URLs by hostname for optimal connection reuse
     */
    private Map<String, List<String>> groupUrlsByHost(List<String> urls) {
        Map<String, List<String>> grouped = new HashMap<>();
        
        for (String url : urls) {
            try {
                String host = URI.create(url).getHost();
                grouped.computeIfAbsent(host, k -> new ArrayList<>()).add(url);
            } catch (Exception e) {
                logger.warn("⚠️ Invalid URL format, using fallback grouping: {}", url);
                grouped.computeIfAbsent("unknown", k -> new ArrayList<>()).add(url);
            }
        }
        
        return grouped;
    }
    
    /**
     * Crawl multiple URLs from the same host with optimal connection reuse
     */
    private Map<String, CrawlResult> crawlHostUrlsConcurrently(String host, List<String> urls, Map<String, String> customHeaders) {
        Map<String, CrawlResult> results = new HashMap<>();
        
        if (enableHttp2 && urls.size() > 1) {
            // Use HTTP/2 multiplexing for same-host URLs
            logger.debug("🔄 Using HTTP/2 multiplexing for {} URLs on host: {}", urls.size(), host);
            
            List<CompletableFuture<CrawlResult>> urlFutures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> crawl(url, customHeaders), processingPool))
                .toList();
            
            // Wait for all to complete
            CompletableFuture.allOf(urlFutures.toArray(new CompletableFuture[0])).join();
            
            // Collect results
            for (int i = 0; i < urls.size(); i++) {
                try {
                    CrawlResult result = urlFutures.get(i).get();
                    results.put(urls.get(i), result);
                } catch (Exception e) {
                    logger.error("Error in multiplexed crawl for URL: {}", urls.get(i), e);
                    CrawlResult errorResult = new CrawlResult(urls.get(i));
                    errorResult.setErrorMessage("Multiplexed crawl error: " + e.getMessage());
                    results.put(urls.get(i), errorResult);
                }
            }
        } else {
            // Standard sequential processing for HTTP/1.1 or single URL
            for (String url : urls) {
                results.put(url, crawl(url, customHeaders));
            }
        }
        
        return results;
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
        logger.info("🔄 Shutting down enhanced crawler with all thread pools...");
        
        // Log final thread pool statistics
        Map<String, Object> finalStats = getThreadPoolStats();
        logger.info("📊 Final Thread Pool Stats: {}", finalStats);
        logger.info("🔄 Processing Pool Active: {}, Parallelism: {}", 
                   processingPool.getActiveThreadCount(), processingPool.getParallelism());
        
        try {
            // Shutdown processing pool first (for concurrent response processing)
            processingPool.shutdown();
            if (!processingPool.awaitTermination(10, TimeUnit.SECONDS)) {
                processingPool.shutdownNow();
                logger.warn("⚠️ Processing pool forced shutdown");
            }
            
            // Shutdown monitoring service
            monitoringService.shutdown();
            if (!monitoringService.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringService.shutdownNow();
                logger.warn("⚠️ Monitoring service forced shutdown");
            }
            
            // Shutdown main executor service
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("⚠️ Thread pool did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
                
                // Wait a bit more for forced shutdown
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("❌ Thread pool did not terminate after forced shutdown");
                }
            }
            
            logger.info("✅ All enhanced thread pools shut down successfully");
            logger.info("🚀 Enhanced scalability features: HTTP/2={}, Concurrent Processing={}", 
                       enableHttp2, enableConcurrentProcessing);
            
        } catch (InterruptedException e) {
            logger.warn("🚨 Shutdown interrupted, forcing immediate termination");
            processingPool.shutdownNow();
            monitoringService.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Create a robust thread pool with custom thread factory for monitoring
     */
    private ThreadPoolExecutor createRobustThreadPool(int threadPoolSize) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
            threadPoolSize, 
            new RobustThreadFactory()
        );
        
        // Enable thread replacement and monitoring
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            logger.error("🚨 Task rejected! Thread pool may be overwhelmed. Active threads: {}, Queue size: {}", 
                        exec.getActiveCount(), exec.getQueue().size());
            
            // Try to execute in a new thread as fallback
            try {
                Thread fallbackThread = new Thread(runnable, "fallback-crawler-thread");
                fallbackThread.setDaemon(true);
                fallbackThread.start();
                threadsReplaced.incrementAndGet();
                logger.warn("⚡ Created fallback thread for rejected task");
            } catch (Exception e) {
                logger.error("❌ Failed to create fallback thread: {}", e.getMessage());
                throw new RuntimeException("Thread pool exhausted and fallback failed", e);
            }
        });
        
        return executor;
    }
    
    /**
     * Custom thread factory that monitors thread lifecycle
     */
    private class RobustThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(() -> {
                try {
                    runnable.run();
                } catch (OutOfMemoryError e) {
                    logger.error("🚨 CRITICAL: Thread died due to OutOfMemoryError! Thread: {}", Thread.currentThread().getName());
                    threadsReplaced.incrementAndGet();
                    throw e;
                } catch (Error e) {
                    logger.error("🚨 CRITICAL: Thread died due to JVM Error: {} - Thread: {}", e.getMessage(), Thread.currentThread().getName());
                    threadsReplaced.incrementAndGet();
                    throw e;
                } catch (Exception e) {
                    logger.warn("⚠️ Thread completed with exception: {} - Thread: {}", e.getMessage(), Thread.currentThread().getName());
                    // Exception in task - thread will be replaced automatically by ThreadPoolExecutor
                }
            }, "robust-crawler-thread-" + threadNumber.getAndIncrement());
            
            thread.setDaemon(false); // Ensure main threads are not daemon
            thread.setUncaughtExceptionHandler((t, e) -> {
                logger.error("🚨 Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
                threadsReplaced.incrementAndGet();
            });
            
            logger.debug("✨ Created new thread: {}", thread.getName());
            return thread;
        }
    }
    
    /**
     * Monitoring thread factory
     */
    private class MonitoringThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "thread-pool-monitor");
            thread.setDaemon(true);
            return thread;
        }
    }
    
    /**
     * Start monitoring thread pool health
     */
    private void startThreadPoolMonitoring() {
        monitoringService.scheduleAtFixedRate(() -> {
            try {
                checkThreadPoolHealth();
            } catch (Exception e) {
                logger.error("Error in thread pool monitoring: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }
    
    /**
     * Monitor thread pool health and take corrective action
     */
    private void checkThreadPoolHealth() {
        lastHealthCheck.set(System.currentTimeMillis());
        
        int activeThreads = executorService.getActiveCount();
        int poolSize = executorService.getPoolSize();
        int corePoolSize = executorService.getCorePoolSize();
        long completedTasks = executorService.getCompletedTaskCount();
        int queueSize = executorService.getQueue().size();
        
        logger.debug("🔍 Thread Pool Health Check:");
        logger.debug("   Active threads: {}/{}", activeThreads, poolSize);
        logger.debug("   Core pool size: {}", corePoolSize);
        logger.debug("   Completed tasks: {}", completedTasks);
        logger.debug("   Queue size: {}", queueSize);
        logger.debug("   Threads created: {}", threadsCreated.get());
        logger.debug("   Threads replaced: {}", threadsReplaced.get());
        
        // Check if thread pool is unhealthy
        if (poolSize < corePoolSize) {
            logger.warn("⚠️ Thread pool size ({}) is below core size ({}). Some threads may have died.", 
                       poolSize, corePoolSize);
            
            // Force thread pool to restore to core size
            executorService.prestartAllCoreThreads();
            logger.info("🔄 Attempted to restart core threads");
        }
        
        // Alert if queue is growing too large
        if (queueSize > 100) {
            logger.warn("⚠️ Thread pool queue is large ({}). Consider increasing thread pool size or reducing load.", queueSize);
        }
        
        // Alert on high thread replacement rate
        int replacementRate = threadsReplaced.get();
        if (replacementRate > originalThreadPoolSize * 2) {
            logger.warn("🚨 High thread replacement rate detected ({}). Check for memory leaks or thread-killing errors.", replacementRate);
        }
    }
    
    /**
     * Get thread pool health statistics
     */
    public Map<String, Object> getThreadPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeThreads", executorService.getActiveCount());
        stats.put("poolSize", executorService.getPoolSize());
        stats.put("corePoolSize", executorService.getCorePoolSize());
        stats.put("completedTasks", executorService.getCompletedTaskCount());
        stats.put("queueSize", executorService.getQueue().size());
        stats.put("threadsCreated", threadsCreated.get());
        stats.put("threadsReplaced", threadsReplaced.get());
        stats.put("lastHealthCheck", new java.util.Date(lastHealthCheck.get()));
        stats.put("isHealthy", executorService.getPoolSize() >= executorService.getCorePoolSize());
        return stats;
    }
} 