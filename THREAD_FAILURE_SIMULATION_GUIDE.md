# Thread Failure Simulation Guide

A simple step-by-step guide to test the Guardian News Crawler in **Normal Mode** vs **Thread Failure Mode** to demonstrate automatic thread recovery.

## Prerequisites
- **Java 17+**
- **Maven 3.6+**

---

## Step 1: Build the Project

```bash
mvn clean compile
```

---

## Step 2: Run Normal Mode (~1000 Articles)

**What it does:** Crawls Guardian News API without any thread failures.

```bash
mvn exec:java -Dexec.args="--examples --from 2024-01-01 --to 2024-01-31 --page-size 1000 --threads 10"
```

**Expected Result:**
- ✅ All 5 URL requests successful (5/5)
- ✅ All 1000 articles retrieved
- ✅ No thread deaths or recovery events
- ✅ Pool size stays healthy: 10/10 threads

---

## Step 3: Run Thread Failure Mode (~1000 Articles)

**What it does:** Same crawling but **deliberately kills threads** every 5 seconds to test recovery.

```bash
mvn exec:java -Dexec.args="--demo-crawl-with-failures --from 2024-01-01 --to 2024-01-31 --page-size 1000 --threads 10 --failure-interval 5 --failure-threads 2"
```

**What happens:**
- 🔥 Every 5 seconds: 2 threads are intentionally killed
- 🚨 System detects thread death
- 🔄 Automatic recovery creates replacement threads
- ✅ Crawling continues despite failures

---

## Step 4: Expected Results Comparison

### Normal Mode Output:
```
🎯 Enhanced batch crawl completed: 5/5 URLs successful
📄 Retrieved 1000 articles out of 1000 total available
✅ Thread Pool Healthy: 10/10 threads
📊 Final Thread Pool Stats: {poolSize=10, threadsReplaced=0}
```

### Thread Failure Mode Output:
```
💀 THREAD FAILURE SIMULATION: Killing 2 threads...
💀 SIMULATED THREAD DEATH: robust-crawler-thread-3 - SIMULATION: Intentional thread failure
🚨 THREAD POOL DEGRADED: Pool size (8) below core (10). RECOVERY STARTING!
🔄 RECOVERY COMPLETED: Restarted 2 threads. Pool now: 10/10
✅ RECOVERY: Created 2 replacement threads

🎯 Enhanced batch crawl completed: 5/5 URLs successful
📄 Retrieved 1000 articles out of 1000 total available
📊 Final Thread Pool Stats: {poolSize=10, threadsReplaced=4}
```

**Key Difference:** 
- **Normal Mode:** `threadsReplaced=0` (no failures)
- **Failure Mode:** `threadsReplaced=4` (threads died and were replaced)
- **Same Result:** Both get all 1000 articles successfully!

---

## Step 5: Key Code Segments

### A. Thread Failure Simulation Code
**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Line ~950)

```java
/**
 * Simulate runtime exception failures - targets both thread pools
 */
private void simulateRuntimeExceptionFailures(int numberOfThreads) {
    logger.warn("🔥 SIMULATION: Starting RuntimeException failures in {} threads", numberOfThreads);
    
    // Kill coordination pool threads
    for (int i = 0; i < coordinationFailures; i++) {
        executorService.submit(() -> {
            String threadName = Thread.currentThread().getName();
            logger.error("💀 COORDINATION THREAD DEATH: {} is being killed NOW!", threadName);
            throw new RuntimeException("SIMULATION: Intentional thread failure");
        });
    }
    
    // Kill processing pool threads  
    for (int i = 0; i < processingFailures; i++) {
        processingPool.submit(() -> {
            String threadName = Thread.currentThread().getName();
            logger.error("💀 PROCESSING THREAD DEATH: {} is being killed NOW!", threadName);
            throw new RuntimeException("SIMULATION: Intentional thread failure");
        });
    }
}
```

### B. Thread Recovery Code
**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Line ~1134)

```java
/**
 * Monitor thread pool health and automatically recover
 */
private void checkThreadPoolHealth() {
    int poolSize = executorService.getPoolSize();
    int corePoolSize = executorService.getCorePoolSize();
    
    // Detect thread pool degradation
    if (poolSize < corePoolSize) {
        logger.error("🚨 THREAD POOL DEGRADED: Pool size ({}) below core ({}). RECOVERY STARTING!", 
                   poolSize, corePoolSize);
        
        // Automatically create replacement threads
        int newThreads = executorService.prestartAllCoreThreads();
        threadsReplaced.addAndGet(newThreads);
        
        logger.warn("🔄 RECOVERY COMPLETED: Restarted {} threads. Pool now: {}/{}", 
                   newThreads, executorService.getPoolSize(), corePoolSize);
    }
}
```

### C. Thread Death Detection
**Location:** `src/main/java/com/webcrawler/core/ApiCrawler.java` (Line ~1075)

```java
/**
 * Custom thread factory that detects when threads die
 */
private class RobustThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                if (e.getMessage().contains("SIMULATION: Intentional")) {
                    logger.error("💀 SIMULATED THREAD DEATH: {} - {}", threadName, e.getMessage());
                    System.out.println("💀 THREAD DIED: " + threadName);
                }
                threadsReplaced.incrementAndGet();
                throw e;
            }
        }, "robust-crawler-thread-" + threadNumber.getAndIncrement());
        
        return thread;
    }
}
```

---

## Summary

**What This Demonstrates:**
1. **Normal Mode:** Shows baseline performance without failures
2. **Failure Mode:** Shows the system can handle thread deaths gracefully
3. **Auto-Recovery:** Threads are automatically replaced when they die
4. **Data Integrity:** Both modes retrieve the same 1000 articles successfully

**The Key Achievement:** 
🎯 **100% success rate in both modes** - proving the crawler is fault-tolerant and maintains data integrity even when threads die during operation. 