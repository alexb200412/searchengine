package searchengine.services;

import searchengine.dto.statistics.StatisticsResponse;

import java.util.Map;

public interface StatisticsService {
    StatisticsResponse getStatistics();

    Map<String, Object> startIndexing();

    Map<String, Object> stopIndexing();
}
