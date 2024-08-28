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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.ericsson.oss.adc.emsnc.client.enm.EnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.EnmLogin;
import com.ericsson.oss.adc.emsnc.client.enm.RetrofitConfiguration;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmCommandResponseData;
import java.net.InetAddress;
import java.net.URI;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class GetListOfNetworkElementsTest {

  public final URI LOCATION = URI.create("https://ieatenm5439-6.athtem.eei.ericsson.se/");

  private final EmsCredentials emsCredentials =
      new EmsCredentials("", "administrator", "TestPassw0rd", LOCATION);
  private String token;
  private EnmClient enmClient;

  @SneakyThrows
  @BeforeEach
  public void setup() {
    assumeTrue(InetAddress.getByName(LOCATION.getHost()).isReachable(5000));

    RetrofitConfiguration configuration = new RetrofitConfiguration(true);
    Retrofit enmBuilder = configuration.createEnmBuilder(emsCredentials.getLocation());

    enmClient = enmBuilder.create(EnmClient.class);

    token = EnmLogin.queryForAuthenticationToken(emsCredentials, enmClient);
    assertNotNull(token);
    assertTrue(
        token.length() > emsCredentials.getCookieName().length() + 1, "auth token cannot be null");
  }

  @SneakyThrows
  @Test
  void testListingNetworkElements() {
    assertNotNull(enmClient);
    assertNotNull(token);
    String command = "cmedit get * NetworkElement --netype=RadioNode";
    Call<ResponseBody> responseBodyCall =
        enmClient.sendEnmCmCommand(
            token,
            RequestBody.create(MediaType.parse("text/plain"), "command"),
            RequestBody.create(MediaType.parse("text/plain"), "true"),
            RequestBody.create(MediaType.parse("text/plain"), command));

    Response<ResponseBody> result = responseBodyCall.execute();

    assertNotNull(result.body());
    String requestId = result.body().string();
    EnmCommandResponseData enmResponseData =
        enmClient.getEnmCmCommandOutput(token, requestId).execute().body();

    assertNotNull(enmResponseData);
    assertTrue(enmResponseData.getOutput().getElements().size() > 0);
  }
}
