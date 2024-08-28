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
package com.ericsson.oss.adc.emsnc.wiremock;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class SubsystemsResponseTransformer extends ResponseTransformer {
  public static final String NAME = "subsystems-transformer";

  private final Handlebars handlebars = new Handlebars();

  private Supplier<List<SimulatedEnm>> enmListSupplier;

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public Response transform(
      Request request, Response response, FileSource files, Parameters parameters) {
    String body;

    try {
      Template subsystemsTemplate = handlebars.compile("/templates/subsystems.json");
      body = subsystemsTemplate.apply(enmListSupplier.get());
    } catch (IOException e) {
      log.error("Failed to generate subsystems response with Handlebars.", e);
      return Response.notConfigured();
    }

    HttpHeaders headers =
        response
            .getHeaders()
            .plus(new HttpHeader("Content-Length", Integer.toString(body.getBytes().length)));

    return Response.Builder.like(response).but().headers(headers).body(body).build();
  }

  @Override
  public String getName() {
    return NAME;
  }
}
