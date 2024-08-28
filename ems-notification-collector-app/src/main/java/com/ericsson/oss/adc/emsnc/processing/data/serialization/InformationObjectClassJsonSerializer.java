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
package com.ericsson.oss.adc.emsnc.processing.data.serialization;

import com.ericsson.oss.adc.emsnc.model.yang.InformationObjectClass;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class InformationObjectClassJsonSerializer
    implements JsonSerializer<InformationObjectClass> {

  public static final String ID_PROPERTY = "id";

  private final Gson gson = new Gson();

  @Override
  public JsonElement serialize(
      InformationObjectClass source, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonObject jsonObject = new JsonObject();
    if (source.getIdPrefix() != null) {
      jsonObject.addProperty(getFullId(source.getIdPrefix()), source.getId());
    } else {
      jsonObject.addProperty(ID_PROPERTY, source.getId());
    }
    jsonObject.add("attributes", gson.toJsonTree(source.getAttributes()));
    return jsonObject;
  }

  private String getFullId(String idPrefix) {
    return idPrefix + ":" + ID_PROPERTY;
  }
}
