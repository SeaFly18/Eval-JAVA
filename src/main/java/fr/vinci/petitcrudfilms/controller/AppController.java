package fr.vinci.petitcrudfilms.controller;

import fr.vinci.petitcrudfilms.entity.GeocodageResult;
import fr.vinci.petitcrudfilms.service.CadastreService;
import fr.vinci.petitcrudfilms.service.FoncierService;
import fr.vinci.petitcrudfilms.service.GeocodageService;
import fr.vinci.petitcrudfilms.service.GeorisquesService;
import fr.vinci.petitcrudfilms.service.GeorisquesService.GeorisquesResponse;
import fr.vinci.petitcrudfilms.service.MonumentsService;
import fr.vinci.petitcrudfilms.service.MonumentsService.MonumentsResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class AppController {

    private final GeocodageService geocodageService;
    private final CadastreService cadastreService;
    private final FoncierService foncierService;
    private final GeorisquesService georisquesService;
    private final MonumentsService monumentsService;

    public AppController(GeocodageService geocodageService,
                         CadastreService cadastreService,
                         FoncierService foncierService,
                         GeorisquesService georisquesService,
                         MonumentsService monumentsService) {
        this.geocodageService = geocodageService;
        this.cadastreService = cadastreService;
        this.foncierService = foncierService;
        this.georisquesService = georisquesService;
        this.monumentsService = monumentsService;
    }

    @GetMapping("/infos/{adresse}")
    public ResponseEntity<InfosResponse> getInfos(
            @PathVariable String adresse,
            @RequestParam(defaultValue = "5") int nbMonuments,
            @RequestParam(defaultValue = "5") int nbVentes) {

        GeocodageResult geocodage = geocodageService.getAdresse(adresse);
        Double[] coordinates = geocodage.features[0].geometry.coordinates;
        double lon = coordinates[0];
        double lat = coordinates[1];

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<GeorisquesResponse> risquesFuture =
                    CompletableFuture.supplyAsync(() -> georisquesService.getRisques(lat, lon), pool);
            CompletableFuture<MonumentsResponse> monumentsFuture =
                    CompletableFuture.supplyAsync(
                            () -> monumentsService.getMonumentsProches(lat, lon, nbMonuments), pool);
            CompletableFuture<CadastreService.CadastreResponse> cadastreFuture =
                    CompletableFuture.supplyAsync(() -> cadastreService.getParcelles(lat, lon), pool);
            CompletableFuture<FoncierService.FoncierResponse> ventesFuture =
                    CompletableFuture.supplyAsync(
                            () -> foncierService.getVentesProches(lat, lon, nbVentes), pool);

            GeorisquesResponse risques = risquesFuture.join();
            MonumentsResponse monuments = monumentsFuture.join();
            CadastreService.CadastreResponse cadastre = cadastreFuture.join();
            FoncierService.FoncierResponse ventes = ventesFuture.join();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InfosResponse(adresse, lat, lon, risques, monuments, cadastre, ventes));
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    public record InfosResponse(
            String adresseRecherchee,
            double latitude,
            double longitude,
            GeorisquesResponse risques,
            MonumentsResponse monuments,
            CadastreService.CadastreResponse cadastre,
            FoncierService.FoncierResponse ventes
    ) {
    }
}
