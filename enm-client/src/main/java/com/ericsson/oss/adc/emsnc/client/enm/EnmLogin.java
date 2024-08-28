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
package com.ericsson.oss.adc.emsnc.client.enm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import retrofit2.Response;

@Slf4j
public class EnmLogin {
  private EnmLogin() {
    // intentionally empty
  }

  public static String queryForAuthenticationToken(
      EmsCredentials emsCredentials, EnmClient enmClient) {

    String username = emsCredentials.getUsername();
    String password = emsCredentials.getPassword();

    Map<String, String> loginQuery = new HashMap<>();
    loginQuery.put("IDToken1", username);
    loginQuery.put("IDToken2", password);

    Optional<Response<ResponseEntity<String>>> response;
    try {
      response = Optional.of(enmClient.login(loginQuery).execute());
      if (response.get().code() != HttpStatus.FOUND.value()
          && response.get().code() != HttpStatus.OK.value()) {
        throw new HttpClientErrorException(
            Objects.requireNonNull(HttpStatus.resolve(response.get().code())));
      }
      String cookieName = emsCredentials.getCookieName();

      String token =
          response
              .orElseThrow(MissingAuthenticationTokenException::new)
              .headers()
              .toMultimap()
              .get(HttpHeaders.SET_COOKIE)
              .stream()
              .filter(t -> t.contains(cookieName))
              .findFirst()
              .map(cookie -> cookie.substring(cookieName.length() + 1))
              .orElseThrow(MissingAuthenticationTokenException::new);

      token = emsCredentials.getCookieName() + "=" + token;

      return token;
    } catch (IOException e) {
      log.error("Error authenticating", e);
      throw new NetworkException(e);
    }
  }
}
