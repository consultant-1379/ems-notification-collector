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

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

public class DockerExecUtil {
  private DockerExecUtil() {
    // intentionally empty
  }

  public static String executeDockerCommand(String[] command, GenericContainer container)
      throws InterruptedException {
    ExecCreateCmdResponse execCreateCmdResponse =
        container
            .getDockerClient()
            .execCreateCmd(container.getContainerId())
            .withAttachStdout(true)
            .withCmd(command)
            .exec();

    OutputCollectingAdapter adapter = new OutputCollectingAdapter();
    container
        .getDockerClient()
        .execStartCmd(execCreateCmdResponse.getId())
        .exec(adapter)
        .awaitCompletion(2, TimeUnit.MINUTES);

    return adapter.getOutput();
  }

  @Slf4j
  private static class OutputCollectingAdapter extends ResultCallback.Adapter<Frame> {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @SneakyThrows
    public void onNext(Frame frame) {
      if (frame != null) {
        switch (frame.getStreamType()) {
          case STDOUT:
          case RAW:
            this.stdout.write(frame.getPayload());
            this.stdout.flush();
            break;
          case STDERR:
            this.stderr.write(frame.getPayload());
            log.error(
                "Written to stderr: {}", new String(frame.getPayload(), StandardCharsets.UTF_8));
            this.stderr.flush();
            break;
          default:
            log.error(
                "unknown stream type: {} content: {}",
                frame.getStreamType(),
                new String(frame.getPayload(), StandardCharsets.UTF_8));
        }
      }
    }

    public String getOutput() {
      return stdout.toString();
    }
  }
}
