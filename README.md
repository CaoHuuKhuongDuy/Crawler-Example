# Guardian News Crawler

A focused web crawler specifically designed to fetch news articles from The Guardian API with flexible filtering options.

## Features

- **Guardian API Integration**: Direct access to The Guardian's comprehensive news database
- **Date Range Filtering**: Crawl news from specific time periods using `--from` and `--to` parameters
- **Section Filtering**: Filter articles by Guardian sections (sport, business, world, politics, etc.)
- **Page Size Control**: Control the number of articles retrieved (1-200, default: 200 for maximum coverage)
- **Rich Content**: Retrieves headlines, bylines, full article body, thumbnails, and metadata
- **Beautiful JSON Output**: Clean, formatted JSON files with proper indentation and alphabetical key ordering
- **Asynchronous Processing**: Fast, concurrent crawling for optimal performance

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

## Technical Details

- **API Limit**: Guardian API allows maximum 200 articles per request
- **Default Behavior**: Retrieves maximum available articles (200) by default
- **Concurrent Processing**: Uses asynchronous HTTP requests for better performance
- **Error Handling**: Comprehensive error handling with detailed logging
- **JSON Formatting**: Beautiful output with proper indentation and alphabetical key ordering

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

## Notes

- The Guardian API requires an API key for production use (currently using test key)
- Date ranges with many articles may hit the 200-article limit per request
- For comprehensive coverage of large date ranges, consider breaking them into smaller chunks
- All times are in UTC as provided by The Guardian API 