package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import searchengine.config.HttpRequestFields;
import searchengine.config.Messages;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.LemmaTmpRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.SiteUtilRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PageServiceImpl implements PageService {
    private final SitesList sites;
    private final HttpRequestFields httpRequestFields;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaTmpRepository lemmaTmpRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> indexPage(String url) {
        Map<String, Object> response = new HashMap<>();
        String domainName = Util.getDomainName(url);
        Site site = null;
        for (SiteConfig siteConfig : sites.getSites()) {
            if (Util.getDomainName(siteConfig.getUrl()).equals(domainName)) {
                site = siteRepository.findFirstByUrlIgnoreCase(siteConfig.getUrl());
                if (site == null) {
                    site = Util.iniSite(siteConfig);
                    siteRepository.saveAndFlush(site);
                }
                ;
            }
        }
        if (site == null) {
            response.put("result", false);
            response.put("error", Messages.NOT_CONTAIN_PAGE);
            return response;
        }
        String path = Util.getPath(url, domainName);
        List<Page> pages = pageRepository.findByPath(path);
        Page page = null;
        for (Page findPage : pages) {
            if (findPage.getSite() == site) {
                page = findPage;
            }
        }

        if (page == null) {
            page = Util.iniPage(site, url);
        } else {
            page.setUrl(url);
        }

        SiteUtilRepository siteUtilRepository = new SiteUtilRepository(jdbcTemplate);
        siteUtilRepository.clearPageData(page, path);

        LemmaFinder lemmaFinder;
        try {
            lemmaFinder = new LemmaFinder();
        } catch (IOException e) {
            response.put("result", false);
            response.put("error", Messages.ERROR_LOAD_LEMMATIZATOR);
            return response;
        }

        PageParserParams pageParams = new PageParserParams();
        pageParams.setPageRepository(pageRepository);
        pageParams.setLemmaTmpRepository(lemmaTmpRepository);
        pageParams.setPage(page);
        pageParams.setPageList(new HashSet<>());
        pageParams.setHttpRequestFields(httpRequestFields);
        pageParams.setLemmaFinder(lemmaFinder);

        PageParser pageParser = new PageParser(pageParams);
        if (!pageParser.process(1)) {
            response.put("result", false);
            response.put("error", Messages.UNAVAILABLE_PAGE);
            return response;
        }

        siteUtilRepository.savePageLemmas(page, path);

        response.put("result", true);
        return response;
    }
}
