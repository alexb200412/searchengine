package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.HttpRequestFields;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaTmpRepositories;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private final HttpRequestFields httpRequestFields;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ArrayList<SiteParser> siteParsers = new ArrayList<>();
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final JdbcTemplate jdbcTemplate;
    private final LemmaTmpRepositories lemmaTmpRepositories;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        boolean indexed = false;

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();

        for (int i = 0; i < sitesList.size(); i++) {
            SiteConfig siteConfig = sitesList.get(i);

            DetailedStatisticsItem item = getStatistic(siteConfig);

            total.setPages(total.getPages() + item.getPages());
            total.setLemmas(total.getLemmas() + item.getLemmas());
            if (item.getStatus() == Status.INDEXED.toString()) indexed = true;
            detailed.add(item);
        }
        total.setIndexing(indexed);

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private DetailedStatisticsItem getStatistic(SiteConfig siteConfig) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();

        item.setUrl(siteConfig.getUrl());
        item.setName(siteConfig.getName());
        item.setError("");

        Site site = siteRepository.findFirstByUrlIgnoreCase(item.getUrl());
        if (site == null) return item;

        item.setPages(site.getPageCount());
        item.setLemmas(site.getLemmaCount());
        item.setStatus(site.getStatus().toString());
        item.setError(site.getLastError() == null ? "" : site.getLastError());
        item.setStatusTime(ZonedDateTime.of(site.getStatusTime(), ZoneId.systemDefault()).toInstant().toEpochMilli());
        return item;
    }

    @Override
    public Map<String, Object> startIndexing() {
        Map<String, Object> response = new HashMap<>();

        if (!(executorService.isTerminated() || executorService.isShutdown())) {
            clearIndexingList();
        }
        if (siteParsers.size() > 0) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return response;
        }

        sites.getSites().forEach(siteConfig -> {
            SiteParserParams params = new SiteParserParams();
            params.setSiteConfig(siteConfig);
            params.setSiteRepository(siteRepository);
            params.setPageRepository(pageRepository);
            params.setLemmaTmpRepositories(lemmaTmpRepositories);
            params.setJdbcTemplate(jdbcTemplate);
            params.setHttpRequestFields(httpRequestFields);
            SiteParser parseUrl = new SiteParser(params);

            executorService.execute(parseUrl);
            siteParsers.add(parseUrl);
        });
        response.put("result", true);
        return response;
    }

    @Override
    public Map<String, Object> stopIndexing() {
        Map<String, Object> response = new HashMap<>();
        siteParsers.forEach(SiteParser::disable);

        clearIndexingList();
        response.put("result", true);
        return response;
    }

    private void clearIndexingList() {
        siteParsers.clear();
    }
}
