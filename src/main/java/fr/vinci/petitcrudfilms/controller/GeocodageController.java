package fr.vinci.petitcrudfilms.controller;

import fr.vinci.petitcrudfilms.entity.GeocodageResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class GeocodageController {
    private final RestClient geocodageClient = RestClient.create("https://data.geopf.fr");
//    https://data.geopf.fr/geocodage/search?q=4+place+de+l+Hotel+de+Ville+Paris&limit=1
        @GetMapping("/adresse/{adresse}")
        public ResponseEntity<GeocodageResult> getAdresse(
                @PathVariable String adresse
        ) {
            GeocodageResult data = geocodageClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocodage/search")
                            .queryParam("q", adresse)
                            .queryParam("limit", 1)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GeocodageResult.class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(data);
        }
    }

