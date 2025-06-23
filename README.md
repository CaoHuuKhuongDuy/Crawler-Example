# Guardian News Crawler

A focused web crawler specifically designed to fetch news articles from The Guardian API with flexible filtering options.

## Features

- **Guardian API Integration**: Direct access to The Guardian's comprehensive news database
- **Date Range Filtering**: Crawl news from specific time periods using `--from` and `--to` parameters
- **Section Filtering**: Filter articles by Guardian sections (sport, business, world, politics, etc.)
- **Page Size Control**: Control the number of articles retrieved (1-200, default: 200 for maximum coverage)
- **Rich Content**: Retrieves headlines, bylines, full article body, thumbnails, and metadata
- **Beautiful JSON Output**: Clean, formatted JSON files with proper indentation and alphabetical key ordering
- **🚀 Multithreaded Architecture**: Configurable thread pool for concurrent crawling (scalable from 1-N threads)
- **🛡️ Fault Tolerance**: Automatic retry with exponential backoff for resilient data collection
- **⚡ Smart Rate Limiting**: Configurable delays to respect API limits and avoid overwhelming servers

## Getting Started

### Prerequisites

- **Java 11 or higher** - Required runtime environment
- **Maven 3.6 or higher** - For building and dependency management

### Installation & Build

1. **Clone or download the project**
   ```bash
   # If using git
   git clone <repository-url>
   cd crawler
   
   # Or extract if downloaded as ZIP
   ```

2. **Build the project**
   ```bash
   # Clean and compile
   mvn clean compile
   
   # Or build with tests (optional)
   mvn clean test compile
   ```

3. **Verify build**
   ```bash
   # Check if compilation was successful
   mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler"
   ```

### Running the Crawler

#### Method 1: Using Maven (Recommended)
```bash
# Basic run with default settings (June 2025, all sections, max 200 articles)
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler"

# With custom parameters
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--from 2024-01-01 --to 2024-12-31"
```

#### Method 2: Building JAR (Optional)
```bash
# Build executable JAR
mvn clean package

# Run the JAR
java -jar target/crawler-1.0-SNAPSHOT.jar --from 2024-01-01 --to 2024-12-31
```

## Quick Start

```bash
# Compile the project
mvn clean compile

# Get all available news from June 2025 (default)
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler"

# Get all news from a custom date range
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--from 2024-01-01 --to 2024-12-31"

# Filter by section
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--section sport"

# Control page size (limit results)
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--page-size 50"

# Combine all filters
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--from 2024-01-01 --to 2024-01-31 --section business --page-size 100"
```

## Command Line Options

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `--from` | Start date (YYYY-MM-DD) | 2025-06-01 | `--from 2024-01-01` |
| `--to` | End date (YYYY-MM-DD) | 2025-06-30 | `--to 2024-12-31` |
| `--section` | Guardian section filter | All sections | `--section sport` |
| `--page-size` | Number of articles per request | 200 (max coverage) | `--page-size 50` |

## Available Guardian Sections

- `world` - International news
- `business` - Business and finance
- `sport` - Sports coverage
- `politics` - Political news
- `technology` - Tech news
- `environment` - Environmental stories
- `society` - Social issues
- `culture` - Arts and culture
- `science` - Science news
- `media` - Media industry
- `education` - Education news
- `healthcare` - Health news

## Output Format

The crawler generates JSON files in the `output/` directory with the following naming convention:

- All sections: `guardian_news_YYYY-MM-DD_to_YYYY-MM-DD.json`
- Specific section: `guardian_SECTION_news_YYYY-MM-DD_to_YYYY-MM-DD.json`

### Sample JSON Structure

```json
{
  "response": {
    "status": "ok",
    "userTier": "developer",
    "total": 188,
    "startIndex": 1,
    "pageSize": 200,
    "currentPage": 1,
    "pages": 1,
    "orderBy": "newest",
    "results": [
      {
        "id": "sport/2025/may/05/zhao-xintong-snooker-world-championship",
        "type": "article",
        "sectionId": "sport",
        "sectionName": "Sport",
        "webPublicationDate": "2025-05-05T21:30:00Z",
        "webTitle": "Zhao Xintong beats Mark Williams to become China's first snooker world champion",
        "webUrl": "https://www.theguardian.com/sport/2025/may/05/zhao-xintong-snooker-world-championship",
        "apiUrl": "https://content.guardianapis.com/sport/2025/may/05/zhao-xintong-snooker-world-championship",
        "fields": {
          "headline": "Zhao Xintong beats Mark Williams to become China's first snooker world champion",
          "byline": "Ewan Murray",
          "body": "<p>Full article content here...</p>",
          "thumbnail": "https://media.guim.co.uk/..."
        },
        "isHosted": false,
        "pillarId": "pillar/sport",
        "pillarName": "Sport"
      }
    ]
  }
}
```

## Project Structure

```
crawler/
├── src/main/java/com/webcrawler/
│   ├── SimpleCrawler.java          # Main application entry point
│   ├── core/
│   │   └── ApiCrawler.java         # HTTP client and crawling logic
│   ├── model/
│   │   └── CrawlResult.java        # Data model for crawl results
│   └── storage/
│       └── JsonFileStorage.java    # JSON file output handling
├── output/                         # Generated JSON files
├── pom.xml                        # Maven dependencies
└── README.md                      # This file
```

## Dependencies

- **Java 11+**: Required runtime
- **Jackson**: JSON processing and formatting
- **SLF4J + Logback**: Logging framework
- **OkHttp**: HTTP client for API requests

## 🏗️ Architecture & Performance

### Multithreading & Scalability
- **Thread Pool**: Configurable concurrent processing (default: 10 threads)
- **Asynchronous Execution**: Non-blocking HTTP requests using `CompletableFuture`
- **Parallel Processing**: Multiple URLs processed simultaneously for optimal performance
- **Resource Management**: Proper thread pool lifecycle management with graceful shutdown

### 🛡️ Fault Tolerance & Reliability
- **Automatic Retry**: Failed requests are automatically retried up to 3 times (configurable)
- **Exponential Backoff**: Smart retry delays that increase progressively (1s → 2s → 4s → 8s...)
- **Intelligent Retry Logic**: Only retries on temporary failures (5xx errors, timeouts, rate limits)
- **Network Resilience**: Handles connection timeouts, resets, and other transient network issues
- **🧵 Thread-Level Fault Tolerance**: Automatic thread replacement when threads die or crash
- **🔍 Real-Time Thread Monitoring**: Health checks every 30 seconds with proactive recovery
- **💾 Memory Error Recovery**: Handles OutOfMemoryError and JVM crashes gracefully
- **📊 Thread Pool Analytics**: Comprehensive statistics and health metrics

#### Retryable Conditions
- **HTTP Status Codes**: `500`, `502`, `503`, `504`, `429` (rate limiting), `408` (timeout)
- **Network Errors**: Connection timeouts, connection refused, network unreachable
- **Server Overload**: Automatic backoff when servers are temporarily unavailable

#### Thread Fault Tolerance
- **Thread Death Detection**: Monitors for threads killed by OutOfMemoryError, JVM errors, or exceptions
- **Automatic Replacement**: Dead threads are automatically replaced to maintain pool size
- **Health Monitoring**: Periodic health checks (every 30 seconds) with automated recovery
- **Fallback Threads**: Creates emergency threads when main pool is overwhelmed
- **Pool Recovery**: Automatically restarts core threads if pool size degrades
- **Exception Handling**: Comprehensive uncaught exception handling with logging

### ⚙️ Configurable Options

#### CrawlerApp Advanced Options
```bash
# Configure threading and retry behavior
java -jar crawler.jar --threads 20 --max-retries 5 --retry-delay 500 --backoff-multiplier 1.5

# Conservative approach (more retries, longer delays)
java -jar crawler.jar --threads 5 --max-retries 5 --retry-delay 2000

# Aggressive approach (more threads, faster retries)
java -jar crawler.jar --threads 25 --max-retries 2 --retry-delay 500
```

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `--threads` | Number of concurrent threads | 10 | `--threads 20` |
| `--max-retries` | Maximum retry attempts | 3 | `--max-retries 5` |
| `--retry-delay` | Base retry delay (ms) | 1000 | `--retry-delay 500` |
| `--backoff-multiplier` | Exponential backoff multiplier | 2.0 | `--backoff-multiplier 1.5` |
| `--rate-limit` | Delay between requests (ms) | 1000 | `--rate-limit 500` |

### 📊 Retry Pattern Example
```
🔍 Crawling URL: https://content.guardianapis.com/search...
⚠️ Retryable HTTP status 503 for URL: ... (attempt 1/4)
Waiting 1000ms before retry attempt #2
🔁 Retry attempt #2 for URL: ...
⚠️ Retryable network error: connection timeout (attempt 2/4)
Waiting 2000ms before retry attempt #3
🔁 Retry attempt #3 for URL: ...
✅ Successfully crawled URL: ... after 2 retries
```

### 🧵 Thread Monitoring Example
```
✨ Created new thread: robust-crawler-thread-1
🔍 Thread Pool Health Check:
   Active threads: 8/10
   Core pool size: 10
   Completed tasks: 245
   Queue size: 2
   Threads created: 10
   Threads replaced: 0
🚨 CRITICAL: Thread died due to OutOfMemoryError! Thread: robust-crawler-thread-5
⚠️ Thread pool size (9) is below core size (10). Some threads may have died.
🔄 Attempted to restart core threads
✨ Created new thread: robust-crawler-thread-11
📊 Final Thread Pool Stats: {activeThreads=0, completedTasks=156, threadsReplaced=1}
```

## Technical Details

- **API Limit**: Guardian API allows maximum 200 articles per request
- **Default Behavior**: Retrieves maximum available articles (200) by default
- **HTTP Client**: Java 11+ native HTTP client with 30-second request timeout
- **Error Handling**: Comprehensive error handling with detailed logging
- **JSON Formatting**: Beautiful output with proper indentation and alphabetical key ordering
- **Memory Efficient**: Streaming JSON processing for large datasets
- **Thread Safety**: Thread-safe operations with robust concurrent data structures
- **Monitoring**: Real-time thread pool health monitoring with automatic recovery
- **Resilience**: Multi-layered fault tolerance from network to JVM level

## Examples

### Get Recent Sport News
```bash
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--from 2024-12-01 --to 2024-12-31 --section sport"
```

### Get Limited Business News
```bash
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--section business --page-size 25"
```

### Get All News from Specific Day
```bash
mvn exec:java -Dexec.mainClass="com.webcrawler.SimpleCrawler" -Dexec.args="--from 2024-11-15 --to 2024-11-15"
```

### Test Thread Fault Tolerance
```bash
# Run with thread monitoring enabled (shows thread health in logs)
mvn exec:java -Dexec.mainClass="com.webcrawler.CrawlerApp" -Dexec.args="--examples --threads 5"

# Run with enhanced retry and thread monitoring
mvn exec:java -Dexec.mainClass="com.webcrawler.CrawlerApp" -Dexec.args="--examples --threads 10 --max-retries 5"
```

## 🔧 Fault Tolerance Features Summary

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Automatic Retry** | 3 retry attempts with exponential backoff | Handles temporary network/server issues |
| **Thread Replacement** | Dead threads automatically replaced | Maintains pool size during errors |
| **Health Monitoring** | 30-second health checks | Proactive issue detection |
| **Memory Recovery** | OutOfMemoryError handling | Survives memory pressure |
| **Fallback Threads** | Emergency threads for overload | Prevents complete failure |
| **Pool Recovery** | Automatic core thread restart | Restores degraded performance |
| **Smart Retries** | Only retries on retryable errors | Avoids wasted attempts |
| **Exception Logging** | Detailed error tracking | Easy debugging and monitoring |

## Notes

- The Guardian API requires an API key for production use (currently using test key)
- Date ranges with many articles may hit the 200-article limit per request
- For comprehensive coverage of large date ranges, consider breaking them into smaller chunks
- All times are in UTC as provided by The Guardian API
- Thread monitoring logs are available at DEBUG level for detailed diagnostics
- Thread pool statistics are logged at shutdown for performance analysis