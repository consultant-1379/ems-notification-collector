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
package com.ericsson.oss.adc.emsnc.service.kafka.serialization;

import com.ericsson.oss.adc.emsnc.model.yang.InformationObjectClass;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.data.serialization.InformationObjectClassJsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Serializer;

public class YangEventSerializer implements Serializer<YangEvent> {

  private final Gson gson =
      new GsonBuilder()
          .disableHtmlEscaping()
          .registerTypeAdapter(
              InformationObjectClass.class, new InformationObjectClassJsonSerializer())
          .create();

  @Override
  public byte[] serialize(String topic, YangEvent data) {
    return gson.toJson(data).getBytes(StandardCharsets.UTF_8);
  }
}
