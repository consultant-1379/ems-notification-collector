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

import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmCommandResponseData;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmNodeData;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmOutputData;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EnmCommandOutputResponseTransformer extends ResponseTransformer {
  public static final String NAME = "enm-command-output-transformer";
  private final Supplier<Map<String, Integer>> targetsSupplier;

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Response transform(
      Request request, Response response, FileSource fileSource, Parameters parameters) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    String requestId =
        request
            .getAbsoluteUrl()
            .substring(0, request.getAbsoluteUrl().indexOf("/stream"))
            .substring(request.getAbsoluteUrl().indexOf("/st:") + 4);
    return Response.Builder.like(response)
        .but()
        .body(gson.toJson(createEnmCommandResponseData(requestId)))
        .build();
  }

  private EnmCommandResponseData createEnmCommandResponseData(String requestId) {
    Map<String, Integer> possibleTargets = targetsSupplier.get();
    EnmCommandResponseData enmCommandResponseData = new EnmCommandResponseData();
    Map.Entry<String, Integer> neTypeEntry = findNeType(requestId, possibleTargets);
    enmCommandResponseData.setCommand(
        "cmedit get * NetworkElement --netype=" + neTypeEntry.getKey());
    enmCommandResponseData.setResponseStatus("FETCHING");
    enmCommandResponseData.setV("2");
    enmCommandResponseData.setOutput(createEnmOutputData(neTypeEntry));
    return enmCommandResponseData;
  }

  private Map.Entry<String, Integer> findNeType(
      String requestId, Map<String, Integer> possibleTargets) {
    for (Map.Entry<String, Integer> neType : possibleTargets.entrySet()) {
      String otherUUid =
          UUID.nameUUIDFromBytes(neType.getKey().getBytes(StandardCharsets.UTF_8)).toString();
      if (requestId.equals(otherUUid)) {
        return neType;
      }
    }
    // wouldn't match any stub, impossible to get here
    return null;
  }

  private EnmOutputData createEnmOutputData(Map.Entry<String, Integer> neType) {
    EnmOutputData enmOutputData = new EnmOutputData();
    enmOutputData.setType("group");
    enmOutputData.setElements(createEnmNodeDataList(neType));
    return enmOutputData;
  }

  private ArrayList<EnmNodeData> createEnmNodeDataList(final Map.Entry<String, Integer> neType) {
    ArrayList<EnmNodeData> enmNodeDataList = new ArrayList<>();
    for (int i = 0; i < neType.getValue(); i++) {
      EnmNodeData enmNodeDataFdn = new EnmNodeData();
      enmNodeDataFdn.setType("text");
      enmNodeDataFdn.setValue(
          "FDN : NetworkElement="
              + neType.getKey()
              + UUID.nameUUIDFromBytes(
                  (Objects.hashCode(this) + (neType.getKey() + i))
                      .getBytes(StandardCharsets.UTF_8)));
      enmNodeDataList.add(enmNodeDataFdn);
    }
    return enmNodeDataList;
  }
}
