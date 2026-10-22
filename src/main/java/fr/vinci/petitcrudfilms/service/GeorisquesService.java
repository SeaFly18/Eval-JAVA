package fr.vinci.petitcrudfilms.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeorisquesService {

    private final RestClient restClient;

    public GeorisquesService() {
        this(RestClient.builder());
    }

    GeorisquesService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://georisques.gouv.fr/api/v1")
                .build();
    }

    public GeorisquesResponse getRisques(double lat, double lon) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/resultats_rapport_risque")
                        .queryParam("latlon", lon + "," + lat)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("La réponse de Géorisques est vide");
        }

        String adresse = requiredText(response.path("adresse").path("libelle"), "adresse.libelle");
        String commune = requiredText(response.path("commune").path("libelle"), "commune.libelle");

        return new GeorisquesResponse(adresse, commune, extractRisques(response));
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
                        risque.path("libelleStatutCommune").asText(null),
                        risque.path("libelleStatutAdresse").asText(null)
                ));
            }
        });
    }

    private String requiredText(JsonNode value, String field) {
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException("Champ absent dans la réponse Géorisques : " + field);
        }
        return value.asText();
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
