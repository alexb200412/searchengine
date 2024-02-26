package searchengine.services;

import searchengine.config.Messages;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.SiteUtilRepository;

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

        SiteUtilRepository siteUtilRepository = new SiteUtilRepository(params.getJdbcTemplate());

        siteUtilRepository.clearSiteData(params.getSiteConfig().getUrl().toUpperCase());

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

        siteUtilRepository.saveSiteLemmas(site);
        Util.print("END lemma " + site.getName());

        if (!isActive()) return;

        siteUtilRepository.saveStatistic(site);
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

}
