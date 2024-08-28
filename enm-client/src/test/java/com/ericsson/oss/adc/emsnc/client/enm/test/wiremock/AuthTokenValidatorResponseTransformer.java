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
package com.ericsson.oss.adc.emsnc.client.enm.test.wiremock;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthTokenValidatorResponseTransformer extends ResponseTransformer {
  public static final String NAME = "auth-token-validator-response-transformer";
  public static final String ERROR_MESSAGE =
      "{\"code\":\"FAILED\",\"message\":\"<title>OpenAM (Authentication Failed)</title><h3>Authentication failed.</h3>\"}";

  private final Supplier<Long> authTokenExpirationTimeSec;
  private final Supplier<Instant> authTokenInvalidation;

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Response transform(
      Request request, Response response, FileSource fileSource, Parameters parameters) {
    Cookie authCookie = request.getCookies().get(SimulatedEnm.ENM_COOKIE_NAME);
    if (authCookie != null && authCookie.values() != null && authCookie.values().get(0) != null) {
      Instant tokenCreationTime =
          Instant.parse(new String(Base64.getDecoder().decode(authCookie.values().get(0))));
      if (!tokenCreationTime.isBefore(Instant.now().minusSeconds(authTokenExpirationTimeSec.get()))
          && !tokenCreationTime.isBefore(authTokenInvalidation.get())) {
        return response;
      }
    }
    return Response.Builder.like(response).but().status(401).body(ERROR_MESSAGE).build();
  }
}
