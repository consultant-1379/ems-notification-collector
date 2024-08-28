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

import java.net.URI;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class EmsCredentials {

  private Credentials credentials = new Credentials();

  public EmsCredentials(String name, String username, String password, URI location) {
    credentials.setName(name);
    credentials.setUsername(username);
    credentials.setPassword(password);
    credentials.setLocation(location);
  }

  /** Get Authentication username. */
  public String getUsername() {
    try {
      return getCredentials().getUsername();
    } catch (Exception e) {
      log.error("Error initialising credentials, could not get username", e);
      throw new CredentialsNotInitializedException();
    }
  }

  /** Get Authentication password. */
  public String getPassword() {
    try {
      return getCredentials().getPassword();
    } catch (Exception e) {
      log.error("Error initialising credentials, could not get password", e);
      throw new CredentialsNotInitializedException();
    }
  }

  /** Get Authentication cookie name. */
  public String getCookieName() {
    try {
      return getCredentials().getCookieName();
    } catch (Exception e) {
      log.error("Error initialising credentials, could not get cooki name", e);
      throw new CredentialsNotInitializedException();
    }
  }

  /**
   * Get location.
   *
   * @return ENM location
   */
  public URI getLocation() {
    try {
      return getCredentials().getLocation();
    } catch (Exception e) {
      log.error("Error initialising credentials, could not get location", e);
      throw new CredentialsNotInitializedException();
    }
  }

  @Data
  public static class Credentials {

    private String name;
    private String username;
    @ToString.Exclude private String password;
    private URI location;
    private String cookieName = "iPlanetDirectoryPro";
  }

  // Run time error for no file been found
  public static class CredentialsNotInitializedException extends RuntimeException {

    public CredentialsNotInitializedException() {
      super(
          "Credentials were not initialised:"
              + " Please check Credential Service & directory provided");
    }
  }
}
