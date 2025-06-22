package com.webcrawler;

import com.webcrawler.core.ApiCrawler;
import com.webcrawler.model.CrawlResult;
import com.webcrawler.storage.CrawlDataStorage;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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
            
            // Initialize crawler and storage
            ApiCrawler crawler = new ApiCrawler(threadPoolSize, rateLimitMs, maxRetries, baseRetryDelayMs, backoffMultiplier);
            crawler.setUserAgent(userAgent);
            
            // Show retry configuration
            System.out.println("🔧 Retry Configuration:");
            System.out.println("   Max retries: " + crawler.getMaxRetries());
            System.out.println("   Base retry delay: " + crawler.getBaseRetryDelayMs() + "ms");
            System.out.println("   Backoff multiplier: " + crawler.getBackoffMultiplier() + "x");
            System.out.println();
            
            CrawlDataStorage storage = new CrawlDataStorage();
            
            if (cmd.hasOption("url")) {
                // Crawl single URL
                String url = cmd.getOptionValue("url");
                crawlSingleUrl(crawler, storage, url);
            } else if (cmd.hasOption("examples")) {
                // Run example crawls
                runExampleCrawls(crawler, storage);
            } else if (cmd.hasOption("stats")) {
                // Show statistics
                showStats(storage);
            } else {
                // Default: show examples
                System.out.println("No specific action requested. Here are some example APIs you can crawl:");
                printExampleApis();
                System.out.println("\nUse --examples to run example crawls, or --help for more options.");
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
                
        return options;
    }
    
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar api-web-crawler.jar", 
                "A Java web crawler for APIs and client-side rendered websites", 
                options, 
                "\nExamples:\n" +
                "  java -jar api-web-crawler.jar --examples\n" +
                "  java -jar api-web-crawler.jar --url https://api.github.com/repos/octocat/Hello-World\n" +
                "  java -jar api-web-crawler.jar --stats\n");
    }
    
    private static void crawlSingleUrl(ApiCrawler crawler, CrawlDataStorage storage, String url) {
        System.out.println("Crawling URL: " + url);
        
        CrawlResult result = crawler.crawl(url);
        storage.save(result);
        
        System.out.println("Result: " + result);
        
        if (result.isSuccessful() && result.getData() != null) {
            System.out.println("Data keys: " + result.getData().keySet());
        }
    }
    
    private static void runExampleCrawls(ApiCrawler crawler, CrawlDataStorage storage) {
        System.out.println("Running example API crawls...\n");
        
        List<String> exampleUrls = getExampleUrls();
        
        // Crawl asynchronously
        CompletableFuture<Map<String, CrawlResult>> future = crawler.crawlAsync(exampleUrls);
        
        try {
            Map<String, CrawlResult> results = future.get();
            
            System.out.println("Crawl Results:");
            System.out.println("==============");
            
            for (Map.Entry<String, CrawlResult> entry : results.entrySet()) {
                CrawlResult result = entry.getValue();
                System.out.printf("URL: %s%n", result.getUrl());
                System.out.printf("Status: %d%n", result.getStatusCode());
                System.out.printf("Duration: %dms%n", result.getCrawlDurationMs());
                System.out.printf("Success: %s%n", result.isSuccessful());
                
                if (result.getData() != null) {
                    System.out.printf("Data keys: %s%n", result.getData().keySet());
                    
                    // Show some interesting data
                    showInterestingData(result);
                }
                
                if (result.getErrorMessage() != null) {
                    System.out.printf("Error: %s%n", result.getErrorMessage());
                }
                
                System.out.println("---");
                
                // Save to storage
                storage.save(result);
            }
            
            System.out.println("\nCrawl completed. Results saved to database.");
            showStats(storage);
            
        } catch (Exception e) {
            logger.error("Error running example crawls", e);
            System.err.println("Error running crawls: " + e.getMessage());
        }
    }
    
    private static List<String> getExampleUrls() {
        return Arrays.asList(
            // GitHub API - Repository info
            "https://api.github.com/repos/octocat/Hello-World",
            
            // JSONPlaceholder - Test API
            "https://jsonplaceholder.typicode.com/posts/1",
            "https://jsonplaceholder.typicode.com/users/1",
            
            // CoinGecko API - Cryptocurrency data
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
            
            // REST Countries API
            "https://restcountries.com/v3.1/name/japan",
            
            // Cat Facts API
            "https://catfact.ninja/fact",
            
            // News API (may require API key for full access)
            "https://newsapi.org/v2/top-headlines?country=us&apiKey=demo",
            
            // Random User API
            "https://randomuser.me/api/",
            
            // Open Weather API (demo data)
            "https://samples.openweathermap.org/data/2.5/weather?q=London&appid=demo"
        );
    }
    
    private static void showInterestingData(CrawlResult result) {
        Map<String, Object> data = result.getData();
        String url = result.getUrl();
        
        try {
            if (url.contains("github.com")) {
                System.out.printf("  GitHub Repo: %s (Stars: %s, Language: %s)%n", 
                    data.get("name"), data.get("stargazers_count"), data.get("language"));
            } else if (url.contains("jsonplaceholder")) {
                if (data.containsKey("title")) {
                    System.out.printf("  Post: %s%n", data.get("title"));
                } else if (data.containsKey("name")) {
                    System.out.printf("  User: %s (%s)%n", data.get("name"), data.get("email"));
                }
            } else if (url.contains("coingecko")) {
                System.out.printf("  Bitcoin Price: $%s%n", 
                    ((Map<String, Object>) data.get("bitcoin")).get("usd"));
            } else if (url.contains("restcountries")) {
                List<?> countries = (List<?>) data;
                if (!countries.isEmpty()) {
                    Map<String, Object> country = (Map<String, Object>) countries.get(0);
                    System.out.printf("  Country: %s (Capital: %s)%n", 
                        ((Map<String, Object>) country.get("name")).get("common"),
                        ((List<?>) country.get("capital")).get(0));
                }
            } else if (url.contains("catfact")) {
                System.out.printf("  Cat Fact: %s%n", data.get("fact"));
            } else if (url.contains("randomuser")) {
                List<?> results = (List<?>) data.get("results");
                if (!results.isEmpty()) {
                    Map<String, Object> user = (Map<String, Object>) results.get(0);
                    Map<String, Object> name = (Map<String, Object>) user.get("name");
                    System.out.printf("  Random User: %s %s%n", name.get("first"), name.get("last"));
                }
            }
        } catch (Exception e) {
            // Ignore data parsing errors for display
            logger.debug("Error displaying data for {}: {}", url, e.getMessage());
        }
    }
    
    private static void showStats(CrawlDataStorage storage) {
        CrawlDataStorage.CrawlStats stats = storage.getStats();
        System.out.println("\nCrawling Statistics:");
        System.out.println("===================");
        System.out.println(stats);
        
        if (stats.getTotalRequests() > 0) {
            System.out.println("\nRecent successful results:");
            List<CrawlResult> recent = storage.getSuccessful(5);
            for (CrawlResult result : recent) {
                System.out.printf("  %s - %d (%dms)%n", 
                    result.getUrl(), result.getStatusCode(), result.getCrawlDurationMs());
            }
        }
    }
    
    private static void printExampleApis() {
        System.out.println("\nExample APIs you can crawl:");
        System.out.println("===========================");
        
        System.out.println("1. GitHub API:");
        System.out.println("   https://api.github.com/repos/octocat/Hello-World");
        System.out.println("   https://api.github.com/users/octocat");
        
        System.out.println("\n2. JSONPlaceholder (Testing API):");
        System.out.println("   https://jsonplaceholder.typicode.com/posts");
        System.out.println("   https://jsonplaceholder.typicode.com/users");
        
        System.out.println("\n3. CoinGecko (Cryptocurrency):");
        System.out.println("   https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd");
        
        System.out.println("\n4. REST Countries:");
        System.out.println("   https://restcountries.com/v3.1/all");
        System.out.println("   https://restcountries.com/v3.1/name/japan");
        
        System.out.println("\n5. Cat Facts API:");
        System.out.println("   https://catfact.ninja/fact");
        
        System.out.println("\n6. Random User API:");
        System.out.println("   https://randomuser.me/api/");
        
        System.out.println("\n7. News API (requires API key):");
        System.out.println("   https://newsapi.org/v2/top-headlines?country=us&apiKey=YOUR_KEY");
        
        System.out.println("\n8. OpenWeather API (requires API key):");
        System.out.println("   https://api.openweathermap.org/data/2.5/weather?q=London&appid=YOUR_KEY");
    }
} 