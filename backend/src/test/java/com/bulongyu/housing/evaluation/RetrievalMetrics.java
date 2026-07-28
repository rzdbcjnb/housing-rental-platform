package com.bulongyu.housing.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

final class RetrievalMetrics {
    private static final List<Integer> RECALL_K = List.of(10, 20, 50);
    private static final List<Integer> PRECISION_K = List.of(5, 10);
    private static final List<Integer> NDCG_K = List.of(10, 20);

    private RetrievalMetrics() {
    }

    static Report evaluate(List<Observation> observations) {
        List<Observation> resultQueries = observations.stream()
                .filter(observation -> "RESULTS".equals(observation.expectedOutcome()))
                .toList();
        BinaryMetrics lenient = binary(resultQueries, grade -> grade >= 1);
        BinaryMetrics strict = binary(resultQueries, grade -> grade == 2);

        Map<Integer, Double> ndcg = new LinkedHashMap<>();
        for (int k : NDCG_K) {
            ndcg.put(k, average(resultQueries, observation -> ndcgAt(observation, k)));
        }

        List<Observation> emptyQueries = observations.stream()
                .filter(observation -> "EMPTY".equals(observation.expectedOutcome()))
                .toList();
        long correctEmpty = emptyQueries.stream().filter(observation -> observation.rankedIds().isEmpty()).count();
        long zeroHitFalsePositives = emptyQueries.stream().mapToLong(observation -> observation.rankedIds().size()).sum();
        long totalReturned = observations.stream().mapToLong(observation -> observation.rankedIds().size()).sum();
        long hardViolations = observations.stream().mapToLong(Observation::hardViolationCount).sum();
        long vectorActive = observations.stream().filter(Observation::vectorActive).count();
        List<Long> latencies = observations.stream().map(Observation::latencyMs).sorted().toList();

        return new Report(
                observations.size(),
                resultQueries.size(),
                emptyQueries.size(),
                lenient,
                strict,
                Map.copyOf(ndcg),
                ratio(correctEmpty, emptyQueries.size()),
                zeroHitFalsePositives,
                ratio(hardViolations, totalReturned),
                hardViolations,
                totalReturned,
                ratio(vectorActive, observations.size()),
                averageLatency(latencies),
                percentile(latencies, 0.95),
                observations.stream().mapToInt(observation -> observation.rankedIds().size()).max().orElse(0));
    }

    private static BinaryMetrics binary(List<Observation> observations, IntPredicate relevantGrade) {
        Map<Integer, Double> recall = new LinkedHashMap<>();
        for (int k : RECALL_K) {
            recall.put(k, average(observations, observation -> recallAt(observation, k, relevantGrade)));
        }
        Map<Integer, Double> precision = new LinkedHashMap<>();
        for (int k : PRECISION_K) {
            precision.put(k, average(observations, observation -> precisionAt(observation, k, relevantGrade)));
        }
        double mrr = average(observations, observation -> reciprocalRank(observation, relevantGrade));
        return new BinaryMetrics(Map.copyOf(recall), Map.copyOf(precision), mrr);
    }

    private static double recallAt(Observation observation, int k, IntPredicate relevantGrade) {
        Set<Long> relevant = relevantIds(observation, relevantGrade);
        if (relevant.isEmpty()) {
            return 0;
        }
        long hits = observation.rankedIds().stream().limit(k).filter(relevant::contains).count();
        return ratio(hits, relevant.size());
    }

    private static double precisionAt(Observation observation, int k, IntPredicate relevantGrade) {
        Set<Long> relevant = relevantIds(observation, relevantGrade);
        long hits = observation.rankedIds().stream().limit(k).filter(relevant::contains).count();
        return ratio(hits, k);
    }

    private static double reciprocalRank(Observation observation, IntPredicate relevantGrade) {
        Set<Long> relevant = relevantIds(observation, relevantGrade);
        for (int index = 0; index < observation.rankedIds().size(); index++) {
            if (relevant.contains(observation.rankedIds().get(index))) {
                return 1.0 / (index + 1);
            }
        }
        return 0;
    }

    private static Set<Long> relevantIds(Observation observation, IntPredicate relevantGrade) {
        return observation.judgments().entrySet().stream()
                .filter(entry -> relevantGrade.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static double ndcgAt(Observation observation, int k) {
        double dcg = 0;
        for (int index = 0; index < Math.min(k, observation.rankedIds().size()); index++) {
            int grade = observation.judgments().getOrDefault(observation.rankedIds().get(index), 0);
            dcg += gain(grade) / log2(index + 2);
        }
        List<Integer> idealGrades = new ArrayList<>(observation.judgments().values());
        idealGrades.sort(java.util.Comparator.reverseOrder());
        double idealDcg = 0;
        for (int index = 0; index < Math.min(k, idealGrades.size()); index++) {
            idealDcg += gain(idealGrades.get(index)) / log2(index + 2);
        }
        return idealDcg == 0 ? 0 : dcg / idealDcg;
    }

    private static double gain(int grade) {
        return Math.pow(2, grade) - 1;
    }

    private static double log2(int value) {
        return Math.log(value) / Math.log(2);
    }

    private static double average(List<Observation> observations, Score score) {
        return observations.stream().mapToDouble(score::value).average().orElse(0);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static double averageLatency(List<Long> latencies) {
        return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    @FunctionalInterface
    private interface Score {
        double value(Observation observation);
    }

    record Observation(String queryId,
                       String expectedOutcome,
                       List<Long> rankedIds,
                       Map<Long, Integer> judgments,
                       boolean vectorActive,
                       long latencyMs,
                       int hardViolationCount) {
        Observation {
            rankedIds = List.copyOf(rankedIds);
            judgments = Map.copyOf(judgments);
        }
    }

    record BinaryMetrics(Map<Integer, Double> recallAt,
                         Map<Integer, Double> precisionAt,
                         double mrr) {
    }

    record Report(int executedQueries,
                  int resultQueries,
                  int emptyQueries,
                  BinaryMetrics lenient,
                  BinaryMetrics strict,
                  Map<Integer, Double> ndcgAt,
                  double zeroHitAccuracy,
                  long zeroHitFalsePositiveCount,
                  double hardConstraintViolationRate,
                  long hardConstraintViolationCount,
                  long returnedHouseCount,
                  double vectorActiveRate,
                  double averageLatencyMs,
                  long p95LatencyMs,
                  int maximumReturnedCount) {
    }
}
