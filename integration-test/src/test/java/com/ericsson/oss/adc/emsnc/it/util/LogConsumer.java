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
package com.ericsson.oss.adc.emsnc.it.util;

import java.util.function.Consumer;
import org.testcontainers.containers.output.OutputFrame;

public class LogConsumer implements Consumer<OutputFrame> {

  private final String containerName;
  private String expectedMessage;
  private boolean expectedMessageAppeared = false;

  public LogConsumer(String containerName, String expectedMessage) {
    this(containerName);
    this.expectedMessage = expectedMessage;
  }

  public LogConsumer(String containerName) {
    this.containerName = containerName;
  }

  @Override
  public void accept(OutputFrame outputFrame) {
    String line = outputFrame.getUtf8String();
    expectedMessageAppeared =
        expectedMessage != null && (line.indexOf(expectedMessage) >= 0 || expectedMessageAppeared);
    System.out.print("[" + containerName + "]: " + line);
  }

  public boolean verifyMessageFromOutputAppeared() {
    return expectedMessageAppeared;
  }
}
