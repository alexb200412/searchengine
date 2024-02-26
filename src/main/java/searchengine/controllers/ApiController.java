package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.SearchInput;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.PageService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final PageService pageService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, PageService pageService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.pageService = pageService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public Map<String, Object> startIndexing() {
        return statisticsService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public Map<String, Object> stopIndexing() {
        return statisticsService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public Map<String, Object> indexPage(@RequestParam String url) {
        return pageService.indexPage(url);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(SearchInput param) {
        Map<String, Object> result = searchService.search(param);
        return new ResponseEntity<Map<String, Object>>(result, HttpStatus.OK);
    }
}
