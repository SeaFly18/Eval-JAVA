package fr.vinci.petitcrudfilms.service;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeorisquesService {

    private static final int MAX_ATTEMPTS = 8;
    private static final long RETRY_DELAY_MS = 500;

    private final RestClient restClient;

    public GeorisquesService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://georisques.gouv.fr/api/v1")
                .defaultHeader(HttpHeaders.USER_AGENT, "infos-adresse")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
                .build();
    }

    public GeorisquesResponse getRisques(double lat, double lon) {
        JsonNode response = fetchRisques(lat, lon);

        if (response == null) {
            throw new IllegalStateException("La réponse de Géorisques est vide");
        }

        String adresse = requiredText(response.path("adresse").path("libelle"), "adresse.libelle");
        String commune = requiredText(response.path("commune").path("libelle"), "commune.libelle");

        return new GeorisquesResponse(adresse, commune, extractRisques(response));
    }

    private JsonNode fetchRisques(double lat, double lon) {
        ResourceAccessException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/resultats_rapport_risque")
                                .queryParam("latlon", lon + "," + lat)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);
            } catch (ResourceAccessException e) {
                last = e;
                sleep(RETRY_DELAY_MS);
            }
        }
        throw new IllegalStateException(
                "Géorisques injoignable après " + MAX_ATTEMPTS + " tentatives", last);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption pendant l'attente d'un nouvel essai", e);
        }
    }

    private List<RisquePrincipal> extractRisques(JsonNode response) {
        List<RisquePrincipal> risques = new ArrayList<>();
        addPresentRisques(risques, response.path("risquesNaturels"), "naturel");
        addPresentRisques(risques, response.path("risquesTechnologiques"), "technologique");
        return risques;
    }

    private void addPresentRisques(List<RisquePrincipal> risques, JsonNode categorie, String type) {
        if (!categorie.isObject()) {
            return;
        }

        categorie.properties().forEach(entry -> {
            JsonNode risque = entry.getValue();
            if (risque.path("present").asBoolean(false)) {
                risques.add(new RisquePrincipal(
                        type,
                        requiredText(risque.path("libelle"), "libelle du risque"),
                        risque.path("libelleStatutCommune").asString(null),
                        risque.path("libelleStatutAdresse").asString(null)
                ));
            }
        });
    }

    private String requiredText(JsonNode value, String field) {
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new IllegalStateException("Champ absent dans la réponse Géorisques : " + field);
        }
        return value.asString();
    }

    public record GeorisquesResponse(String adresse, String commune, List<RisquePrincipal> risquesPrincipaux) {
    }

    public record RisquePrincipal(
            String categorie,
            String libelle,
            String libelleStatutCommune,
            String libelleStatutAdresse
    ) {
    }
}
