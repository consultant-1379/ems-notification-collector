/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/
package com.ericsson.oss.adc.emsnc.client.enm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.ericsson.oss.adc.emsnc.client.enm.EnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.EnmLogin;
import com.ericsson.oss.adc.emsnc.client.enm.RetrofitConfiguration;
import java.net.InetAddress;
import java.net.URI;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import retrofit2.Retrofit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetEnmServerCookieTest {

  public final URI LOCATION = URI.create("https://ieatenm5439-6.athtem.eei.ericsson.se/");
  private EmsCredentials emsCredentials;
  private EnmClient enmClient;

  @SneakyThrows
  @BeforeAll
  public void setup() {
    // TODO test with wiremock
    assumeTrue(InetAddress.getByName(LOCATION.getHost()).isReachable(5000));

    emsCredentials = new EmsCredentials("", "", "", LOCATION);

    RetrofitConfiguration configuration = new RetrofitConfiguration(true);
    Retrofit enmBuilder = configuration.createEnmBuilder(emsCredentials.getLocation());

    enmClient = enmBuilder.create(EnmClient.class);
  }

  @Test
  public void getAuthToken() {
    emsCredentials = new EmsCredentials("", "administrator", "TestPassw0rd", LOCATION);

    String token = EnmLogin.queryForAuthenticationToken(emsCredentials, enmClient);
    assertNotNull(token);
    assertTrue(
        token.length() > emsCredentials.getCookieName().length() + 1, "auth token cannot be null");
  }

  @Test
  public void getAuthTokenFailure() {
    emsCredentials = new EmsCredentials("", "administrator", "WrongPassword", LOCATION);

    // TODO check and fix assertion
    HttpClientErrorException exception =
        assertThrows(
            HttpClientErrorException.class,
            () -> EnmLogin.queryForAuthenticationToken(emsCredentials, enmClient));
    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }
}
