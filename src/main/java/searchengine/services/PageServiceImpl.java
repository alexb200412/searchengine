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
import searchengine.repositories.LemmaTmpRepositories;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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
    private final LemmaTmpRepositories lemmaTmpRepositories;
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
        String queryUrl = site.getUrl().toUpperCase();
        String sql = "UPDATE lemma l SET frequency = frequency - "
                + "(select count(distinct p.id) from `index` i, page p where i.page_id=p.id and i.lemma_id=l.id and p.path=?) "
                + "WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)";
        jdbcTemplate.update(sql, path, queryUrl);
        jdbcTemplate.update("DELETE FROM `index` WHERE page_id in (SELECT p.id FROM page p, site s WHERE p.site_id=s.id and p.path = ? and upper(s.url)=?)", path, queryUrl);
        jdbcTemplate.update("DELETE FROM lemma_tmp WHERE upper(site)=? and upper(page)=?", queryUrl, url.toUpperCase());

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
        pageParams.setLemmaTmpRepositories(lemmaTmpRepositories);
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

        queryUrl = site.getUrl().toUpperCase();
        sql = "insert into lemma (site_id,lemma,frequency) "
                + "select s.id, t.lemma, count(distinct t.page) from lemma_tmp t, site s "
                + "where upper(s.url)=upper(t.site) and upper(t.site)=? and t.page=?"
                + "group by s.id, t.lemma";
        jdbcTemplate.update(sql, queryUrl, path);
        sql = "insert into `index` (page_id,lemma_id,`rank`) "
                + "select p.id,l.id, t.frequency from lemma_tmp t, lemma l, page p, site s "
                + "where l.site_id=s.id and p.site_id=s.id and upper(t.site)=upper(s.url) "
                + "and t.lemma=l.lemma and t.page=p.path and upper(s.url)=? and p.path=?";
        jdbcTemplate.update(sql, queryUrl, path);

        response.put("result", true);
        return response;
    }
}
