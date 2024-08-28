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
package com.ericsson.oss.adc.emsnc.tools;

import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.model.yang.InformationObjectClass;
import com.ericsson.oss.adc.emsnc.model.yang.InvalidOperationTypeException;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.EventMapperUtils;
import com.ericsson.oss.adc.emsnc.processing.data.serialization.InformationObjectClassJsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings("squid:S1943")
public class EventExampleGenerator {

  private static final String CM_UPDATE_EXAMPLE = "cm-update-event.json";
  private static final String CM_CREATE_EXAMPLE = "cm-create-event.json";
  private static final String YANG_CREATE_CONTAINER = "container-type-create.json";
  private static final String YANG_UPDATE_CONTAINER = "container-type-update.json";
  private static final String YANG_CREATE_LIST = "list-type-create.json";
  private static final String YANG_UPDATE_LIST = "list-type-update.json";
  private static final String SUB_SYSTEM_ID = "ENM77";

  private static final Gson gson =
      new GsonBuilder()
          .setPrettyPrinting()
          .disableHtmlEscaping()
          .registerTypeAdapter(
              InformationObjectClass.class, new InformationObjectClassJsonSerializer())
          .create();

  private static Event getExampleCmEvent(String operationType) throws IOException {

    InputStream in;

    if (Objects.equals(operationType, "UPDATE")) {
      in = EventExampleGenerator.class.getClassLoader().getResourceAsStream(CM_UPDATE_EXAMPLE);
    } else if (Objects.equals(operationType, "CREATE")) {
      in = EventExampleGenerator.class.getClassLoader().getResourceAsStream(CM_CREATE_EXAMPLE);
    } else {
      throw new InvalidOperationTypeException();
    }

    return gson.fromJson(readFromInputStream(in), Event.class);
  }

  private static String readFromInputStream(InputStream inputStream) throws IOException {

    StringBuilder resultStringBuilder = new StringBuilder();

    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        resultStringBuilder.append(line).append("\n");
      }
    }

    return resultStringBuilder.toString();
  }

  public static void main(String[] args) throws IOException {

    new File("docs/target/converted-events/").mkdirs();

    Event createEvent = getExampleCmEvent("CREATE");
    Event updateEvent = getExampleCmEvent("UPDATE");

    YangEvent yangEvent =
        EventMapperUtils.buildListTypeYangEvent(createEvent, SUB_SYSTEM_ID, "Router6675");

    try (FileWriter writer =
        new FileWriter(
            "docs/target/converted-events/" + YANG_CREATE_LIST, StandardCharsets.UTF_8)) {
      writer.write(gson.toJson(yangEvent));
    }

    yangEvent =
        EventMapperUtils.buildContainerTypeYangEvent(createEvent, SUB_SYSTEM_ID, "Router6675");

    try (FileWriter writer =
        new FileWriter(
            "docs/target/converted-events/" + YANG_CREATE_CONTAINER, StandardCharsets.UTF_8)) {
      writer.write(gson.toJson(yangEvent));
    }

    yangEvent = EventMapperUtils.buildListTypeYangEvent(updateEvent, SUB_SYSTEM_ID, "Router6675");

    try (FileWriter writer =
        new FileWriter(
            "docs/target/converted-events/" + YANG_UPDATE_LIST, StandardCharsets.UTF_8)) {
      writer.write(gson.toJson(yangEvent));
    }

    yangEvent =
        EventMapperUtils.buildContainerTypeYangEvent(updateEvent, SUB_SYSTEM_ID, "Router6675");

    try (FileWriter writer =
        new FileWriter(
            "docs/target/converted-events/" + YANG_UPDATE_CONTAINER, StandardCharsets.UTF_8)) {
      writer.write(gson.toJson(yangEvent));
    }
  }
}
