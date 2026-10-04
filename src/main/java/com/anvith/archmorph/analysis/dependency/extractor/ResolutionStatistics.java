package com.anvith.archmorph.analysis.dependency.extractor;

import com.anvith.archmorph.analysis.dependency.resolve.ResolvedType;

import java.util.EnumMap;
import java.util.Map;

/** Counts how type references were resolved, for transparency in reports. */
public class ResolutionStatistics {

    private final Map<ResolvedType.Resolution, Integer> counts = new EnumMap<>(ResolvedType.Resolution.class);
    private int symbolSolverHits;
    private int symbolSolverMisses;

    public synchronized void record(ResolvedType resolved) {
        if (resolved != null) {
            counts.merge(resolved.resolution(), 1, Integer::sum);
        }
    }

    public synchronized void symbolSolverHit() {
        symbolSolverHits++;
    }

    public synchronized void symbolSolverMiss() {
        symbolSolverMisses++;
    }

    public synchronized Map<ResolvedType.Resolution, Integer> getCounts() {
        return Map.copyOf(counts);
    }

    public int getSymbolSolverHits() {
        return symbolSolverHits;
    }

    public int getSymbolSolverMisses() {
        return symbolSolverMisses;
    }

    public synchronized int getLowConfidenceResolutions() {
        return counts.getOrDefault(ResolvedType.Resolution.SIMPLE_NAME_FALLBACK, 0);
    }
}
