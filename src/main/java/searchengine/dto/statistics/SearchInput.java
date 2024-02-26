package searchengine.dto.statistics;

import lombok.Data;

@Data
public class SearchInput {
    private String query;
    private String site;
    private int limit;
    private int offset;
}
