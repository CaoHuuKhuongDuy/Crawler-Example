package com.webcrawler;

import com.webcrawler.core.ApiCrawler;
import com.webcrawler.model.CrawlResult;
import com.webcrawler.storage.JsonFileStorage;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main application class for the API Web Crawler
 */
public class CrawlerApp {
    
    private static final Logger logger = LoggerFactory.getLogger(CrawlerApp.class);
    
    public static void main(String[] args) {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("help")) {
                printHelp(options);
                return;
            }
            
            // Configuration
            int threadPoolSize = Integer.parseInt(cmd.getOptionValue("threads", "10"));
            long rateLimitMs = Long.parseLong(cmd.getOptionValue("rate-limit", "1000"));
            String userAgent = cmd.getOptionValue("user-agent", "ApiWebCrawler/1.0");
            int maxRetries = Integer.parseInt(cmd.getOptionValue("max-retries", "3"));
            long baseRetryDelayMs = Long.parseLong(cmd.getOptionValue("retry-delay", "1000"));
            double backoffMultiplier = Double.parseDouble(cmd.getOptionValue("backoff-multiplier", "2.0"));
            
            // HTTP/2 and concurrency configuration
            boolean enableHttp2 = !cmd.hasOption("disable-http2"); // Default to true unless disabled
            if (cmd.hasOption("enable-http2")) {
                enableHttp2 = true; // Explicitly enable
            }
            
            boolean enableConcurrentProcessing = !cmd.hasOption("disable-concurrent-processing"); // Default to true unless disabled
            if (cmd.hasOption("enable-concurrent-processing")) {
                enableConcurrentProcessing = true; // Explicitly enable
            }
            
            int maxConnections = Integer.parseInt(cmd.getOptionValue("max-connections", "4"));
            
            // Guardian API specific configuration
            String fromDate = cmd.getOptionValue("from", "2025-06-01");
            String toDate = cmd.getOptionValue("to", "2025-06-30");
            String section = cmd.getOptionValue("section", null);
            int pageSize = Integer.parseInt(cmd.getOptionValue("page-size", "200"));
            
            // Initialize crawler and storage with advanced options
            ApiCrawler crawler = new ApiCrawler(threadPoolSize, rateLimitMs, maxRetries, baseRetryDelayMs, 
                                               backoffMultiplier, enableHttp2, enableConcurrentProcessing, maxConnections);
            crawler.setUserAgent(userAgent);
            
            // Show configuration
            System.out.println("🔧 Crawler Configuration:");
            System.out.println("   Thread Pool Size: " + threadPoolSize);
            System.out.println("   HTTP/2 Enabled: " + enableHttp2);
            System.out.println("   Concurrent Processing: " + enableConcurrentProcessing);
            System.out.println("   Max Connections per Host: " + maxConnections);
            System.out.println("   Rate Limit: " + rateLimitMs + "ms");
            System.out.println("   Max Retries: " + maxRetries);
            System.out.println("   Base Retry Delay: " + baseRetryDelayMs + "ms");
            System.out.println("   Backoff Multiplier: " + backoffMultiplier + "x");
            System.out.println();
            
            // Show retry configuration
            System.out.println("🔧 Retry Configuration:");
            System.out.println("   Max retries: " + crawler.getMaxRetries());
            System.out.println("   Base retry delay: " + crawler.getBaseRetryDelayMs() + "ms");
            System.out.println("   Backoff multiplier: " + crawler.getBackoffMultiplier() + "x");
            System.out.println();
            
            JsonFileStorage storage = new JsonFileStorage();
            
            if (cmd.hasOption("url")) {
                // Crawl single URL
                String url = cmd.getOptionValue("url");
                crawlSingleUrl(crawler, storage, url);
            } else if (cmd.hasOption("examples")) {
                // Run Guardian news crawl (like SimpleCrawler but with advanced features)
                runGuardianNewsCrawl(crawler, storage, fromDate, toDate, section, pageSize);
            } else if (cmd.hasOption("stats")) {
                // Show statistics
                showStats(storage);
            } else {
                // Default: show Guardian API usage
                System.out.println("🕷️  Enhanced API Web Crawler for Guardian News");
                System.out.println("==============================================");
                System.out.println("Usage:");
                System.out.println("  --examples                                   # Crawl Guardian news from June 2025 (default)");
                System.out.println("  --url <URL>                                  # Crawl a single URL");
                System.out.println("  --stats                                      # Show JSON file statistics");
                System.out.println();
                System.out.println("Guardian API Options:");
                System.out.println("  --from <YYYY-MM-DD>                         # Start date (default: 2025-06-01)");
                System.out.println("  --to <YYYY-MM-DD>                           # End date (default: 2025-06-30)");
                System.out.println("  --section <name>                            # Filter by section (sport, business, world, etc.)");
                System.out.println("  --page-size <N>                             # Articles per request (default: 200, >200 uses pagination)");
                System.out.println();
                System.out.println("Advanced Features:");
                System.out.println("  --threads <N>                                # Number of threads (default: 10)");
                System.out.println("  --enable-http2 / --disable-http2            # HTTP/2 multiplexing control");
                System.out.println("  --enable-concurrent-processing              # Concurrent response processing");
                System.out.println("  --max-connections <N>                       # Max connections per host (default: 4)");
                System.out.println("  --max-retries <N>                           # Retry attempts (default: 3)");
                System.out.println();
                System.out.println("Examples:");
                System.out.println("  mvn exec:java -Dexec.args=\"--examples --threads 20 --enable-http2\"");
                System.out.println("  mvn exec:java -Dexec.args=\"--examples --from 2024-01-01 --to 2024-12-31\"");
                System.out.println("  mvn exec:java -Dexec.args=\"--examples --section sport --page-size 50\"");
                System.out.println("  mvn exec:java -Dexec.args=\"--examples --disable-http2\"  # HTTP/1.1 only");
                System.out.println("  mvn exec:java -Dexec.args=\"--url https://content.guardianapis.com/search?api-key=test\"");
            }
            
            crawler.shutdown();
            
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            printHelp(options);
        } catch (Exception e) {
            logger.error("Application error", e);
            System.err.println("Application error: " + e.getMessage());
        }
    }
    
    private static Options createOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show help message")
                .build());
                
        options.addOption(Option.builder("u")
                .longOpt("url")
                .hasArg()
                .desc("Single URL to crawl")
                .build());
                
        options.addOption(Option.builder("e")
                .longOpt("examples")
                .desc("Run example crawls")
                .build());
                
        options.addOption(Option.builder("s")
                .longOpt("stats")
                .desc("Show crawling statistics")
                .build());
                
        options.addOption(Option.builder("t")
                .longOpt("threads")
                .hasArg()
                .desc("Number of threads (default: 10)")
                .build());
                
        options.addOption(Option.builder("r")
                .longOpt("rate-limit")
                .hasArg()
                .desc("Rate limit in milliseconds between requests (default: 1000)")
                .build());
                
        options.addOption(Option.builder("a")
                .longOpt("user-agent")
                .hasArg()
                .desc("User agent string (default: ApiWebCrawler/1.0)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("max-retries")
                .hasArg()
                .desc("Maximum number of retry attempts (default: 3)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("retry-delay")
                .hasArg()
                .desc("Base retry delay in milliseconds (default: 1000)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("backoff-multiplier")
                .hasArg()
                .desc("Exponential backoff multiplier (default: 2.0)")
                .build());
                
        // HTTP/2 and concurrency options
        options.addOption(Option.builder()
                .longOpt("enable-http2")
                .desc("Enable HTTP/2 multiplexing (default: true)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("disable-http2")
                .desc("Disable HTTP/2, use HTTP/1.1 only")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("enable-concurrent-processing")
                .desc("Enable concurrent response processing (default: true)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("disable-concurrent-processing")
                .desc("Disable concurrent response processing")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("max-connections")
                .hasArg()
                .desc("Maximum connections per host (default: 4)")
                .build());
                
        // Guardian API specific options
        options.addOption(Option.builder()
                .longOpt("from")
                .hasArg()
                .desc("Start date for Guardian news (YYYY-MM-DD, default: 2025-06-01)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("to")
                .hasArg()
                .desc("End date for Guardian news (YYYY-MM-DD, default: 2025-06-30)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("section")
                .hasArg()
                .desc("Guardian section filter (sport, business, world, politics, etc.)")
                .build());
                
        options.addOption(Option.builder()
                .longOpt("page-size")
                .hasArg()
                .desc("Number of articles per request (default: 200, >200 uses pagination)")
                .build());
                
        return options;
    }
    
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar api-web-crawler.jar", 
                "A Java web crawler for APIs and client-side rendered websites", 
                options, 
                "\nExamples:\n" +
                "  java -jar api-web-crawler.jar --examples\n" +
                "  java -jar api-web-crawler.jar --examples --from 2024-01-01 --to 2024-12-31\n" +
                "  java -jar api-web-crawler.jar --examples --section sport --page-size 50\n" +
                "  java -jar api-web-crawler.jar --url https://api.github.com/repos/octocat/Hello-World\n" +
                "  java -jar api-web-crawler.jar --examples --threads 20 --enable-http2\n" +
                "  java -jar api-web-crawler.jar --examples --enable-http2 --enable-concurrent-processing --max-connections 8\n" +
                "  java -jar api-web-crawler.jar --examples --disable-http2  # Use HTTP/1.1 only\n" +
                "  java -jar api-web-crawler.jar --stats\n");
    }
    
    private static void crawlSingleUrl(ApiCrawler crawler, JsonFileStorage storage, String url) {
        System.out.println("Crawling URL: " + url);
        
        CrawlResult result = crawler.crawl(url);
        storage.save(result);
        
        System.out.println("Result: " + result);
        
        if (result.isSuccessful() && result.getData() != null) {
            System.out.println("Data keys: " + result.getData().keySet());
        }
    }
    
    private static void runGuardianNewsCrawl(ApiCrawler crawler, JsonFileStorage storage, String fromDate, String toDate, String section, int pageSize) {
        if (section != null) {
            System.out.println("📰 Crawling Guardian " + section + " news from " + fromDate + " to " + toDate + " with enhanced features...\n");
        } else {
            System.out.println("📰 Crawling Guardian news from " + fromDate + " to " + toDate + " with enhanced features...\n");
        }
        
        List<String> newsUrls = getGuardianUrls(fromDate, toDate, section, pageSize);
        
        // Crawl asynchronously with enhanced features
        CompletableFuture<Map<String, CrawlResult>> future = crawler.crawlAsync(newsUrls);
        
        try {
            Map<String, CrawlResult> results = future.get();
            List<CrawlResult> allResults = new ArrayList<>();
            
            // Check if we have multiple paginated results to combine
            if (newsUrls.size() > 1) {
                // Multiple paginated requests - combine results
                CrawlResult combinedResult = combinePaginatedResults(results, newsUrls, fromDate, toDate, section, pageSize);
                allResults.add(combinedResult);
                
                System.out.println("🔍 Combined paginated results from " + newsUrls.size() + " requests");
                if (combinedResult.isSuccessful()) {
                    System.out.println("✅ Success - Combined " + newsUrls.size() + " pages - Duration: " + combinedResult.getCrawlDurationMs() + "ms");
                    showGuardianNewsData(combinedResult);
                } else {
                    System.out.println("❌ Failed to combine paginated results");
                }
            } else {
                // Single request - process normally
                for (Map.Entry<String, CrawlResult> entry : results.entrySet()) {
                    CrawlResult result = entry.getValue();
                    
                    System.out.println("🔍 " + result.getUrl());
                    
                    if (result.isSuccessful()) {
                        System.out.println("✅ Success - Status: " + result.getStatusCode() + 
                                         " - Duration: " + result.getCrawlDurationMs() + "ms");
                        
                        // Show Guardian-specific data
                        showGuardianNewsData(result);
                    } else {
                        System.out.println("❌ Failed - Status: " + result.getStatusCode() + 
                                         " - Error: " + result.getErrorMessage());
                    }
                    
                    // Add to results list
                    allResults.add(result);
                }
            }
            
            System.out.println("---");
            
            // Save results to Guardian-specific file
            String filename;
            if (section != null && !section.trim().isEmpty()) {
                filename = "guardian_" + section.trim().toLowerCase() + "_news_" + fromDate + "_to_" + toDate + ".json";
            } else {
                filename = "guardian_news_" + fromDate + "_to_" + toDate + ".json";
            }
            storage.saveAllToSingleFile(allResults, filename);
            
            System.out.println("\n📊 Crawl Summary:");
            System.out.println("================");
            System.out.println("Total URLs: " + newsUrls.size());
            System.out.println("✅ Successful: " + allResults.stream().mapToInt(r -> r.isSuccessful() ? 1 : 0).sum());
            System.out.println("❌ Failed: " + allResults.stream().mapToInt(r -> r.isSuccessful() ? 0 : 1).sum());
            System.out.println("⏱️  Total Duration: " + allResults.stream().mapToLong(CrawlResult::getCrawlDurationMs).sum() + "ms");
            System.out.println("⚡ Average Duration: " + allResults.stream().mapToLong(CrawlResult::getCrawlDurationMs).average().orElse(0) + "ms");
            System.out.println("🎯 Success Rate: " + String.format("%.1f%%", 
                allResults.stream().mapToDouble(r -> r.isSuccessful() ? 100.0 : 0.0).average().orElse(0)));
            
            System.out.println("\n✅ Guardian news crawling completed! Check the 'output' directory for JSON files.");
            
        } catch (Exception e) {
            logger.error("Error running Guardian news crawls", e);
            System.err.println("Error running Guardian news crawls: " + e.getMessage());
        }
    }
    
    private static List<String> getGuardianUrls(String fromDate, String toDate, String section, int pageSize) {
        List<String> urls = new ArrayList<>();
        
        // Guardian API has a maximum page-size of 200
        int maxPageSize = 200;
        
        if (pageSize <= maxPageSize) {
            // Single request - within API limits
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("https://content.guardianapis.com/search?from-date=")
                      .append(fromDate)
                      .append("&to-date=")
                      .append(toDate)
                      .append("&show-fields=headline,byline,body,thumbnail")
                      .append("&page-size=")
                      .append(pageSize)
                      .append("&api-key=test");
            
            // Add section filter if specified
            if (section != null && !section.trim().isEmpty()) {
                urlBuilder.append("&section=").append(section.trim().toLowerCase());
            }
            
            urls.add(urlBuilder.toString());
        } else {
            // Multiple requests - pagination needed
            int totalRequests = (int) Math.ceil((double) pageSize / maxPageSize);
            
            System.out.println("📄 Page size (" + pageSize + ") exceeds Guardian API limit (200).");
            System.out.println("📄 Will make " + totalRequests + " paginated requests to fetch " + pageSize + " articles...\n");
            
            for (int page = 1; page <= totalRequests; page++) {
                int currentPageSize = Math.min(maxPageSize, pageSize - (page - 1) * maxPageSize);
                
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append("https://content.guardianapis.com/search?from-date=")
                          .append(fromDate)
                          .append("&to-date=")
                          .append(toDate)
                          .append("&show-fields=headline,byline,body,thumbnail")
                          .append("&page-size=")
                          .append(currentPageSize)
                          .append("&page=")
                          .append(page)
                          .append("&api-key=test");
                
                // Add section filter if specified
                if (section != null && !section.trim().isEmpty()) {
                    urlBuilder.append("&section=").append(section.trim().toLowerCase());
                }
                
                urls.add(urlBuilder.toString());
            }
        }
        
        return urls;
    }
    
    /**
     * Combine multiple paginated Guardian API results into a single result
     */
    private static CrawlResult combinePaginatedResults(Map<String, CrawlResult> results, List<String> urls, 
                                                      String fromDate, String toDate, String section, int requestedPageSize) {
        // Create a combined URL for the result
        String combinedUrl = "https://content.guardianapis.com/search?from-date=" + fromDate + "&to-date=" + toDate;
        if (section != null && !section.trim().isEmpty()) {
            combinedUrl += "&section=" + section.trim().toLowerCase();
        }
        combinedUrl += "&page-size=" + requestedPageSize + "&api-key=test";
        
        CrawlResult combinedResult = new CrawlResult(combinedUrl);
        
        List<Map<String, Object>> allArticles = new ArrayList<>();
        long totalDuration = 0;
        int successfulRequests = 0;
        int totalArticles = 0;
        String lastError = null;
        
        // Process each paginated result
        for (String url : urls) {
            CrawlResult pageResult = results.get(url);
            if (pageResult != null) {
                totalDuration += pageResult.getCrawlDurationMs();
                
                if (pageResult.isSuccessful() && pageResult.getData() != null) {
                    successfulRequests++;
                    
                    try {
                        // Extract articles from this page
                        Map<String, Object> data = pageResult.getData();
                        
                        // The data structure is: data -> response -> results (direct, not nested under URL)
                        if (data.containsKey("response")) {
                            Map<String, Object> apiResponse = (Map<String, Object>) data.get("response");
                            
                            if (apiResponse.containsKey("results")) {
                                List<Map<String, Object>> pageArticles = (List<Map<String, Object>>) apiResponse.get("results");
                                allArticles.addAll(pageArticles);
                                
                                // Get total count from first successful response
                                if (totalArticles == 0 && apiResponse.containsKey("total")) {
                                    totalArticles = (Integer) apiResponse.get("total");
                                }
                            }
                        } else {
                            // Alternative: try the nested structure (data -> {url} -> response -> results)
                            for (Map.Entry<String, Object> entry : data.entrySet()) {
                                Object responseObj = entry.getValue();
                                if (responseObj instanceof Map) {
                                    Map<String, Object> responseMap = (Map<String, Object>) responseObj;
                                    if (responseMap.containsKey("response")) {
                                        Map<String, Object> apiResponse = (Map<String, Object>) responseMap.get("response");
                                        
                                        if (apiResponse.containsKey("results")) {
                                            List<Map<String, Object>> pageArticles = (List<Map<String, Object>>) apiResponse.get("results");
                                            allArticles.addAll(pageArticles);
                                            
                                            // Get total count from first successful response
                                            if (totalArticles == 0 && apiResponse.containsKey("total")) {
                                                totalArticles = (Integer) apiResponse.get("total");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error processing paginated result from {}: {}", url, e.getMessage());
                        lastError = "Error processing page: " + e.getMessage();
                    }
                } else {
                    lastError = pageResult.getErrorMessage();
                }
            } else {
                logger.warn("No result found for URL: {}", url);
            }
        }
        
        // Set combined result properties
        combinedResult.setCrawlDurationMs(totalDuration);
        
        if (successfulRequests > 0) {
            // Create combined response structure
            Map<String, Object> combinedData = new HashMap<>();
            Map<String, Object> combinedResponse = new HashMap<>();
            Map<String, Object> apiResponse = new HashMap<>();
            
            apiResponse.put("status", "ok");
            apiResponse.put("total", totalArticles);
            apiResponse.put("results", allArticles);
            apiResponse.put("pages", urls.size());
            apiResponse.put("pagesCombined", successfulRequests);
            apiResponse.put("articlesRetrieved", allArticles.size());
            
            combinedResponse.put("response", apiResponse);
            combinedData.put(combinedUrl, combinedResponse);
            
            combinedResult.setData(combinedData);
            combinedResult.setStatusCode(200);
            
            System.out.println("📄 Successfully combined " + successfulRequests + "/" + urls.size() + " pages");
            System.out.println("📄 Retrieved " + allArticles.size() + " articles out of " + totalArticles + " total available");
        } else {
            // All requests failed
            combinedResult.setStatusCode(400);
            combinedResult.setErrorMessage("All paginated requests failed. Last error: " + lastError);
        }
        
        return combinedResult;
    }
    
    private static void showGuardianNewsData(CrawlResult result) {
        Map<String, Object> data = result.getData();
        String url = result.getUrl();
        
        try {
            if (url.contains("guardian")) {
                Map<String, Object> response = (Map<String, Object>) data.get("response");
                List<?> results = (List<?>) response.get("results");
                Integer total = (Integer) response.get("total");
                
                System.out.println("📰 Guardian News (" + url + "): Found " + results.size() + " articles out of " + total + " total");
                
                // Show first few article headlines
                for (int i = 0; i < Math.min(3, results.size()); i++) {
                    Map<String, Object> article = (Map<String, Object>) results.get(i);
                    String headline = (String) article.get("webTitle");
                    String section = (String) article.get("sectionName");
                    System.out.println("   📝 [" + section + "] " + headline);
                }
                
                if (results.size() > 3) {
                    System.out.println("   ... and " + (results.size() - 3) + " more articles");
                }
            }
            
        } catch (Exception e) {
            logger.debug("Error displaying Guardian news data for {}: {}", url, e.getMessage());
            System.out.println("📰 Guardian data available (parsing error)");
        }
    }
    
    private static void showStats(JsonFileStorage storage) {
        System.out.println("\nJSON File Storage Statistics:");
        System.out.println("============================");
        
        List<String> jsonFiles = storage.listJsonFiles();
        System.out.println("Output Directory: " + storage.getOutputDirectory());
        System.out.println("Total JSON Files: " + jsonFiles.size());
        
        if (!jsonFiles.isEmpty()) {
            System.out.println("\nAvailable JSON Files:");
            for (String filename : jsonFiles) {
                System.out.println("  📄 " + filename);
            }
        } else {
            System.out.println("\nNo JSON files found. Run some crawls first!");
        }
    }
} 