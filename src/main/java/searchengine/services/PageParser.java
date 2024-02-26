package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import searchengine.config.HttpRequestFields;
import searchengine.model.LemmaTmp;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PageParser {
    private final PageParserParams params;

    public PageParser(PageParserParams params) {
        this.params = params;
    }

    public boolean process(int launchType) {
        /* launchType: 0: только проверка наличия страницы; 1: анализ одной страницы; 2: анализ всего сайта
         * */
        Connection.Response response;
        try {
            Thread.sleep(150); // чтобы сайт не заблокировал
        } catch (InterruptedException e) {
            return false;
        }
        Page page = params.getPage();

        Elements elements = null;
        Document document = null;
        synchronized (page.getUrl()) {
            try {
                HttpRequestFields httpFields = params.getHttpRequestFields();
                response = Jsoup.connect(page.getUrl())
                        .userAgent(httpFields.getUserAgent())
                        .referrer(httpFields.getReferer())
                        .execute();
                document = response.parse();
            } catch (IOException e) {
                return false;
            }
            page.setCode(response.statusCode());
            if (launchType > 0) {
                PageRepository pageRepository = params.getPageRepository();
                String content = Jsoup.clean(document.html(), new Safelist());
                page.setContent(Util.extractWord(content));
                page.setTitle(document.title());
                try {
                    pageRepository.saveAndFlush(page);
                } catch (Exception e) {
                    return false;
                }
                Map<String, Integer> lemmas = params.getLemmaFinder().getLemmas(page.getContent());
                List<LemmaTmp> list = lemmas.keySet().stream().map(word -> {
                    LemmaTmp lemma = new LemmaTmp();
                    lemma.setSite(page.getSite().getUrl());
                    lemma.setPage(page.getPath());
                    lemma.setLemma(word);
                    lemma.setFrequency(lemmas.get(word));
                    return lemma;
                }).toList();
                params.getLemmaTmpRepository().saveAllAndFlush(list);
            }
        }
        if (launchType == 2) {
            elements = document.select("a[href]");

            elements.forEach(element -> {
                String urlName = element.absUrl("href");
                Page subPage = addSubPage(urlName, page);
                if (subPage != null) {
                    addTask(subPage);
                }
            });
        }
        return true;
    }

    private Page addSubPage(String urlName, Page page) {
        if (Util.incorrectUrl(urlName)) {
            return null;
        }
        if (page.getUrl().equals(urlName)) {
            return null;
        }
        String domain = Util.getDomainName(urlName);
        Site site = page.getSite();
        if (!site.getDomain().equals(domain)) {
            return null;
        }
        Page subPage = new Page();
        subPage.setSite(site);
        subPage.setUrl(urlName);
        subPage.setPath(Util.getPath(urlName, domain));
        synchronized (subPage.getPath()) {
            if (!params.getPageList().add(subPage)) {
                return null;
            }
        }
        return subPage;
    }

    private void addTask(Page subPage) {
        SiteParserNode node = params.getNode();

        SiteParserNode task = new SiteParserNode(subPage, node.getSiteParser());
        task.fork();

        node.getTaskList().add(task);
    }
}
