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

import com.google.gson.annotations.SerializedName;

public enum Operator {
  @SerializedName("eq")
  EQ,
  @SerializedName("neq")
  NEQ,
  @SerializedName("lt")
  LT,
  @SerializedName("gt")
  GT,
  @SerializedName("lte")
  LTE,
  @SerializedName("gte")
  GTE,
  @SerializedName("contains")
  CONTAINS,
  @SerializedName("not_contains")
  NOT_CONTAINS;
}
