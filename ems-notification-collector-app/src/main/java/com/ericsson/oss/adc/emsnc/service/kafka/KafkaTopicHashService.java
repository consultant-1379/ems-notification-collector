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
package com.ericsson.oss.adc.emsnc.service.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KafkaTopicHashService {

  public static final int EVENT_API_REQUEST_LIMIT = 25;

  public String getTopicNameByHashingTargetName(String targetName, int topicCount) {
    int topicNumber = Math.abs((targetName.hashCode() * 131) % topicCount);
    return generateTopicName(topicNumber);
  }

  public Map<String, List<String>> distributeTopicNames(List<String> targetNames, int topicCount) {
    Map<String, List<String>> topicMap =
        IntStream.range(0, topicCount)
            .mapToObj(KafkaTopicHashService::generateTopicName)
            .collect(Collectors.toMap(t -> t, t -> new ArrayList<String>()));
    log.debug("initialized topic map: {}", topicMap);
    targetNames.forEach(t -> topicMap.get(getTopicNameByHashingTargetName(t, topicCount)).add(t));
    log.debug(
        "distributed total {} targets to {} topics as {}",
        targetNames.size(),
        topicCount,
        topicMap.values().stream().map(List::size).collect(Collectors.toList()));
    // filter empty lists from the result
    return topicMap.entrySet().stream()
        .filter(e -> !e.getValue().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static String generateTopicName(int topicPostfix) {
    return KafkaTopicConfiguration.EMSNC_TOPIC_NAME_PREFIX + topicPostfix;
  }

  public List<List<String>> splitListToBatches(List<String> targetList) {
    return IntStream.rangeClosed(0, ((targetList.size() - 1) / EVENT_API_REQUEST_LIMIT))
        .mapToObj(
            i ->
                targetList.subList(
                    EVENT_API_REQUEST_LIMIT * i,
                    Math.min(EVENT_API_REQUEST_LIMIT * (i + 1), targetList.size())))
        .collect(Collectors.toList());
  }
}
