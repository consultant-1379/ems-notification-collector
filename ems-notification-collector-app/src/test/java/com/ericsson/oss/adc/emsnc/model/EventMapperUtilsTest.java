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
package com.ericsson.oss.adc.emsnc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.model.yang.Edit;
import com.ericsson.oss.adc.emsnc.model.yang.InformationObjectClass;
import com.ericsson.oss.adc.emsnc.model.yang.ListTypeEdit;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.model.yang.YangPatchContainerTypeModule;
import com.ericsson.oss.adc.emsnc.model.yang.YangPatchListTypeModule;
import com.ericsson.oss.adc.emsnc.processing.EventMapperUtils;
import com.ericsson.oss.adc.emsnc.processing.data.serialization.InformationObjectClassJsonSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventMapperUtilsTest {

  private static final String SUB_SYSTEM_ID = "ENM77";

  private Gson gson;

  @BeforeEach
  public void beforeEach() {
    gson =
        new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(
                InformationObjectClass.class, new InformationObjectClassJsonSerializer())
            .create();
  }

  @Test
  void jsonToListTypeModelCreateOperation() {
    Event dummyCMEvent = getDummyCMEvent("CREATE");
    YangEvent yangEvent =
        EventMapperUtils.buildListTypeYangEvent(dummyCMEvent, SUB_SYSTEM_ID, "Router6675");
    YangPatchListTypeModule module = (YangPatchListTypeModule) yangEvent.getModule();
    Edit edit = module.getEdits().get(0);
    Map<String, ArrayList<InformationObjectClass>> value = module.getEdits().get(0).getValue();

    assertEquals("create", edit.getOperation());
    assertEquals("ceeea9b3-08b9-41b9-9747-752fbd11035e", module.getPatchId());
    assertTrue(edit.getEditId().endsWith("LTE64ERBS00042-8"));
    assertEquals("7013AF133EB328E17C7C01F0A486AE36", yangEvent.getCmHandle());
    assertTrue(value.containsKey("EUtranCellFDD"));

    InformationObjectClass informationObjectClass = value.get("EUtranCellFDD").get(0);

    assertNotNull(informationObjectClass);
    assertNull(informationObjectClass.getIdPrefix());
    assertEquals("LTE64ERBS00042-8", informationObjectClass.getId());
    assertEquals(4, informationObjectClass.getAttributes().size());
    assertEquals("CCP", informationObjectClass.getAttributes().get("platformType"));
  }

  @Test
  void jsonToListTypeModelUpdateOperation() {
    Event dummyCMEvent = getDummyCMEvent("UPDATE");
    YangEvent yangEvent =
        EventMapperUtils.buildListTypeYangEvent(dummyCMEvent, SUB_SYSTEM_ID, "vDU");

    YangPatchListTypeModule module = (YangPatchListTypeModule) yangEvent.getModule();
    ListTypeEdit edit = module.getEdits().get(0);
    Map<String, ArrayList<InformationObjectClass>> value = module.getEdits().get(0).getValue();
    InformationObjectClass informationObjectClass = value.get("EUtranCellFDD").get(0);

    assertNull(informationObjectClass.getIdPrefix());
    assertNull(informationObjectClass.getId());
    assertEquals("replace", edit.getOperation());
  }

  @Test
  void jsonToContainerTypeModelCreateOperation() {
    Event dummyCMEvent = getDummyCMEvent("CREATE");

    YangEvent yangEvent =
        EventMapperUtils.buildContainerTypeYangEvent(dummyCMEvent, SUB_SYSTEM_ID, "RadioNode");
    YangPatchContainerTypeModule module = (YangPatchContainerTypeModule) yangEvent.getModule();

    Edit edit = module.getEdits().get(0);
    InformationObjectClass informationObjectClass = module.getEdits().get(0).getValue();

    assertEquals("create", edit.getOperation());
    assertEquals("ceeea9b3-08b9-41b9-9747-752fbd11035e", module.getPatchId());
    assertTrue(edit.getEditId().endsWith("LTE64ERBS00042-8"));
    assertTrue(edit.getTarget().endsWith("/attribute"));
    assertEquals("7013AF133EB328E17C7C01F0A486AE36", yangEvent.getCmHandle());

    assertNotNull(informationObjectClass);
    assertEquals("ericsson-enm-com-top", informationObjectClass.getIdPrefix());
    assertEquals("LTE64ERBS00042-8", informationObjectClass.getId());
    assertEquals(4, informationObjectClass.getAttributes().size());
    assertEquals("CCP", informationObjectClass.getAttributes().get("platformType"));
  }

  @Test
  void testYangEventMapping_RadioNode() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "RadioNode"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("ericsson-enm-com-top:id"));
  }

  @Test
  void testYangEventMapping_vDU() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "vDU"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("_3gpp-common-top:id"));
  }

  @Test
  void testYangEventMapping_vCUCP() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "vCU-CP"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("_3gpp-common-top:id"));
  }

  @Test
  void testYangEventMapping_vCUUP() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "vCU-UP"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("_3gpp-common-top:id"));
  }

  @Test
  void testYangEventMapping_vRM() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "vRM"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("_3gpp-common-top:id"));
  }

  @Test
  void testYangEventMapping_Router6675() {
    String jsonString = gson.toJson(getDummyContainerTypeYangEvent("CREATE", "Router6675"));
    System.out.println(jsonString);
    assertTrue(jsonString.contains("\"id\":"));
  }

  private YangEvent getDummyContainerTypeYangEvent(String operationType, String neType) {
    return EventMapperUtils.buildContainerTypeYangEvent(
        getDummyCMEvent(operationType), SUB_SYSTEM_ID, neType);
  }

  private Event getDummyCMEvent(String operationType) {

    Event event = new Event();

    event.setId("ceeea9b3-08b9-41b9-9747-752fbd11035e");
    event.setOperationType(operationType);
    event.setMoFDN(
        "SubNetwork=ERBS-SUBNW-1,MeContext=ieatnetsimv5048-16_LTE64ERBS00042,ManagedElement=1,ENodeBFunction=1,EUtranCellFDD=LTE64ERBS00042-8");
    event.setMoClass("EUtranCellFDD");

    event.setNewAttributeValues(new ArrayList<>());

    Map<String, Object> newAttributeValue1 = new HashMap<>();
    newAttributeValue1.put("platformType", "CCP");

    Map<String, Object> newAttributeValue2 = new HashMap<>();
    newAttributeValue1.put("ossModelIdentity", "18.Q4-J.2.300");

    Map<String, Object> newAttributeValue3 = new HashMap<>();
    newAttributeValue1.put("ossPrefix", "MeContext=LTE02ERBS00001");

    Map<String, Object> newAttributeValue4 = new HashMap<>();
    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put("identity", "CXP102051/27");
    additionalProperties.put("revision", "R28J01");

    newAttributeValue4.put("neProductVersion", additionalProperties);

    event.getNewAttributeValues().add(newAttributeValue1);
    event.getNewAttributeValues().add(newAttributeValue2);
    event.getNewAttributeValues().add(newAttributeValue3);
    event.getNewAttributeValues().add(newAttributeValue4);

    return event;
  }
}
