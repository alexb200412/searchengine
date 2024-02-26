package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.Messages;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.SearchData;
import searchengine.dto.statistics.SearchInput;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SearchUtilRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final JdbcTemplate jdbcTemplate;

    private Map<Page, Float> pageList;
    private List<Site> siteList;
    private Map<String, Object> response;
    private LemmaFinder lemmaFinder;

    @Override
    public Map<String, Object> search(SearchInput param) {
        response = new HashMap<>();
        response.put("result", false);

        String siteUrls = getSites(param);
        if (siteUrls == null) {
            response.put("error", Messages.SEARCH_NO_INDEXED);
            return response;
        }
        try {
            lemmaFinder = new LemmaFinder();
        } catch (IOException e) {
            response.put("error", Messages.ERROR_LOAD_LEMMATIZATOR);
            return null;
        }
        Map<String, Integer> queryLemmas = getQueryLemmas(param.getQuery(), siteUrls);
        if (queryLemmas == null) {
            return response;
        }
        setPageList(queryLemmas);

        List<SearchData> result = getResultList(param, queryLemmas);

        response.put("result", true);
        response.put("count", pageList.size());
        response.put("data", result);

        return response;
    }

    private String getSites(SearchInput param) {
        String result = null;
        siteList = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();
        for (int i = 0; i < sitesList.size(); i++) {
            Site site = getSite(param, sitesList.get(i).getUrl().toUpperCase());
            if (site == null) continue;
            siteList.add(site);
            result = (result == null ? "(" : result + ",") + "'" + site.getUrl().toUpperCase() + "'";
        }
        result = result == null ? null : result + ")";
        return result;
    }

    private Site getSite(SearchInput param, String url) {
        if (!(param.getSite() == null) && (!param.getSite().toUpperCase().equals(url))) {
            return null;
        }
        Site site = siteRepository.findFirstByUrlIgnoreCase(url);
        if (site == null) return null;
        if (site.getLemmaCount() < 1) return null;
        return site;
    }

    private Map<String, Integer> getQueryLemmas(String query, String siteUrls) {
        if (query.isBlank()) {
            response.put("error", Messages.SEARCH_EMPTY_QUERY);
            return null;
        }
        Map<String, Integer> lemmas = lemmaFinder.getLemmas(query);
        if (lemmas.size() == 0) {
            response.put("error", Messages.NOT_LEMMA);
            return null;
        }
        for (String word : lemmas.keySet()) {
            SearchUtilRepository searchUtilRepository = new SearchUtilRepository(jdbcTemplate);
            Integer count = searchUtilRepository.getLemmasCount(word, siteUrls);
            lemmas.put(word, count == null ? 0 : count);
        }
        return lemmas;
    }

    private void setPageList(Map<String, Integer> lemmas) {
        pageList = null;
        lemmas.entrySet().stream().
                sorted(Map.Entry.<String, Integer>comparingByValue())
                .forEach(e -> {
                    Map<Page, Float> pages = new HashMap<>();
                    List<Lemma> words = lemmaRepository.findByLemma(e.getKey());

                    for (Lemma lemma : words) {
                        if (siteList.contains(lemma.getSite())) {
                            List<Index> indexes = indexRepository.findByLemma(lemma);
                            indexes.stream().forEach(index -> {
                                Float rank = pages.get(index.getPage());
                                pages.put(index.getPage(), (rank == null ? 0F : rank) + index.getRank());
                            });
                        }
                    }
                    if (pageList == null) {
                        pageList = new HashMap<>(pages);
                    } else if (pageList.size() > 0) {
                        pageList.keySet().retainAll(pages.keySet());
                    }
                });
    }

    private String getSnippet(String content, Map<String, Integer> queryLemmas) {
        String result = "";
        List<String> words = new ArrayList<>(Arrays.asList(content.split("\\s")));
        int prevIndex = -10;
        int countWords = 0;
        for (int i = 0; i < words.size(); i++) {
            if (i <= prevIndex) continue;
            String word = words.get(i);
            Set<String> lemmas = lemmaFinder.getLemmas(word.toLowerCase()).keySet();
            lemmas.retainAll(queryLemmas.keySet());
            if (lemmas.size() < 1) continue;

            if (!result.isEmpty()) result += '\n';
            int firstIndex = -3;
            int lastIndex = 3;
            if (firstIndex + i < 0) {
                int num = Math.abs(firstIndex + i);
                firstIndex += num;
                lastIndex += num;
            }
            if (lastIndex + i >= words.size()) {
                lastIndex = words.size() - i - 1;
            }
            for (int ii = firstIndex; ii <= lastIndex; ii++) {
                String wrd = words.get(i + ii);
                if (ii == 0) {
                    wrd = "<b>" + wrd + "</b>";
                    countWords++;

                }
                result += wrd + " ";
            }
            if (countWords > 3) break;
            prevIndex = i + lastIndex;
        }
        return result.trim();
    }

    private List<SearchData> getResultList(SearchInput param, Map<String, Integer> queryLemmas) {
        List<SearchData> result = new ArrayList<>();
        Float maxRank = Collections.max(pageList.entrySet(), Map.Entry.comparingByValue()).getValue();
        pageList.entrySet().stream().
                sorted(Map.Entry.<Page, Float>comparingByValue().reversed())
                .limit(param.getLimit())
                .forEach(e -> {
                    Page page = e.getKey();
                    SearchData data = new SearchData();
                    data.setSite(page.getSite().getUrl());
                    data.setSiteName(page.getSite().getName());
                    data.setUri(page.getPath());
                    data.setTitle(page.getTitle());
                    data.setSnippet(getSnippet(page.getContent(), queryLemmas));
                    data.setRelevance(e.getValue() / maxRank);
                    result.add(data);
                });
        return result;
    }
}
