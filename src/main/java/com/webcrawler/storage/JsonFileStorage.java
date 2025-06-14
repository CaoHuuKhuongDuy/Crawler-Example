package com.webcrawler.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.webcrawler.model.CrawlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple JSON file storage for crawled data
 */
public class JsonFileStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonFileStorage.class);
    
    private final ObjectMapper objectMapper;
    private final String outputDirectory;
    
    public JsonFileStorage() {
        this("output");
    }
    
    public JsonFileStorage(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        this.objectMapper = new ObjectMapper();
        
        // Enable pretty printing with proper indentation
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Additional formatting options for better readability
        this.objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Configure the default pretty printer for consistent formatting
        this.objectMapper.setDefaultPrettyPrinter(new DefaultPrettyPrinter()
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE));
        
        this.objectMapper.findAndRegisterModules(); // Register JSR310 module for LocalDateTime
        
        // Create output directory if it doesn't exist
        createOutputDirectory();
    }
    
    private void createOutputDirectory() {
        try {
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                logger.info("Created output directory: {}", outputDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create output directory: {}", outputDirectory, e);
            throw new RuntimeException("Cannot create output directory", e);
        }
    }
    
    /**
     * Save a single crawl result to JSON file (clean data only)
     */
    public void save(CrawlResult result) {
        try {
            String filename = generateFilename(result.getUrl(), result.getTimestamp());
            File outputFile = new File(outputDirectory, filename);
            
            // Save only the clean data, not all the metadata
            if (result.getData() != null) {
                objectMapper.writeValue(outputFile, result.getData());
                logger.info("Saved clean data to: {}", outputFile.getAbsolutePath());
            } else {
                logger.warn("No data to save for URL: {}", result.getUrl());
            }
            
        } catch (IOException e) {
            logger.error("Failed to save crawl result for URL: {}", result.getUrl(), e);
        }
    }
    
    /**
     * Save multiple crawl results
     */
    public void saveAll(List<CrawlResult> results) {
        for (CrawlResult result : results) {
            save(result);
        }
    }
    
    /**
     * Save all results to a single JSON file (clean data only)
     */
    public void saveAllToSingleFile(List<CrawlResult> results, String filename) {
        try {
            File outputFile = new File(outputDirectory, filename);
            
            // Create a clean map of URL -> data
            Map<String, Object> cleanResults = new LinkedHashMap<>();
            for (CrawlResult result : results) {
                if (result.getData() != null) {
                    // Use a clean URL as the key
                    String cleanUrl = result.getUrl().replaceAll("https?://", "");
                    cleanResults.put(cleanUrl, result.getData());
                }
            }
            
            objectMapper.writeValue(outputFile, cleanResults);
            logger.info("Saved {} clean results to: {}", cleanResults.size(), outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.error("Failed to save crawl results to file: {}", filename, e);
        }
    }
    
    /**
     * Load crawl results from a JSON file
     */
    public List<CrawlResult> loadFromFile(String filename) {
        try {
            File inputFile = new File(outputDirectory, filename);
            if (!inputFile.exists()) {
                logger.warn("File does not exist: {}", inputFile.getAbsolutePath());
                return new ArrayList<>();
            }
            
            CrawlResult[] results = objectMapper.readValue(inputFile, CrawlResult[].class);
            logger.info("Loaded {} crawl results from: {}", results.length, inputFile.getAbsolutePath());
            
            return List.of(results);
            
        } catch (IOException e) {
            logger.error("Failed to load crawl results from file: {}", filename, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Generate a filename based on URL (without timestamp for overwriting)
     */
    private String generateFilename(String url, LocalDateTime timestamp) {
        // Clean URL to make it filename-safe
        String cleanUrl = url.replaceAll("https?://", "")
                            .replaceAll("[^a-zA-Z0-9.-]", "_")
                            .replaceAll("_+", "_")
                            .replaceAll("^_|_$", "");
        
        // If URL is too long, truncate it
        if (cleanUrl.length() > 50) {
            cleanUrl = cleanUrl.substring(0, 50);
        }
        
        return String.format("crawl_%s.json", cleanUrl);
    }
    
    /**
     * List all JSON files in the output directory
     */
    public List<String> listJsonFiles() {
        List<String> jsonFiles = new ArrayList<>();
        
        File dir = new File(outputDirectory);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    jsonFiles.add(file.getName());
                }
            }
        }
        
        return jsonFiles;
    }
    
    public String getOutputDirectory() {
        return outputDirectory;
    }
} 