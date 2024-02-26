package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaTmp;

public interface LemmaTmpRepositories extends JpaRepository<LemmaTmp, Integer> {
}
