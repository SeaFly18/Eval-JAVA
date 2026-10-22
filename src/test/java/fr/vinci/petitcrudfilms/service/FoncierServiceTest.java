package fr.vinci.petitcrudfilms.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FoncierServiceTest {

    @Test
    void returnsNearestSalesGroupedByMutation() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        FoncierService service = new FoncierService(restClientBuilder);

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

        int thisYear = Year.now().getValue();
        server.expect(requestTo(containsString("/" + thisYear + "/communes")))
                .andRespond(withSuccess("""
                        id_mutation,date_mutation,nature_mutation,valeur_fonciere,adresse_numero,adresse_nom_voie,code_postal,id_parcelle,type_local,surface_reelle_bati,nombre_pieces_principales,surface_terrain,longitude,latitude
                        M1,2025-03-04,Vente,400000,1,RUE PROCHE,75004,P1,Appartement,40,2,,2.3525,48.8570
                        M1,2025-03-04,Vente,400000,1,RUE PROCHE,75004,P1,Dépendance,,,,2.3525,48.8570
                        M2,2025-05-10,Vente,800000,50,RUE LOIN,75004,P2,Maison,100,5,120,2.3600,48.8620
                        M3,2025-06-01,Echange,10000,9,RUE X,75004,P3,Appartement,30,1,,2.3523,48.8567
                        """, MediaType.TEXT_PLAIN));

        for (int year = thisYear - 1; year > thisYear - 5; year--) {
            server.expect(requestTo(containsString("/" + year + "/communes")))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND));
        }

        FoncierService.FoncierResponse response = service.getVentesProches(48.8566, 2.3522, 10);

        assertThat(response.commune()).isEqualTo("Paris");
        assertThat(response.codeInsee()).isEqualTo("75104");

        // M3 (Echange) est filtré ; M1 est plus proche que M2
        assertThat(response.ventes())
                .extracting(FoncierService.Vente::idMutation)
                .containsExactly("M1", "M2");

        FoncierService.Vente m1 = response.ventes().getFirst();
        assertThat(m1.types()).isEqualTo("Appartement, Dépendance");
        assertThat(m1.surfaceBati()).isEqualTo(40.0);
        assertThat(m1.prixM2()).isEqualTo(10000.0);
        assertThat(m1.distanceMetres()).isLessThan(m1.valeur());

        server.verify();
    }
}
