package fr.vinci.petitcrudfilms.controller;

import fr.vinci.petitcrudfilms.entity.GeocodageResult;
import fr.vinci.petitcrudfilms.service.CadastreService;
import fr.vinci.petitcrudfilms.service.FoncierService;
import fr.vinci.petitcrudfilms.service.GeocodageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {
    private GeocodageService geocodageService;
    private CadastreService cadastreService;
    private FoncierService foncierService;
    public AppController(GeocodageService geocodageService, CadastreService cadastreService, FoncierService foncierService) {
        this.geocodageService = geocodageService;
        this.cadastreService = cadastreService;
        this.foncierService = foncierService;
    }

    @GetMapping("/cadastre/{adresse}")
    public ResponseEntity<CadastreService.CadastreResponse> getCadastre(@PathVariable String adresse) {
        GeocodageResult result = geocodageService.getAdresse(adresse);
        Double[] coordinates = result.features[0].geometry.coordinates;
        CadastreService.CadastreResponse cadastreRes = cadastreService.getParcelles(coordinates[1], coordinates[0]);
        System.out.println(coordinates[0]);
        System.out.println(coordinates[1]);

        return ResponseEntity.ok(cadastreRes);
    }

    @GetMapping("/foncier/{adresse}")
    public ResponseEntity<FoncierService.FoncierResponse> getFoncier(
            @PathVariable String adresse,
            @RequestParam Integer occurrencies
    ) {
        Double[] coords = geocodageService.getAdresse(adresse).features[0].geometry.coordinates;
        FoncierService.FoncierResponse res = foncierService.getVentesProches(coords[1], coords[0], occurrencies);
        return ResponseEntity.ok().body(res);
    }
}
