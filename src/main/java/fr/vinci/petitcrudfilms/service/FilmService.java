package fr.vinci.petitcrudfilms.service;

import fr.vinci.petitcrudfilms.entity.Film;
import java.util.List;
import java.util.Optional;


public interface FilmService {
    List<Film> getAllFilms();
    Optional<Film> getFilmById(Long id);
    Film saveFilm(Film film);
    Film updateFilm(Long id, Film filmDetails);
    boolean deleteFilm(Long id);

    // ----------------------------------
    Film getFilmByTitle(String titre);
}

