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
package com.ericsson.oss.adc.emsnc.client.enm.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterClause {
  public static final String EVENT_DETECTION_TIMESTAMP = "eventDetectionTimestamp";
  public static final String EVENT_RECORD_TIMESTAMP = "eventRecordTimestamp";

  public static final String TARGET_NAME = "targetName";
  private String attrName;
  private Operator operator;
  private String attrValue;
  private List<String> attrValues;
}
