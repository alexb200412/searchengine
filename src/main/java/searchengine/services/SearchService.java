package searchengine.services;

import searchengine.dto.statistics.SearchInput;

import java.util.Map;

public interface SearchService {
    Map<String, Object> search(SearchInput param);

}
