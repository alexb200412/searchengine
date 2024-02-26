package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "site")
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq")
    @GenericGenerator(name = "seq", strategy = "native")
    private int id;
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;
    @Column(name = "last_error")
    private String lastError;
    @Column(nullable = false)
    private String url;
    @Column(nullable = false)
    private String name;
    @Column(name = "page_count")
    private int pageCount;
    @Column(name = "lemma_count")
    private int lemmaCount;

    @Transient
    private String domain;
}
