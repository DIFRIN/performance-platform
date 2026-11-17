package com.performance.platform.injection.gatling.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.performance.platform.domain.injection.GeneratorHealth;
import org.junit.jupiter.api.Test;

class GeneratorHealthProbeTest {

    @Test
    void measuresCpuAndHeapUnderLoad() {
        var probe = GeneratorHealthProbe.start();
        long x = 0;
        long end = System.nanoTime() + 100_000_000L;
        byte[][] keep = new byte[8][];
        int i = 0;
        while (System.nanoTime() < end) {
            x += i++;
            keep[i % 8] = new byte[64 * 1024];
        }
        GeneratorHealth h = probe.stop();
        assertTrue(x != 1);
        assertTrue(h.cpuLoad() > 0.0, "cpu " + h.cpuLoad());
        assertTrue(h.peakHeapBytes() > 0);
        assertTrue(!h.gcPause().isNegative());
    }

    @Test
    void healthSurvivesResultJsonRoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        var dir = new RunDirectory(tmp);
        var health = new GeneratorHealth(0.5, java.time.Duration.ofMillis(12), 4096);
        dir.writeResult(new WorkerOutcome(0, true, false, null, dir.resultFile(), "", health));
        assertEquals(health, dir.readResult().generatorHealth());
    }
}
