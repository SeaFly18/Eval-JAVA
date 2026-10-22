package fr.vinci.petitcrudfilms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MonumentsServiceTest {

    @Test
    void returnsNearestMonumentsSortedByDistance() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MonumentsService service = new MonumentsService(restClientBuilder);

        server.expect(requestTo(containsString("geocodage/reverse")))
                .andRespond(withSuccess("""
                        {
                          "features": [
                            {
                              "properties": {
                                "label": "8 Place de l'Hôtel de Ville 75004 Paris",
                                "city": "Paris",
                                "citycode": "75104"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(containsString("tabular-api")))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "Reference": "PA00086319",
                              "Titre_editorial_de_la_notice": "Hôtel de ville",
                              "Adresse_forme_editoriale": "place de l'Hôtel-de-Ville",
                              "Commune_forme_editoriale": "Paris 4e Arrondissement",
                              "Nature_de_la_protection": "arrêté",
                              "coordonnees_au_format_WGS84": "48.8566,2.3522"
                            },
                            {
                              "Reference": "PA00086395",
                              "Denomination_de_l_edifice": "Immeuble",
                              "Adresse_forme_editoriale": "9 rue du Renard",
                              "Commune_forme_editoriale": "Paris 4e Arrondissement",
                              "Nature_de_la_protection": "arrêté",
                              "coordonnees_au_format_WGS84": "48.8600,2.3500"
                            }
                          ],
                          "links": {"next": null}
                        }
                        """, MediaType.APPLICATION_JSON));

        MonumentsService.MonumentsResponse response = service.getMonumentsProches(48.8566, 2.3522, 10);

        assertThat(response.commune()).isEqualTo("Paris");
        assertThat(response.departement()).isEqualTo("75");
        assertThat(response.monuments())
                .extracting(MonumentsService.Monument::titre)
                .containsExactly("Hôtel de ville", "Immeuble");
        assertThat(response.monuments().getFirst().distanceMetres()).isLessThan(1.0);
        assertThat(response.monuments().getFirst().urlNotice())
                .isEqualTo("https://www.pop.culture.gouv.fr/notice/merimee/PA00086319");

        server.verify();
    }
}
