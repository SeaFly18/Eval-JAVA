package fr.vinci.petitcrudfilms.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class CadastreService {

    private final RestClient restClient;

    public CadastreService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://apicarto.ign.fr/api/cadastre")
                .build();
    }

    public CadastreResponse getParcelles(double lat, double lon) {
        String geom = "{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}";

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/parcelle")
                        .queryParam("geom", "{geom}")
                        .build(geom))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("La réponse du Cadastre est vide");
        }

        return new CadastreResponse(extractParcelles(response));
    }

    private List<Parcelle> extractParcelles(JsonNode response) {
        List<Parcelle> parcelles = new ArrayList<>();
        JsonNode features = response.path("features");
        if (!features.isArray()) {
            return parcelles;
        }

        for (JsonNode feature : features) {
            JsonNode props = feature.path("properties");
            parcelles.add(new Parcelle(
                    requiredText(props.path("idu"), "idu"),
                    requiredText(props.path("section"), "section"),
                    requiredText(props.path("numero"), "numero"),
                    requiredText(props.path("nom_com"), "nom_com"),
                    requiredText(props.path("code_insee"), "code_insee"),
                    props.path("contenance").asInt()
            ));
        }
        return parcelles;
    }

    private String requiredText(JsonNode value, String field) {
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            throw new IllegalStateException("Champ absent dans la réponse Cadastre : " + field);
        }
        return value.asString();
    }

    public record CadastreResponse(List<Parcelle> parcelles) {
    }

    public record Parcelle(
            String idu,
            String section,
            String numero,
            String commune,
            String codeInsee,
            int contenance
    ) {
    }
}
