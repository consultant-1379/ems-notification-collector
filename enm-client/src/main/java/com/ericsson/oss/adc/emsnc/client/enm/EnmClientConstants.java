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

public class EnmClientConstants {

  private EnmClientConstants() {
    // intentionally empty
  }

  // ENM Resource URLs
  public static final String LOGIN = "login";
  public static final String LOGOUT = "logout";
  // ENM CM Resource URLs
  public static final String SERVER_CONFIG_EVENTS = "config-mgmt/event/events";
  public static final String SERVER_SCRIPTING_COMMAND = "/server-scripting/services/command";
  public static final String SERVER_SCRIPTING_GET = SERVER_SCRIPTING_COMMAND + "/output/";
  public static final String SERVER_SCRIPTING_WAIT_SUFFIX = "/stream?_wait_milli=1000";
}
