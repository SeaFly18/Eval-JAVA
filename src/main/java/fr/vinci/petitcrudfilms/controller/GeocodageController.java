package fr.vinci.petitcrudfilms.controller;

import fr.vinci.petitcrudfilms.entity.GeocodageResult;
import fr.vinci.petitcrudfilms.service.GeocodageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeocodageController {
    private final GeocodageService geocodageService;

    public GeocodageController(GeocodageService geocodageService) {
        this.geocodageService = geocodageService;
    }

    @GetMapping("/adresse/{adresse}")
    public ResponseEntity<GeocodageResult> getAdresse(@PathVariable String adresse) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(geocodageService.getAdresse(adresse));
    }
}

