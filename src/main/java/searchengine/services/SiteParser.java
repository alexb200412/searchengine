package searchengine.services;

import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.config.Messages;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class SiteParser implements Runnable {
    private final SiteParserParams params;
    private final Page page;

    private final Set<Page> pageList;
    private LemmaFinder lemmaFinder;
    private boolean isActive;
    public String domain;

    public SiteParserParams getParams() {
        return params;
    }

    public SiteParser(SiteParserParams params) {
        this.params = params;
        this.pageList = new HashSet<>();
        this.isActive = true;
        page = initSiteData();
        this.domain = Util.getDomainName(page.getSite().getUrl());
    }

    public Set<Page> getPageList() {
        return pageList;
    }


    public LemmaFinder getLemmaFinder() {
        return lemmaFinder;
    }

    public PageRepository getPageRepository() {
        return params.getPageRepository();
    }

    public String getDomain() {
        return domain;
    }

    public boolean isActive() {
        return isActive;
    }

    public void disable() {
        isActive = false;
    }

    public void run() {
        if (!isActive()) return;

        clearData();

        Site site = page.getSite();
        params.getSiteRepository().saveAndFlush(site);
        Util.print("create site " + site.getUrl());
        pageList.add(page);

        SiteParserNode proc = new SiteParserNode(page, this);

        try {
            lemmaFinder = new LemmaFinder();
        } catch (IOException e) {
            site.setStatus(Status.FAILED);
            site.setLastError(Messages.ERROR_LOAD_LEMMATIZATOR);
            return;
        }
        SiteRepository siteRepository = params.getSiteRepository();

        ForkJoinPool pool = new ForkJoinPool();
        pool.execute(proc);
        do {
            LocalDateTime time = LocalDateTime.now();
            site.setStatusTime(time);
            siteRepository.saveAndFlush(site);

            System.out.printf("*********" + Util.getTimeStr(time) + "**************\n");
            System.out.printf("URL: %s\n", page.getSite().getName());
            System.out.printf("Main: Parallelism: %d\n", pool.getParallelism());
            System.out.printf("Main: Active Threads: %d\n",
                    pool.getActiveThreadCount());
            System.out.printf("Main: Task Count: %d\n", pool.getQueuedTaskCount());
            System.out.printf("Main: Steal Count: %d\n", pool.getStealCount());
            System.out.printf("Main: Page Count: %d\n", pageList.size());
            System.out.printf("******************************************\n");
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (!proc.isDone());
        Util.print("END node " + site.getName());
        pool.shutdown();
        proc.join();
        Util.print("END node join " + site.getName());

        if (!isActive()) return;

        saveLemmas(site);
        Util.print("END lemma " + site.getName());

        if (!isActive()) return;

        saveStatistic(site);
        Util.print("END statistic " + site.getName());

        if (page.getContent() == null || !isActive()) {
            site.setStatus(Status.FAILED);
            site.setLastError(isActive() ? Messages.UNAVAILABLE_MAIN_PAGE : Messages.INDEXING_ABORTED);
        } else {
            site.setStatus(Status.INDEXED);
            site.setLastError("");
        }
        site.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(site);
    }


    private Page initSiteData() {
        Site site = Util.iniSite(params.getSiteConfig());

        Page parentPage = new Page();
        parentPage.setSite(site);
        parentPage.setPath("/");
        parentPage.setUrl(site.getUrl());

        return parentPage;
    }

    private void clearData() {
        String url = params.getSiteConfig().getUrl().toUpperCase();
        Util.print("run clear " + url + "..");
        int count =
                params.getJdbcTemplate().update("DELETE FROM `index` WHERE page_id in (SELECT p.id FROM page p, site s WHERE p.site_id=s.id and upper(s.url) = ?)", url);
        Util.print("end clear " + url + ": index1 = " + count);
        count = params.getJdbcTemplate().update("DELETE FROM `index` WHERE lemma_id in (SELECT l.id FROM lemma l, site s WHERE l.site_id=s.id and upper(s.url) = ?)", url);
        Util.print("end clear " + url + ": index2 = " + count);
        count = params.getJdbcTemplate().update("DELETE FROM lemma WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)", url);
        Util.print("end clear " + url + ": lemma = " + count);
        count = params.getJdbcTemplate().update("DELETE FROM page WHERE site_id in (SELECT id FROM site WHERE upper(url) = ?)", url);
        Util.print("end clear " + url + ": page = " + count);
        count = params.getJdbcTemplate().update("DELETE FROM site WHERE upper(url) = ?", url);
        Util.print("end clear " + url + ": site = " + count);

        count = params.getJdbcTemplate().update("DELETE FROM lemma_tmp WHERE upper(site) = ?", url);
        Util.print("end clear " + url + ": lemma_tmp = " + count);
    }

    private void saveStatistic(Site site) {
        JdbcTemplate jdbcTemplate = params.getJdbcTemplate();
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

    private void saveLemmas(Site site) {
        JdbcTemplate jdbcTemplate = params.getJdbcTemplate();

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
}
