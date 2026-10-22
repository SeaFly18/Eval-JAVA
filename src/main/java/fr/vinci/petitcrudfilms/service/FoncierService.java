package fr.vinci.petitcrudfilms.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class FoncierService {

    private static final String GEOCODE_REVERSE = "https://data.geopf.fr/geocodage/reverse";
    private static final String DVF_URL =
            "https://files.data.gouv.fr/geo-dvf/latest/csv/%d/communes/%s/%s.csv";
    private static final List<String> LOGEMENTS = List.of("Maison", "Appartement");
    private static final int ANNEES = 5;

    private final RestClient restClient;

    public FoncierService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public FoncierResponse getVentesProches(double lat, double lon, int n) {
        Localisation loc = reverseGeocode(lat, lon);

        List<Map<String, String>> rows = new ArrayList<>();
        int thisYear = Year.now().getValue();
        for (int year = thisYear; year > thisYear - ANNEES; year--) {
            List<Map<String, String>> data = fetchYear(loc.code(), loc.departement(), year);
            if (data != null) {
                rows.addAll(data);
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Aucun fichier DVF trouvé pour la commune " + loc.code());
        }

        List<Vente> ventes = new ArrayList<>();
        for (Mutation m : buildMutations(rows)) {
            if (!"Vente".equals(m.nature) || m.valeur == null || m.lat == null || m.lon == null) {
                continue;
            }
            double distance = haversineMetres(lat, lon, m.lat, m.lon);
            ventes.add(m.toVente(distance));
        }

        ventes.sort(Comparator.comparingDouble(Vente::distanceMetres));
        List<Vente> proches = ventes.subList(0, Math.min(n, ventes.size()));

        return new FoncierResponse(loc.adresse(), loc.commune(), loc.code(), List.copyOf(proches));
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
        String code = props.path("citycode").asString("");
        String dept = code.startsWith("97")
                ? code.substring(0, Math.min(3, code.length()))
                : code.substring(0, Math.min(2, code.length()));

        return new Localisation(props.path("label").asString(""),
                props.path("city").asString(""), code, dept);
    }

    private List<Map<String, String>> fetchYear(String code, String dept, int year) {
        String csv = getFollowingRedirects(String.format(DVF_URL, year, dept, code));
        return csv == null ? null : parseCsv(csv);
    }

    private String getFollowingRedirects(String url) {
        String current = url;
        for (int hop = 0; hop < 5; hop++) {
            String target = current;
            try {
                ResponseEntity<byte[]> entity = restClient.get()
                        .uri(uriBuilder -> URI.create(target))
                        .retrieve()
                        .toEntity(byte[].class);

                if (entity.getStatusCode().is3xxRedirection()) {
                    URI location = entity.getHeaders().getLocation();
                    if (location == null) {
                        return null;
                    }
                    current = location.toString();
                    continue;
                }
                byte[] body = entity.getBody();
                return body == null ? null : new String(body, StandardCharsets.UTF_8);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    return null;
                }
                throw e;
            }
        }
        throw new IllegalStateException("Trop de redirections pour " + url);
    }

    private List<Map<String, String>> parseCsv(String csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = csv.split("\n", -1);
        if (lines.length == 0) {
            return rows;
        }
        String[] headers = splitCsvLine(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] values = splitCsvLine(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < values.length; c++) {
                row.put(headers[c], values[c]);
            }
            rows.add(row);
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else if (ch != '\r') {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private List<Mutation> buildMutations(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            groups.computeIfAbsent(row.getOrDefault("id_mutation", ""), k -> new ArrayList<>())
                    .add(row);
        }

        List<Mutation> mutations = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : groups.entrySet()) {
            List<Map<String, String>> lines = entry.getValue();
            Map<String, String> first = lines.get(0);

            Double valeur = null;
            Double lat = null;
            Double lon = null;
            for (Map<String, String> line : lines) {
                if (valeur == null) {
                    valeur = toDouble(line.get("valeur_fonciere"));
                }
                if (lat == null || lon == null) {
                    Double la = toDouble(line.get("latitude"));
                    Double lo = toDouble(line.get("longitude"));
                    if (la != null && lo != null) {
                        lat = la;
                        lon = lo;
                    }
                }
            }

            TreeSet<String> types = new TreeSet<>();
            double bati = 0;
            double pieces = 0;
            Map<String, Double> parcelles = new LinkedHashMap<>();
            for (Map<String, String> line : lines) {
                String type = line.get("type_local");
                if (type != null && !type.isBlank()) {
                    types.add(type);
                    if (LOGEMENTS.contains(type)) {
                        bati += orZero(toDouble(line.get("surface_reelle_bati")));
                        pieces += orZero(toDouble(line.get("nombre_pieces_principales")));
                    }
                }
                String pid = line.get("id_parcelle");
                Double surface = toDouble(line.get("surface_terrain"));
                if (pid != null && !pid.isBlank() && surface != null) {
                    parcelles.merge(pid, surface, Math::max);
                }
            }
            double terrain = parcelles.values().stream().mapToDouble(Double::doubleValue).sum();

            List<String> logements = types.stream().filter(LOGEMENTS::contains).toList();
            List<String> autres = types.stream()
                    .filter(t -> !LOGEMENTS.contains(t) && !"Dépendance".equals(t)).toList();
            String logement = logements.size() == 1 && autres.isEmpty() ? logements.get(0) : null;
            Double prixM2 = (valeur != null && bati > 0 && logement != null) ? valeur / bati : null;

            String adresse = joinNonBlank(first.get("adresse_numero"),
                    first.get("adresse_suffixe"), first.get("adresse_nom_voie"));

            Mutation mutation = new Mutation();
            mutation.idMutation = entry.getKey();
            mutation.date = first.getOrDefault("date_mutation", "");
            mutation.nature = first.getOrDefault("nature_mutation", "");
            mutation.valeur = valeur;
            mutation.adresse = adresse.isBlank() ? "(adresse inconnue)" : adresse;
            mutation.codePostal = first.getOrDefault("code_postal", "");
            mutation.types = types.isEmpty() ? "terrain / autre" : String.join(", ", types);
            mutation.surfaceBati = bati;
            mutation.pieces = pieces;
            mutation.terrain = terrain;
            mutation.prixM2 = prixM2;
            mutation.lat = lat;
            mutation.lon = lon;
            mutations.add(mutation);
        }
        return mutations;
    }

    private String joinNonBlank(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.trim());
            }
        }
        return String.join(" ", kept);
    }

    private Double toDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double orZero(Double value) {
        return value == null ? 0 : value;
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

    private record Localisation(String adresse, String commune, String code, String departement) {
    }

    private static final class Mutation {
        String idMutation;
        String date;
        String nature;
        Double valeur;
        String adresse;
        String codePostal;
        String types;
        double surfaceBati;
        double pieces;
        double terrain;
        Double prixM2;
        Double lat;
        Double lon;

        Vente toVente(double distance) {
            return new Vente(idMutation, date, nature, valeur, adresse, codePostal, types,
                    surfaceBati, pieces, terrain, prixM2, distance);
        }
    }

    public record FoncierResponse(
            String adresse,
            String commune,
            String codeInsee,
            List<Vente> ventes
    ) {
    }

    public record Vente(
            String idMutation,
            String date,
            String nature,
            Double valeur,
            String adresse,
            String codePostal,
            String types,
            double surfaceBati,
            double pieces,
            double terrain,
            Double prixM2,
            double distanceMetres
    ) {
    }
}
