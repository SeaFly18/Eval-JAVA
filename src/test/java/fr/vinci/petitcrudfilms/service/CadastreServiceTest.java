package fr.vinci.petitcrudfilms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CadastreServiceTest {

    @Test
    void returnsParcellesFromFeatureCollection() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        CadastreService service = new CadastreService(restClientBuilder);

        server.expect(queryParam("geom", "%7B%22type%22%3A%22Point%22%2C%22coordinates%22%3A%5B2.3522%2C48.8566%5D%7D"))
                .andRespond(withSuccess("""
                        {
                          "type": "FeatureCollection",
                          "features": [
                            {
                              "type": "Feature",
                              "properties": {
                                "numero": "0003",
                                "section": "AE",
                                "nom_com": "Paris",
                                "idu": "75104000AE0003",
                                "contenance": 15168,
                                "code_insee": "75056"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CadastreService.CadastreResponse response = service.getParcelles(48.8566, 2.3522);

        assertThat(response.parcelles()).hasSize(1);
        CadastreService.Parcelle parcelle = response.parcelles().getFirst();
        assertThat(parcelle.idu()).isEqualTo("75104000AE0003");
        assertThat(parcelle.section()).isEqualTo("AE");
        assertThat(parcelle.numero()).isEqualTo("0003");
        assertThat(parcelle.commune()).isEqualTo("Paris");
        assertThat(parcelle.codeInsee()).isEqualTo("75056");
        assertThat(parcelle.contenance()).isEqualTo(15168);

        server.verify();
    }
}
