package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.HttpRequestFields;
import searchengine.config.Messages;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.SearchData;
import searchengine.dto.statistics.SearchInput;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepositories;
import searchengine.repositories.LemmaRepositories;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final LemmaRepositories lemmaRepositories;
    private final IndexRepositories indexRepositories;
    private final JdbcTemplate jdbcTemplate;
    private final HttpRequestFields httpRequestFields;

    private Map<Page, Float> pageList;
    private List<Site> siteList;

    @Override
    public Map<String, Object> search(SearchInput param) {
        Map<String, Object> response = new HashMap<>();
        response.put("result", false);
        String siteUrls = getSites(param);
        if (siteUrls == null) {
            response.put("error", Messages.SEARCH_NO_INDEXED);
            return response;
        }
        if (param.getQuery().isBlank()) {
            response.put("error", Messages.SEARCH_EMPTY_QUERY);
            return response;
        }
        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = new LemmaFinder();
        } catch (IOException e) {
            response.put("error", Messages.ERROR_LOAD_LEMMATIZATOR);
            return response;
        }
        Map<String, Integer> lemmas = lemmaFinder.getLemmas(param.getQuery());
        if (lemmas.size() == 0) {
            response.put("error", Messages.NOT_LEMMA);
            return response;
        }
        for (String word : lemmas.keySet()) {
            Integer count = jdbcTemplate.queryForObject(
                    "select sum(frequency) from lemma l, site s where l.site_id=s.id and l.lemma='" + word + "' and upper(s.url) in " + siteUrls, Integer.class);
            lemmas.put(word, count == null ? 0 : count);
        }
        pageList = null;
        lemmas.entrySet().stream().
                sorted(Map.Entry.<String, Integer>comparingByValue())
                .forEach(e -> {
                    Map<Page, Float> pages = new HashMap<>();
                    List<Lemma> words = lemmaRepositories.findByLemma(e.getKey());

                    for (Lemma lemma : words) {
                        if (siteList.contains(lemma.getSite())) {
                            List<Index> indexes = indexRepositories.findByLemma(lemma);
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
        Float maxRank = Collections.max(pageList.entrySet(), Map.Entry.comparingByValue()).getValue();
        List<SearchData> result = new ArrayList<>();
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
                    data.setSnippet(getSnippet(page.getContent(), lemmaFinder, lemmas));
                    data.setRelevance(e.getValue() / maxRank);
                    result.add(data);
                });

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

    private String getSnippet(String content, LemmaFinder lemmaFinder, Map<String, Integer> queryLemmas) {
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
}
