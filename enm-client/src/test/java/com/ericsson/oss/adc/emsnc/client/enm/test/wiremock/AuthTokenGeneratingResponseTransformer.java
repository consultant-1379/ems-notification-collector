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
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class AuthTokenGeneratingResponseTransformer extends ResponseTransformer {

  public static final String NAME = "auth-token-generating-response-transformer";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public Response transform(
      Request request, Response response, FileSource fileSource, Parameters parameters) {
    HttpHeaders extendedHeaders =
        response
            .getHeaders()
            .plus(
                new HttpHeader(
                    "Set-Cookie",
                    SimulatedEnm.ENM_COOKIE_NAME
                        + "="
                        + Base64.getEncoder()
                            .encodeToString(
                                Instant.now().toString().getBytes(StandardCharsets.UTF_8))));
    return Response.Builder.like(response).but().headers(extendedHeaders).build();
  }
}
