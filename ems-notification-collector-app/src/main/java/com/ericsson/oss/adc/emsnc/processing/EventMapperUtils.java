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
package com.ericsson.oss.adc.emsnc.processing;

import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.model.yang.CmHandleIdGenerationException;
import com.ericsson.oss.adc.emsnc.model.yang.ContainerTypeEdit;
import com.ericsson.oss.adc.emsnc.model.yang.Edit;
import com.ericsson.oss.adc.emsnc.model.yang.InformationObjectClass;
import com.ericsson.oss.adc.emsnc.model.yang.ListTypeEdit;
import com.ericsson.oss.adc.emsnc.model.yang.MissingManagedElementException;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.model.yang.YangPatchContainerTypeModule;
import com.ericsson.oss.adc.emsnc.model.yang.YangPatchListTypeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SuppressWarnings("java:S2583")
public class EventMapperUtils {

  private EventMapperUtils() {}

  public static YangEvent cmToYang(Event source, String name, String neType) {

    if (isYangModuleOfTypeList()) {
      return buildListTypeYangEvent(source, name, neType);
    } else {
      return buildContainerTypeYangEvent(source, name, neType);
    }
  }

  public static YangEvent buildContainerTypeYangEvent(
      Event source, String subsystem, String neType) {
    YangEvent target = new YangEvent();
    YangPatchContainerTypeModule module = new YangPatchContainerTypeModule();
    String cmHandle = generateCmHandle(source.getMoFDN(), subsystem);
    ContainerTypeEdit edit =
        (ContainerTypeEdit) setCommonAttributesOnEdit(source, new ContainerTypeEdit(), cmHandle);

    edit.setValue(getInformationObjectClass(source, edit.getOperation(), neType));
    target.setCmHandle(cmHandle);
    target.setModule(module);
    module.setPatchId(source.getId());

    module.getEdits().add(edit);

    return target;
  }

  public static YangEvent buildListTypeYangEvent(Event source, String subsystem, String neType) {
    YangEvent target = new YangEvent();
    YangPatchListTypeModule module = new YangPatchListTypeModule();
    String cmHandle = generateCmHandle(source.getMoFDN(), subsystem);
    ListTypeEdit edit =
        (ListTypeEdit) setCommonAttributesOnEdit(source, new ListTypeEdit(), cmHandle);

    InformationObjectClass informationObjectClass =
        getInformationObjectClass(source, edit.getOperation(), neType);

    edit.getValue()
        .put(
            source.getMoClass(),
            new ArrayList<>(Collections.singletonList(informationObjectClass)));

    target.setCmHandle(cmHandle);
    target.setModule(module);
    module.setPatchId(source.getId());

    module.getEdits().add(edit);

    return target;
  }

  // TODO this functionality will be provided by Model Service eventually
  private static String getInformationObjectClassIdPrefix(String neType) {
    switch (neType) {
      case "vDU":
      case "vCU-CP":
      case "vCU-UP":
      case "vRM":
        return "_3gpp-common-top";
      case "RadioNode":
        return "ericsson-enm-com-top";
      default:
        return null;
    }
  }

  private static Edit setCommonAttributesOnEdit(Event source, Edit edit, String cmHandle) {
    edit.setOperation(getOperationFromCMEvent(source));
    edit.setTarget(buildTarget(source, edit));
    edit.setEditId(buildEditId(source, cmHandle));
    return edit;
  }

  // TODO perform actual class yang model check
  private static boolean isYangModuleOfTypeList() {
    return true;
  }

  private static String getOperationFromCMEvent(Event source) {
    if (source.getOperationType().equalsIgnoreCase("UPDATE")) {
      return "replace";
    } else {
      return source.getOperationType().toLowerCase(Locale.ENGLISH);
    }
  }

  private static String buildEditId(Event source, String cmHandle) {
    return cmHandle + "-" + getMoClassId(source);
  }

  private static String buildTarget(Event source, Edit edit) {
    String target = "/" + getMoFdnWithoutPrefix(source.getMoFDN()).replace(",", "/");
    if (edit instanceof ListTypeEdit) {
      return target;
    } else {
      return target + "/attribute";
    }
  }

  private static String getMoClassId(Event source) {
    String[] splitMoFDN = source.getMoFDN().split("=");
    return splitMoFDN[splitMoFDN.length - 1];
  }

  private static InformationObjectClass getInformationObjectClass(
      Event source, String operation, String neType) {
    InformationObjectClass informationObjectClass = new InformationObjectClass();
    if (source.getNewAttributeValues() != null) {
      source
          .getNewAttributeValues()
          .forEach(
              newAttributeValue ->
                  newAttributeValue.forEach(
                      (key, value) -> informationObjectClass.getAttributes().put(key, value)));
    }

    if (!operation.equals("replace")) {
      informationObjectClass.setIdPrefix(getInformationObjectClassIdPrefix(neType));
      informationObjectClass.setId(getMoClassId(source));
    }

    return informationObjectClass;
  }

  private static String getShortMoFdn(String moFDN) {
    int position = moFDN.indexOf("ManagedElement");

    if (position < 0) {
      throw new MissingManagedElementException();
    }

    String[] splitMoFdn = moFDN.split(",");
    int length = 0;

    for (String s : splitMoFdn) {
      if (s.startsWith("ManagedElement")) {
        length = s.length();
      }
    }

    // cut off moFdn after ManagedElement part
    return moFDN.substring(0, position + length);
  }

  private static String getMoFdnWithoutPrefix(String moFDN) {
    int position = moFDN.lastIndexOf("ManagedElement");

    if (position < 0) {
      throw new MissingManagedElementException();
    }

    return moFDN.substring(position);
  }

  // TODO align to future standard generator algorithm
  public static String generateCmHandle(String moFDN, String subsystem) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(
          String.format("EricssonENMAdapter-%s-%s", subsystem, getShortMoFdn(moFDN))
              .getBytes(StandardCharsets.UTF_8));
      return DatatypeConverter.printHexBinary(md.digest()).toUpperCase();
    } catch (NoSuchAlgorithmException ex) {
      throw new CmHandleIdGenerationException(ex);
    }
  }
}
