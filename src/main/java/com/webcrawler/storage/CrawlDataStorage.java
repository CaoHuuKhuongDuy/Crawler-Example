package com.webcrawler.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.model.CrawlResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage class for crawled data using SQLite
 */
public class CrawlDataStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(CrawlDataStorage.class);
    
    private final String databasePath;
    private final ObjectMapper objectMapper;
    
    public CrawlDataStorage(String databasePath) {
        this.databasePath = databasePath;
        this.objectMapper = new ObjectMapper();
        initializeDatabase();
    }
    
    public CrawlDataStorage() {
        this("crawl_data.db");
    }
    
    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS crawl_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                status_code INTEGER,
                data TEXT,
                headers TEXT,
                error_message TEXT,
                crawl_duration_ms INTEGER,
                timestamp TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Error initializing database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }
    
    /**
     * Save a single crawl result
     */
    public void save(CrawlResult result) {
        String insertSQL = """
            INSERT INTO crawl_results (url, status_code, data, headers, error_message, crawl_duration_ms, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            pstmt.setString(1, result.getUrl());
            pstmt.setInt(2, result.getStatusCode());
            
            // Convert data to JSON string
            String dataJson = null;
            if (result.getData() != null) {
                dataJson = objectMapper.writeValueAsString(result.getData());
            }
            pstmt.setString(3, dataJson);
            
            // Convert headers to JSON string
            String headersJson = null;
            if (result.getHeaders() != null) {
                headersJson = objectMapper.writeValueAsString(result.getHeaders());
            }
            pstmt.setString(4, headersJson);
            
            pstmt.setString(5, result.getErrorMessage());
            pstmt.setLong(6, result.getCrawlDurationMs());
            pstmt.setString(7, result.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            pstmt.executeUpdate();
            logger.debug("Saved crawl result for URL: {}", result.getUrl());
            
        } catch (Exception e) {
            logger.error("Error saving crawl result for URL: {}", result.getUrl(), e);
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
     * Get all crawl results for a specific URL
     */
    public List<CrawlResult> getByUrl(String url) {
        String selectSQL = """
            SELECT * FROM crawl_results 
            WHERE url = ? 
            ORDER BY created_at DESC
        """;
        
        List<CrawlResult> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setString(1, url);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving crawl results for URL: {}", url, e);
        }
        
        return results;
    }
    
    /**
     * Get recent crawl results
     */
    public List<CrawlResult> getRecent(int limit) {
        String selectSQL = """
            SELECT * FROM crawl_results 
            ORDER BY created_at DESC 
            LIMIT ?
        """;
        
        List<CrawlResult> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving recent crawl results", e);
        }
        
        return results;
    }
    
    /**
     * Get successful crawl results only
     */
    public List<CrawlResult> getSuccessful(int limit) {
        String selectSQL = """
            SELECT * FROM crawl_results 
            WHERE status_code >= 200 AND status_code < 300 AND error_message IS NULL
            ORDER BY created_at DESC 
            LIMIT ?
        """;
        
        List<CrawlResult> results = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving successful crawl results", e);
        }
        
        return results;
    }
    
    /**
     * Get crawl statistics
     */
    public CrawlStats getStats() {
        String statsSQL = """
            SELECT 
                COUNT(*) as total_requests,
                COUNT(CASE WHEN status_code >= 200 AND status_code < 300 AND error_message IS NULL THEN 1 END) as successful_requests,
                COUNT(CASE WHEN error_message IS NOT NULL OR status_code >= 400 THEN 1 END) as failed_requests,
                AVG(crawl_duration_ms) as avg_duration_ms,
                COUNT(DISTINCT url) as unique_urls
            FROM crawl_results
        """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(statsSQL)) {
            
            if (rs.next()) {
                return new CrawlStats(
                    rs.getInt("total_requests"),
                    rs.getInt("successful_requests"),
                    rs.getInt("failed_requests"),
                    rs.getDouble("avg_duration_ms"),
                    rs.getInt("unique_urls")
                );
            }
            
        } catch (SQLException e) {
            logger.error("Error retrieving crawl statistics", e);
        }
        
        return new CrawlStats(0, 0, 0, 0.0, 0);
    }
    
    private CrawlResult mapResultSet(ResultSet rs) throws Exception {
        CrawlResult result = new CrawlResult();
        result.setUrl(rs.getString("url"));
        result.setStatusCode(rs.getInt("status_code"));
        result.setErrorMessage(rs.getString("error_message"));
        result.setCrawlDurationMs(rs.getLong("crawl_duration_ms"));
        
        String timestampStr = rs.getString("timestamp");
        if (timestampStr != null) {
            result.setTimestamp(LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Parse JSON data
        String dataJson = rs.getString("data");
        if (dataJson != null) {
            result.setData(objectMapper.readValue(dataJson, objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class)));
        }
        
        // Parse JSON headers
        String headersJson = rs.getString("headers");
        if (headersJson != null) {
            result.setHeaders(objectMapper.readValue(headersJson, objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, String.class)));
        }
        
        return result;
    }
    
    /**
     * Statistics class for crawl data
     */
    public static class CrawlStats {
        private final int totalRequests;
        private final int successfulRequests;
        private final int failedRequests;
        private final double avgDurationMs;
        private final int uniqueUrls;
        
        public CrawlStats(int totalRequests, int successfulRequests, int failedRequests, double avgDurationMs, int uniqueUrls) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.avgDurationMs = avgDurationMs;
            this.uniqueUrls = uniqueUrls;
        }
        
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessfulRequests() { return successfulRequests; }
        public int getFailedRequests() { return failedRequests; }
        public double getAvgDurationMs() { return avgDurationMs; }
        public int getUniqueUrls() { return uniqueUrls; }
        public double getSuccessRate() { 
            return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0; 
        }
        
        @Override
        public String toString() {
            return String.format("CrawlStats{total=%d, successful=%d, failed=%d, success_rate=%.1f%%, avg_duration=%.1fms, unique_urls=%d}",
                    totalRequests, successfulRequests, failedRequests, getSuccessRate(), avgDurationMs, uniqueUrls);
        }
    }
} 