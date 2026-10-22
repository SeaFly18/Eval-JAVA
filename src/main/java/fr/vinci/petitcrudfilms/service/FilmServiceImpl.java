package fr.vinci.petitcrudfilms.service;

import fr.vinci.petitcrudfilms.entity.Film;
import fr.vinci.petitcrudfilms.repository.FilmRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class FilmServiceImpl implements fr.vinci.petitcrudfilms.service.FilmService {

    private final FilmRepository filmRepository;

    public FilmServiceImpl(FilmRepository filmRepository) {
        this.filmRepository = filmRepository;
    }

    @Override
    public List<Film> getAllFilms() {
        return filmRepository.findAll();
    }

    @Override
    public Optional<Film> getFilmById(Long id) {
        return filmRepository.findById(id);
    }

    @Override
    public Film saveFilm(Film film) {
        return filmRepository.save(film);
    }

    @Override
    public Film updateFilm(Long id, Film filmDetails) {
        return filmRepository.findById(id).map(film -> {
            film.setTitre(filmDetails.getTitre());
            film.setDateDeSortie(filmDetails.getDateDeSortie());
            film.setResume(filmDetails.getResume());
            film.setRealisateur(filmDetails.getRealisateur());
            return filmRepository.save(film);
        }).orElseThrow(() -> new RuntimeException("Film non trouvé"));
    }

    @Override
    public boolean deleteFilm(Long id) {
        return filmRepository.findById(id).map(film -> {
            filmRepository.delete(film);
            return true;
        }).orElse(false);
    }

    @Override
    public Film getFilmByTitle(String titre){
        return filmRepository.findFirstByTitre(titre);
    }
}