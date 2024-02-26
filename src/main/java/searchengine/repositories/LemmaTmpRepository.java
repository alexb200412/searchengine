package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaTmp;

public interface LemmaTmpRepository extends JpaRepository<LemmaTmp, Integer> {
}
