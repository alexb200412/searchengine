package searchengine.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.core.JdbcTemplate;
import searchengine.config.HttpRequestFields;
import searchengine.config.SiteConfig;
import searchengine.repositories.LemmaTmpRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

@Getter
@Setter
public class SiteParserParams {
    private SiteConfig siteConfig;
    private SiteRepository siteRepository;
    private PageRepository pageRepository;
    private LemmaTmpRepository lemmaTmpRepository;
    private JdbcTemplate jdbcTemplate;
    private HttpRequestFields httpRequestFields;
}
