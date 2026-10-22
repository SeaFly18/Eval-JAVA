package fr.vinci.petitcrudfilms.repository;

public class FilmRepositoryImplementation {
    FilmRepository jpa;
    public FilmRepositoryImplementation (FilmRepository jpa) {
        this.jpa = jpa;
    }
}
