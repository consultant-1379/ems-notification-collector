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

import com.ericsson.oss.adc.emsnc.client.enm.EnmClientService;
import com.ericsson.oss.adc.emsnc.client.enm.LoginHandlingEnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmNodeData;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TargetResolvingService {

  private static final Pattern FDN_PATTERN = Pattern.compile("FDN : NetworkElement=(.*)");

  private final EnmClientService enmClientService;

  public List<String> findTargetNames(EnmInfo enmInfo, String neType) {
    LoginHandlingEnmClient enmClient = enmClientService.getEnmClient(enmInfo.toEmsCredentials());
    return enmClient.getNetworkElements(neType).stream()
        .map(EnmNodeData::getValue)
        .map(this::extractTargetName)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private String extractTargetName(String fdn) {
    Matcher matcher = FDN_PATTERN.matcher(fdn);
    if (matcher.matches()) {
      return matcher.group(1);
    } else {
      log.warn("Failed to extract targetName from FDN '{}', skipping this entry", fdn);
      return null;
    }
  }
}
