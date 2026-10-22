package fr.vinci.petitcrudfilms.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MonumentsService {

    private static final String RID = "3a52af4a-f9da-4dcc-8110-b07774dfb3bc";
    private static final String TABULAR =
            "https://tabular-api.data.gouv.fr/api/resources/" + RID + "/data/";
    private static final String GEOCODE_REVERSE =
            "https://data.geopf.fr/geocodage/reverse";
    private static final String POP_NOTICE =
            "https://www.pop.culture.gouv.fr/notice/merimee/";

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final RestClient restClient;

    public MonumentsService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public MonumentsResponse getMonumentsProches(double lat, double lon, int n) {
        Localisation loc = reverseGeocode(lat, lon);

        List<JsonNode> rows = fetchDepartment(loc.departement());

        List<Monument> monuments = new ArrayList<>();
        for (JsonNode row : rows) {
            double[] coords = parseCoords(row.path("coordonnees_au_format_WGS84").asText(null));
            if (coords == null) {
                continue;
            }
            double distance = haversineMetres(lat, lon, coords[0], coords[1]);
            monuments.add(toMonument(row, distance));
        }

        monuments.sort(Comparator.comparingDouble(Monument::distanceMetres));
        List<Monument> proches = monuments.subList(0, Math.min(n, monuments.size()));

        return new MonumentsResponse(loc.adresse(), loc.commune(), loc.departement(),
                List.copyOf(proches));
    }

    private Localisation reverseGeocode(double lat, double lon) {
        JsonNode data = restClient.get()
                .uri(uriBuilder -> URI.create(GEOCODE_REVERSE
                        + "?lon=" + lon + "&lat=" + lat + "&limit=1"))
                .retrieve()
                .body(JsonNode.class);

        JsonNode features = data == null ? null : data.path("features");
        if (features == null || !features.isArray() || features.isEmpty()) {
            throw new IllegalStateException("Aucune adresse trouvée pour ces coordonnées");
        }

        JsonNode props = features.get(0).path("properties");
        String citycode = props.path("citycode").asText("");
        String dept = citycode.startsWith("97")
                ? citycode.substring(0, Math.min(3, citycode.length()))
                : citycode.substring(0, Math.min(2, citycode.length()));

        return new Localisation(
                props.path("label").asText(""),
                props.path("city").asText(""),
                dept);
    }

    private List<JsonNode> fetchDepartment(String dept) {
        List<JsonNode> rows = fetchAll(dept);
        if (rows.isEmpty() && dept.startsWith("0")) {
            rows = fetchAll(dept.replaceFirst("^0+", ""));
        }
        return rows;
    }

    private List<JsonNode> fetchAll(String dept) {
        List<JsonNode> rows = new ArrayList<>();
        String url = TABULAR + "?Departement_format_numerique__exact=" + dept + "&page_size=100";

        while (url != null) {
            String current = url;
            JsonNode page = restClient.get()
                    .uri(uriBuilder -> URI.create(current))
                    .retrieve()
                    .body(JsonNode.class);

            if (page == null) {
                break;
            }
            for (JsonNode row : page.path("data")) {
                rows.add(row);
            }
            JsonNode next = page.path("links").path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText(null);
        }
        return rows;
    }

    private Monument toMonument(JsonNode row, double distance) {
        return new Monument(
                titre(row),
                row.path("Adresse_forme_editoriale").asText(null),
                row.path("Commune_forme_editoriale").asText(null),
                row.path("Nature_de_la_protection").asText(null),
                row.path("Reference").asText(null),
                distance,
                POP_NOTICE + row.path("Reference").asText(""));
    }

    private String titre(JsonNode row) {
        for (String key : List.of("Titre_editorial_de_la_notice",
                "Denomination_de_l_edifice", "Autre_appellation_de_l_edifice")) {
            String value = row.path(key).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "(sans titre)";
    }

    private double[] parseCoords(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(value);
        List<Double> nums = new ArrayList<>();
        while (matcher.find() && nums.size() < 2) {
            nums.add(Double.parseDouble(matcher.group()));
        }
        if (nums.size() < 2) {
            return null;
        }
        double a = nums.get(0);
        double b = nums.get(1);
        if (a < -90 || a > 90 || (Math.abs(a) < 12 && Math.abs(b) > 35 && Math.abs(b) < 60)) {
            double tmp = a;
            a = b;
            b = tmp;
        }
        return new double[]{a, b};
    }

    private double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dphi = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dphi / 2) * Math.sin(dphi / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    private record Localisation(String adresse, String commune, String departement) {
    }

    public record MonumentsResponse(
            String adresse,
            String commune,
            String departement,
            List<Monument> monuments
    ) {
    }

    public record Monument(
            String titre,
            String adresse,
            String commune,
            String nature,
            String reference,
            double distanceMetres,
            String urlNotice
    ) {
    }
}
