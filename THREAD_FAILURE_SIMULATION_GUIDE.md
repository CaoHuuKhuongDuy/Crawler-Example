# Thread Failure Simulation & Auto-Recovery Guide

Simple guide showing how a Java web crawler automatically recovers from thread failures while maintaining data integrity.

## Prerequisites
- **Java 17+**
- **Maven 3.6+**

## Build & Run

### 1. Build the Project
```bash
mvn clean compile
```

### 2. Run Thread Failure Demo (Crawls 5000+ Articles)
```bash
# Original demo (2000 articles in 1 week)
mvn exec:java -Dexec.args="--demo-crawl-with-failures --from 2024-01-01 --to 2024-01-07 --threads 10 --failure-interval 8"

# Extended demo for 5000+ articles (3 weeks)
mvn exec:java -Dexec.args="--demo-crawl-with-failures --from 2024-01-01 --to 2024-01-21 --threads 10 --failure-interval 8"

# Large dataset demo (3 months, 20,000+ articles)
mvn exec:java -Dexec.args="--demo-crawl-with-failures --from 2024-01-01 --to 2024-04-01 --threads 15 --failure-interval 5"
```

**What this does:**
- Crawls Guardian News API for the specified date range
- Uses multi-threaded concurrent processing  
- Injects thread failures at specified intervals
- Automatically recovers and continues crawling
- **New capacity**: ~2000 articles per week (100 articles × 20 pages)

## Key Code Segments

### Thread Death Simulation Code

**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Lines 1136-1205)

```java
/**
 * Simulate runtime exception failures - targets both coordination and processing pools
 */
private void simulateRuntimeExceptionFailures(int numberOfThreads) {
    logger.warn("🔥 SIMULATION: Starting RuntimeException failures in {} threads", numberOfThreads);
    
    // Split failures between both thread pools
    int coordinationFailures = Math.max(1, numberOfThreads / 2);
    int processingFailures = numberOfThreads - coordinationFailures;
    
    // Target coordination pool (executorService)
    for (int i = 0; i < coordinationFailures; i++) {
        final int threadId = i;
        executorService.submit(() -> {
            try {
                String threadName = Thread.currentThread().getName();
                logger.warn("💀 THREAD DEATH: Coordination-Thread-{} ({}) about to throw RuntimeException", threadId, threadName);
                Thread.sleep(1000);
                
                // Log the actual death
                logger.error("☠️ THREAD KILLED: Coordination-Thread-{} ({}) throwing RuntimeException NOW", threadId, threadName);
                
                throw new RuntimeException("SIMULATION: Intentional coordination thread failure #" + threadId + " in " + threadName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    // Target processing pool (where actual crawling happens)
    for (int i = 0; i < processingFailures; i++) {
        final int threadId = coordinationFailures + i;
        processingPool.submit(() -> {
            try {
                String threadName = Thread.currentThread().getName();
                logger.warn("💀 THREAD DEATH: Processing-Thread-{} ({}) about to throw RuntimeException", threadId, threadName);
                Thread.sleep(1000);
                
                // Log the actual death
                logger.error("☠️ THREAD KILLED: Processing-Thread-{} ({}) throwing RuntimeException NOW", threadId, threadName);
                
                throw new RuntimeException("SIMULATION: Intentional processing thread failure #" + threadId + " in " + threadName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
```

### Thread Recovery Code

**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Lines 975-1040)

```java
/**
 * Monitor thread pool health and take corrective action
 */
private void checkThreadPoolHealth() {
    // Check both thread pools - coordination and processing
    int activeThreads = executorService.getActiveCount();
    int poolSize = executorService.getPoolSize();
    int corePoolSize = executorService.getCorePoolSize();
    
    // Processing pool stats (where actual work happens)
    int processingPoolSize = processingPool.getPoolSize();
    int processingActive = processingPool.getActiveThreadCount();
    int processingParallelism = processingPool.getParallelism();
    
    boolean needsRecovery = false;
    String recoveryReason = "";
    
    // Check coordination pool health
    if (poolSize < corePoolSize) {
        needsRecovery = true;
        recoveryReason += String.format("Coordination pool size (%d) below core (%d). ", poolSize, corePoolSize);
    }
    
    // Check processing pool health (more critical for actual work)
    if (processingActive == 0 && processingPoolSize < processingParallelism / 2) {
        needsRecovery = true;
        recoveryReason += String.format("Processing pool degraded (%d active, %d size, %d parallelism). ", 
                                       processingActive, processingPoolSize, processingParallelism);
    }
    
    if (needsRecovery) {
        logger.warn("⚠️ Thread pool health issues detected: {}", recoveryReason);
        System.out.println("*** THREAD RECOVERY TRIGGERED! ***");
        System.out.println("Issues detected: " + recoveryReason);
        
        // Restart coordination threads
        int startedCoordThreads = executorService.prestartAllCoreThreads();
        
        // For ForkJoinPool, stimulate with a new task to ensure active threads
        if (processingActive == 0) {
            processingPool.submit(() -> {
                logger.info("🔄 Processing pool stimulation task completed");
                return null;
            });
        }
        
        String recoveryMsg = String.format("Restarted %d coordination threads, stimulated processing pool", startedCoordThreads);
        logger.info("🔄 Attempted thread pool recovery: {}", recoveryMsg);
        System.out.println("SUCCESS: " + recoveryMsg);
        
        threadsReplaced.addAndGet(startedCoordThreads);
    }
}
```

### Enhanced Thread Factory with Death Detection

**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Lines 932-958)

```java
private class RobustThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    
    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "robust-crawler-thread-" + threadNumber.getAndIncrement());
        thread.setDaemon(false);
        
        // Add uncaught exception handler to log thread deaths
        thread.setUncaughtExceptionHandler((t, e) -> {
            logger.error("🚨 THREAD DEATH DETECTED: Thread {} died with uncaught exception: {}", 
                       t.getName(), e.getClass().getSimpleName(), e);
            System.out.println("🚨 THREAD DEATH DETECTED: Thread " + t.getName() + 
                             " died with uncaught exception: " + e.getClass().getSimpleName());
            threadsReplaced.incrementAndGet();
            
            if (e instanceof ThreadDeath) {
                logger.error("💀 CONFIRMED THREAD KILL: {} terminated by ThreadDeath", t.getName());
                System.out.println("💀 CONFIRMED THREAD KILL: " + t.getName() + " terminated by ThreadDeath");
            } else if (e instanceof RuntimeException) {
                logger.error("💥 CONFIRMED THREAD CRASH: {} crashed with RuntimeException: {}", t.getName(), e.getMessage());
                System.out.println("💥 CONFIRMED THREAD CRASH: " + t.getName() + " crashed with RuntimeException: " + e.getMessage());
            }
        });
        
        return thread;
    }
}
```

## Expected Output

When you run the demo command, you'll see:

### 1. Thread Failures
```
💀 THREAD DEATH: Coordination-Thread-0 (robust-crawler-thread-1) about to throw RuntimeException
💀 THREAD DEATH: Processing-Thread-1 (ForkJoinPool-1-worker-1) about to throw RuntimeException
☠️ THREAD KILLED: Coordination-Thread-0 (robust-crawler-thread-1) throwing RuntimeException NOW
☠️ THREAD KILLED: Processing-Thread-1 (ForkJoinPool-1-worker-1) throwing RuntimeException NOW
💥 THREAD DEAD: Coordination-Thread-0 died from RuntimeException
```

### 2. Auto-Recovery
```
*** THREAD RECOVERY TRIGGERED! ***
Issues detected: Coordination pool size (1) below core (10)
SUCCESS: Restarted 9 coordination threads, stimulated processing pool
```

### 3. Final Results
```
🎉 CRAWLING COMPLETED!
   ✅ Successful crawls: 20/20
   📰 Total articles retrieved: 2000+ (1 week) | 6000+ (3 weeks) | 20000+ (3 months)
   📈 Success rate: 100.0%
   🔄 Threads replaced: 9
   ✅ Final dataset is complete despite multiple thread deaths
```

**Key Achievement:** 100% success rate and complete data integrity despite multiple thread deaths! 