package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.Util;

@RequiredArgsConstructor
public class SiteUtilRepository {
    private final JdbcTemplate jdbcTemplate;

    public void clearSiteData(String url) {
        Util.print("run clear " + url + "..");
        int count =
                jdbcTemplate.update("DELETE FROM `index` WHERE page_id in (SELECT p.id FROM page p, site s WHERE p.site_id=s.id and upper(s.url) = ?)", url);
        Util.print("end clear " + url + ": index1 = " + count);
        count = jdbcTemplate.update("DELETE FROM `index` WHERE lemma_id in (SELECT l.id FROM lemma l, site s WHERE l.site_id=s.id and upper(s.url) = ?)", url);
        Util.print("end clear " + url + ": index2 = " + count);
        count = jdbcTemplate.update("DELETE FROM lemma WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)", url);
        Util.print("end clear " + url + ": lemma = " + count);
        count = jdbcTemplate.update("DELETE FROM page WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)", url);
        Util.print("end clear " + url + ": page = " + count);
        count = jdbcTemplate.update("DELETE FROM site WHERE upper(url) = ?", url);
        Util.print("end clear " + url + ": site = " + count);

        count = jdbcTemplate.update("DELETE FROM lemma_tmp WHERE upper(site) = ?", url);
        Util.print("end clear " + url + ": lemma_tmp = " + count);
    }

    public void clearPageData(Page page, String path) {
        String pageUrl = page.getUrl().toUpperCase();
        String siteUrl = page.getSite().getUrl().toUpperCase();

        String sql = "UPDATE lemma l SET frequency = frequency - "
                + "(select count(distinct p.id) from `index` i, page p where i.page_id=p.id and i.lemma_id=l.id and p.path=?) "
                + "WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)";
        jdbcTemplate.update(sql, path, siteUrl);
        jdbcTemplate.update("DELETE FROM `index` WHERE page_id in (SELECT p.id FROM page p, site s WHERE p.site_id=s.id and p.path = ? and upper(s.url)=?)", path, siteUrl);
        jdbcTemplate.update("DELETE FROM lemma_tmp WHERE upper(site)=? and upper(page)=?", siteUrl, pageUrl);
    }

    public void saveStatistic(Site site) {
        String siteUrl = site.getUrl().toUpperCase();

        String sql = "select count(1) from site s, page p where p.site_id=s.id and upper(s.url) = ?";
        int pageCount = jdbcTemplate.queryForObject(sql, Integer.class, siteUrl);
        Util.print(siteUrl + ": count page = " + pageCount);
        site.setPageCount(pageCount);

        sql = "select count(1) from site s, lemma p where p.site_id=s.id and upper(s.url) = ?";
        int lemmaCount = jdbcTemplate.queryForObject(sql, Integer.class, siteUrl);
        Util.print(siteUrl + ": count lemma = " + lemmaCount);
        site.setLemmaCount(lemmaCount);
    }

    public void saveSiteLemmas(Site site) {
        String siteUrl = site.getUrl().toUpperCase();
        String sql = "insert into lemma (site_id,lemma,frequency) "
                + "select s.id, t.lemma, count(distinct t.page) from lemma_tmp t, site s "
                + "where upper(s.url)=upper(t.site) and upper(t.site)=?"
                + "group by s.id, t.lemma";
        int count = jdbcTemplate.update(sql, siteUrl);
        Util.print(siteUrl + ": lemma = " + count);
        sql = "insert into `index` (page_id,lemma_id,`rank`) "
                + "select p.id,l.id, t.frequency from lemma_tmp t, lemma l, page p, site s "
                + "where l.site_id=s.id and p.site_id=s.id and upper(t.site)=upper(s.url) "
                + "and t.lemma=l.lemma and t.page=p.path and upper(s.url)=?";
        count = jdbcTemplate.update(sql, siteUrl);
        Util.print(siteUrl + ": index = " + count);
    }

    public void savePageLemmas(Page page, String path) {
        String siteUrl = page.getSite().getUrl().toUpperCase();
        String sql;

        sql = "insert into lemma (site_id,lemma,frequency) "
                + "select s.id, t.lemma, count(distinct t.page) from lemma_tmp t, site s "
                + "where upper(s.url)=upper(t.site) and upper(t.site)=? and t.page=?"
                + "group by s.id, t.lemma";
        jdbcTemplate.update(sql, siteUrl, path);
        sql = "insert into `index` (page_id,lemma_id,`rank`) "
                + "select p.id,l.id, t.frequency from lemma_tmp t, lemma l, page p, site s "
                + "where l.site_id=s.id and p.site_id=s.id and upper(t.site)=upper(s.url) "
                + "and t.lemma=l.lemma and t.page=p.path and upper(s.url)=? and p.path=?";
        jdbcTemplate.update(sql, siteUrl, path);
    }

}
