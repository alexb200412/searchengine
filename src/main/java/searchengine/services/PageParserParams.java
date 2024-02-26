package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import searchengine.config.HttpRequestFields;
import searchengine.model.Page;
import searchengine.repositories.LemmaTmpRepository;
import searchengine.repositories.PageRepository;

import java.util.Set;

@Getter
@Setter
public class PageParserParams {
    private Page page;
    private PageRepository pageRepository;
    private LemmaTmpRepository lemmaTmpRepository;
    private HttpRequestFields httpRequestFields;
    private Set<Page> pageList;
    private Set<String> lemmaList;
    private SiteParserNode node;
    private LemmaFinder lemmaFinder;
}
