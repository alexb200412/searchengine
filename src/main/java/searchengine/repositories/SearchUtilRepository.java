package searchengine.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class SearchUtilRepository {
    private final JdbcTemplate jdbcTemplate;

    public Integer getLemmasCount(String word, String siteUrls) {
        Integer count = jdbcTemplate.queryForObject(
                "select sum(frequency) from lemma l, site s where l.site_id=s.id and l.lemma='" + word + "' and upper(s.url) in " + siteUrls, Integer.class);
        return count;
    }
}
