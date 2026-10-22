package fr.vinci.petitcrudfilms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeorisquesServiceTest {

    @Test
    void returnsAddressAndOnlyPresentRisks() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GeorisquesService service = new GeorisquesService(restClientBuilder);

        server.expect(requestTo("https://georisques.gouv.fr/api/v1/resultats_rapport_risque?latlon=2.3522,48.8566"))
                .andExpect(queryParam("latlon", "2.3522,48.8566"))
                .andRespond(withSuccess("""
                        {
                          "adresse": {"libelle": "8 Place de l'Hôtel de Ville, Paris"},
                          "commune": {"libelle": "Paris"},
                          "risquesNaturels": {
                            "inondation": {
                              "present": true,
                              "libelle": "Inondation",
                              "libelleStatutCommune": "Risque Existant",
                              "libelleStatutAdresse": "Risque Existant"
                            },
                            "avalanche": {
                              "present": false,
                              "libelle": "Avalanche",
                              "libelleStatutCommune": null,
                              "libelleStatutAdresse": null
                            }
                          },
                          "risquesTechnologiques": {
                            "icpe": {
                              "present": true,
                              "libelle": "Installations industrielles classées (ICPE)",
                              "libelleStatutCommune": "Risque Concerne",
                              "libelleStatutAdresse": "Risque Concerne"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeorisquesService.GeorisquesResponse response = service.getRisques(48.8566, 2.3522);

        assertThat(response.adresse()).isEqualTo("8 Place de l'Hôtel de Ville, Paris");
        assertThat(response.commune()).isEqualTo("Paris");
        assertThat(response.risquesPrincipaux())
                .extracting(GeorisquesService.RisquePrincipal::libelle)
                .containsExactly("Inondation", "Installations industrielles classées (ICPE)");
        assertThat(response.risquesPrincipaux())
                .extracting(GeorisquesService.RisquePrincipal::categorie)
                .containsExactly("naturel", "technologique");

        server.verify();
    }
}
