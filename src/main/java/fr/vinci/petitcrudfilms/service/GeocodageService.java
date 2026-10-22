package fr.vinci.petitcrudfilms.service;

import fr.vinci.petitcrudfilms.entity.GeocodageResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeocodageService {

    private final RestClient restClient;

    public GeocodageService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://data.geopf.fr/geocodage")
                .build();
    }

    public GeocodageResult getAdresse(String adresse) {
        GeocodageResult result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", adresse)
                        .queryParam("limit", 1)
                        .build())
                .retrieve()
                .body(GeocodageResult.class);

        if (result == null || result.features == null || result.features.length == 0) {
            throw new IllegalStateException("Aucune adresse trouvée pour : " + adresse);
        }

        return result;
    }
}
