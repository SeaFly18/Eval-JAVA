package fr.vinci.petitcrudfilms.repository;

import fr.vinci.petitcrudfilms.entity.Film;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmRepository extends JpaRepository<Film, Long> {
    Film findFirstByTitre(String titre);
}
