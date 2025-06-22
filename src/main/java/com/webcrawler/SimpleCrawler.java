package com.webcrawler;

import com.webcrawler.core.ApiCrawler;
import com.webcrawler.model.CrawlResult;
import com.webcrawler.storage.JsonFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Simple web crawler that crawls APIs and saves data as JSON files
 */
public class SimpleCrawler {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleCrawler.class);
    
    public static void main(String[] args) {
        System.out.println("🕷️  Simple API Web Crawler");
        System.out.println("==========================");
        
        // Show usage if no arguments provided
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  java -jar crawler.jar                           # Crawl all available news from June 2025 (default)");
            System.out.println("  java -jar crawler.jar --from 2024-01-01 --to 2024-12-31  # Crawl all news from custom date range");
            System.out.println("  java -jar crawler.jar --section sport          # Filter by section (sport, business, world, etc.)");
            System.out.println("  java -jar crawler.jar --page-size 20           # Limit number of articles per request (default: 200 = all)");
            System.out.println("  java -jar crawler.jar --from 2024-01-01 --to 2024-01-31 --section business --page-size 100  # Combine all filters");
            System.out.println();
            System.out.println("Available sections: world, business, sport, politics, technology, environment, society, culture, etc.");
            System.out.println("Page size range: 1-200 (Guardian API limit, default 200 gets all available articles)");
            System.out.println();
        }
        
        // Initialize crawler and storage
        ApiCrawler crawler = new ApiCrawler();
        JsonFileStorage storage = new JsonFileStorage();
        
        try {
            // Default to June 2025 if no dates specified
            String fromDate = "2025-06-01";
            String toDate = "2025-06-30";
            String section = null; // null means all sections
            int pageSize = 200; // Default page size
            
            // Parse command line arguments
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--from") && i + 1 < args.length) {
                    fromDate = args[i + 1];
                    i++; // Skip next argument as it's the value
                } else if (args[i].equals("--to") && i + 1 < args.length) {
                    toDate = args[i + 1];
                    i++; // Skip next argument as it's the value
                } else if (args[i].equals("--section") && i + 1 < args.length) {
                    section = args[i + 1];
                    i++; // Skip next argument as it's the value
                } else if (args[i].equals("--page-size") && i + 1 < args.length) {
                    pageSize = Integer.parseInt(args[i + 1]);
                    i++; // Skip next argument as it's the value
                }
            }
            
            // Crawl Guardian news with specified parameters
            runGuardianNewsCrawl(crawler, storage, fromDate, toDate, section, pageSize);
            
        } catch (Exception e) {
            logger.error("Error during crawling", e);
            System.err.println("Error: " + e.getMessage());
        } finally {
            crawler.shutdown();
        }
        
        System.out.println("\n✅ Crawling completed! Check the 'output' directory for JSON files.");
    }
    
    private static void runGuardianNewsCrawl(ApiCrawler crawler, JsonFileStorage storage, String fromDate, String toDate, String section, int pageSize) {
        if (section != null) {
            System.out.println("📰 Crawling Guardian " + section + " news from " + fromDate + " to " + toDate + "...\n");
        } else {
            System.out.println("📰 Crawling Guardian news from " + fromDate + " to " + toDate + "...\n");
        }
        
        List<String> newsUrls = getGuardianUrlsPartitioned(fromDate, toDate, section, pageSize);
        
        // Crawl asynchronously for better performance
        CompletableFuture<Map<String, CrawlResult>> future = crawler.crawlAsync(newsUrls);
        
        try {
            Map<String, CrawlResult> results = future.get();
            List<CrawlResult> allResults = new ArrayList<>();
            
            for (Map.Entry<String, CrawlResult> entry : results.entrySet()) {
                CrawlResult result = entry.getValue();
                
                System.out.println("🔍 " + result.getUrl());
                
                if (result.isSuccessful()) {
                    System.out.println("✅ Success - Status: " + result.getStatusCode() + 
                                     " - Duration: " + result.getCrawlDurationMs() + "ms");
                    
                    // Show news-specific data
                    showNewsData(result);
                } else {
                    System.out.println("❌ Failed - Status: " + result.getStatusCode() + 
                                     " - Error: " + result.getErrorMessage());
                }
                
                // Add to results list (no individual file saving)
                allResults.add(result);
                
                System.out.println("---");
            }
            
            // Save all results to a Guardian-specific file
            String uniqueId = UUID.randomUUID().toString();
            String filename = "guardian_news_" + fromDate + "_to_" + toDate + "_" + uniqueId + ".json";
            storage.saveAllToSingleFile(allResults, filename);
            
            printSummary(allResults);
            
        } catch (Exception e) {
            logger.error("Error running news crawls", e);
            System.err.println("Error running news crawls: " + e.getMessage());
        }
    }

    private static List<String> getGuardianUrls(String fromDate, String toDate, String section, int pageSize) {
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

        return Arrays.asList(urlBuilder.toString());
    }

    private static List<String> getGuardianUrlsPartitioned(String fromDate, String toDate, String section, int pageSize) {
        List<String> urls = new ArrayList<>();
        LocalDate start = LocalDate.parse(fromDate);
        LocalDate end = LocalDate.parse(toDate);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            urls.add(buildGuardianUrl(date.toString(), date.toString(), section, pageSize));
        }
        return urls;
    }

    // In SimpleCrawler.java, update buildGuardianUrl and getGuardianUrlsPartitioned:
    private static String buildGuardianUrl(String from, String to, String section, int pageSize) {
        String apiKey = System.getenv().getOrDefault("GUARDIAN_API_KEY", "test");
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://content.guardianapis.com/search?from-date=")
                .append(from)
                .append("&to-date=")
                .append(to)
                .append("&show-fields=headline,byline,body,thumbnail")
                .append("&page-size=")
                .append(pageSize)
                .append("&api-key=").append(apiKey);
        if (section != null && !section.trim().isEmpty()) {
            urlBuilder.append("&section=").append(section.trim().toLowerCase());
        }
        return urlBuilder.toString();
    }
    
    private static void showNewsData(CrawlResult result) {
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
            logger.debug("Error displaying news data for {}: {}", url, e.getMessage());
            System.out.println("📰 Guardian data available (parsing error)");
        }
    }
    
    private static void printSummary(List<CrawlResult> results) {
        int successful = 0;
        int failed = 0;
        long totalDuration = 0;
        
        for (CrawlResult result : results) {
            if (result.isSuccessful()) {
                successful++;
            } else {
                failed++;
            }
            totalDuration += result.getCrawlDurationMs();
        }
        
        System.out.println("\n📊 Crawl Summary:");
        System.out.println("================");
        System.out.println("Total URLs: " + results.size());
        System.out.println("✅ Successful: " + successful);
        System.out.println("❌ Failed: " + failed);
        System.out.println("⏱️  Total Duration: " + totalDuration + "ms");
        System.out.println("⚡ Average Duration: " + (results.size() > 0 ? totalDuration / results.size() : 0) + "ms");
        System.out.println("🎯 Success Rate: " + (results.size() > 0 ? (successful * 100 / results.size()) : 0) + "%");
    }
} 