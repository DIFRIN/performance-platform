package com.performance.platform.injection.gatling.worker;

import com.performance.platform.domain.injection.GeneratorHealth;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.time.Duration;

/**
 * Mesure la sante du worker (ADR-068) : charge CPU moyenne du processus sur la duree de la sonde,
 * somme des temps de collecte GC, pic de heap.
 */
public final class GeneratorHealthProbe {

    private final long startNanos;
    private final long startCpuNanos;
    private final long startGcMs;

    private GeneratorHealthProbe() {
        this.startNanos = System.nanoTime();
        this.startCpuNanos = processCpuNanos();
        this.startGcMs = gcMillis();
        resetHeapPeaks();
    }

    public static GeneratorHealthProbe start() {
        return new GeneratorHealthProbe();
    }

    /** Snapshot : CPU moyen (cores utilises) depuis {@link #start()}, GC cumule, pic de heap. */
    public GeneratorHealth stop() {
        long wallNanos = Math.max(1, System.nanoTime() - startNanos);
        long cpuNanos = processCpuNanos();
        double cpu = (startCpuNanos < 0 || cpuNanos < 0) ? 0.0
                : Math.max(0.0, (double) (cpuNanos - startCpuNanos) / wallNanos);
        long gc = Math.max(0, gcMillis() - startGcMs);
        return new GeneratorHealth(cpu, Duration.ofMillis(gc), peakHeapBytes());
    }

    private static long processCpuNanos() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuTime();
        }
        return -1;
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long t = gc.getCollectionTime();
            if (t > 0) total += t;
        }
        return total;
    }

    private static void resetHeapPeaks() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) pool.resetPeakUsage();
        }
    }

    private static long peakHeapBytes() {
        long total = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null) {
                total += pool.getPeakUsage().getUsed();
            }
        }
        return total;
    }
}
