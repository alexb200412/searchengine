package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "page")
public class Page implements Comparable<Page> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq")
    @GenericGenerator(name = "seq", strategy = "native")
    private int id;
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;
    @Column(nullable = false)
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
    @Column(columnDefinition = "TEXT")
    private String title;

    @Transient
    private String url;

    @Override
    public int compareTo(Page o) {
        return this.getPath().compareTo(o.getPath());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || getClass() != obj.getClass()) {
            return false;
        }
        Page page = (Page) obj;
        return Objects.equals(path, page.path);
    }

    @Override
    public int hashCode() {
        return getPath().hashCode();
    }
}
