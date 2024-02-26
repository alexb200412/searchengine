package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "lemma_tmp")
public class LemmaTmp {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "seq")
    @GenericGenerator(name = "seq", strategy = "native")
    private int id;

    private String site;
    private String page;
    private String lemma;
    private int frequency;
}
