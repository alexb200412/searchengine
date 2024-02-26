package searchengine.services;

import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class SiteParserNode extends RecursiveTask<Integer> {
    private final Page page;
    private final SiteParser siteParser;
    private final List<SiteParserNode> taskList;

    public SiteParser getSiteParser() {
        return siteParser;
    }

    public List<SiteParserNode> getTaskList() {
        return taskList;
    }

    public SiteParserNode(Page page, SiteParser siteParser) {
        this.page = page;
        this.siteParser = siteParser;
        this.taskList = new ArrayList<>();
    }

    @Override
    protected Integer compute() {
        int pageCount = 0;
        if (!siteParser.isActive()) return pageCount;

        PageParserParams pageParams = new PageParserParams();
        pageParams.setPageRepository(siteParser.getPageRepository());
        pageParams.setPage(page);
        pageParams.setPageList(siteParser.getPageList());
        pageParams.setHttpRequestFields(siteParser.getParams().getHttpRequestFields());
        pageParams.setNode(this);
        pageParams.setLemmaFinder(siteParser.getLemmaFinder());
        pageParams.setLemmaTmpRepository(siteParser.getParams().getLemmaTmpRepository());

        PageParser pageParser = new PageParser(pageParams);
        pageParser.process(2);

        pageCount = taskList.size();

        for (SiteParserNode task : taskList) {
            pageCount += task.join();
        }
        return pageCount;
    }


}
