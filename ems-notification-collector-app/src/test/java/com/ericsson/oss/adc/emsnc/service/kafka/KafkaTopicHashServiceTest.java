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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {KafkaTopicHashService.class})
public class KafkaTopicHashServiceTest {

  public static final int MAX_TOPIC_COUNT = 30;
  public static final int MAX_GENERATED_TARGETNAMES = 15362;
  public static final int MAX_TOLERATED_DISPERSION = 40;

  @Value("${emsnc.kafka.emsnc-internal.topic-count}")
  private int topicCount;

  private static String TARGETNAME_SAMPLE_SRC = "service/kafka/allnetworkelements.txt";

  @Autowired private KafkaTopicHashService kafkaTopicHashService;

  @Test
  public void testHashingAlgorithmWithTopicNumberInterval() {
    List<String> targetNames = loadTargetNamesFromResource(TARGETNAME_SAMPLE_SRC);
    testTargetNameHashing(targetNames);
  }

  @Test
  public void testHashingAlgorithmWithTopicNumberIntervalWithGeneratedTargetNames() {
    List<String> targetNames = getDummyUUIDTargetNames(MAX_GENERATED_TARGETNAMES);
    testTargetNameHashing(targetNames);
  }

  @Test
  public void testHashingAlgorithmDistribution() {
    Map<String, Long> topicSize =
        loadTargetNamesFromResource(TARGETNAME_SAMPLE_SRC).stream()
            .map(t -> kafkaTopicHashService.getTopicNameByHashingTargetName(t, topicCount))
            .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

    Long min = Collections.min(topicSize.values());
    Double avg = (double) topicSize.values().stream().reduce((long) 0, Long::sum) / topicCount;
    Long max = Collections.max(topicSize.values());

    double dispersion = (max - min) * 100 / avg;

    log.info("_____________Min: " + min + ", max: " + max + " Diff: " + (max - min));
    log.info("_____________AVG: " + avg);
    log.info("_____________Dispersion: " + dispersion);

    Assertions.assertTrue(
        dispersion < MAX_TOLERATED_DISPERSION,
        "topicCount: " + topicCount + ", Dispersion: " + dispersion);
  }

  @Test
  public void testDistributeTopicNames() {
    List<String> targetNames = loadTargetNamesFromResource(TARGETNAME_SAMPLE_SRC);
    Map<String, List<String>> distributedTargetNames =
        kafkaTopicHashService.distributeTopicNames(targetNames, topicCount);

    long count = distributedTargetNames.values().stream().flatMap(Collection::stream).count();

    Assertions.assertEquals(targetNames.size(), count);
  }

  @Test
  public void testDistributeTopicNamesWithoutEmptyTopics() {
    List<String> targetNames = Arrays.asList("A", "B", "C");
    Map<String, List<String>> distributedTargetNames =
        kafkaTopicHashService.distributeTopicNames(targetNames, topicCount);

    long count = distributedTargetNames.values().stream().flatMap(Collection::stream).count();

    Assertions.assertEquals(targetNames.size(), count);

    distributedTargetNames
        .values()
        .forEach(
            t -> {
              Assertions.assertFalse(t.isEmpty());
            });
  }

  @Test
  public void testSplitListToBatches() {
    int generatedTargetNameCount = 420;
    List<String> targetNames = getDummyUUIDTargetNames(generatedTargetNameCount);
    List<List<String>> splittedList = kafkaTopicHashService.splitListToBatches(targetNames);

    if (generatedTargetNameCount % KafkaTopicHashService.EVENT_API_REQUEST_LIMIT == 0) {
      Assertions.assertEquals(
          generatedTargetNameCount / KafkaTopicHashService.EVENT_API_REQUEST_LIMIT,
          splittedList.size());
      splittedList.forEach(
          batch -> {
            Assertions.assertEquals(KafkaTopicHashService.EVENT_API_REQUEST_LIMIT, batch.size());
          });
    } else {
      Assertions.assertEquals(
          splittedList.size(),
          generatedTargetNameCount / KafkaTopicHashService.EVENT_API_REQUEST_LIMIT + 1);
      for (int i = 0; i < splittedList.size() - 1; i++) {
        Assertions.assertEquals(
            KafkaTopicHashService.EVENT_API_REQUEST_LIMIT, splittedList.get(i).size());
      }
      Assertions.assertEquals(
          generatedTargetNameCount % KafkaTopicHashService.EVENT_API_REQUEST_LIMIT,
          splittedList.get(splittedList.size() - 1).size());
    }
  }

  private void testTargetNameHashing(List<String> targetNames) {
    for (int topicCount = 1; topicCount < MAX_TOPIC_COUNT; topicCount++) {

      List<String> hashedTargetNames = new ArrayList<>();

      for (String targetName : targetNames) {
        hashedTargetNames.add(
            kafkaTopicHashService.getTopicNameByHashingTargetName(targetName, topicCount));
      }

      Map<String, Long> topicSize =
          hashedTargetNames.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));

      double dispersion = getDispersion(topicCount, topicSize);

      Assertions.assertTrue(
          dispersion < MAX_TOLERATED_DISPERSION,
          "topicCount: " + topicCount + ", Dispersion: " + dispersion);
    }
  }

  private List<String> getDummyUUIDTargetNames(int count) {
    List<String> targetNames = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      targetNames.add(UUID.randomUUID().toString());
    }
    return targetNames;
  }

  private double getDispersion(int topicCount, Map<String, Long> topicSize) {
    Long min = Collections.min(topicSize.values());
    Double avg = (double) topicSize.values().stream().reduce((long) 0, Long::sum) / topicCount;
    Long max = Collections.max(topicSize.values());

    return (max - min) * 100 / avg;
  }

  private List<String> loadTargetNamesFromResource(String resource) {
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> targetNames = new ArrayList<>();
    try (InputStream inputStream = classLoader.getResourceAsStream(resource);
        InputStreamReader streamReader =
            new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader)) {

      String line;
      while ((line = reader.readLine()) != null) {
        targetNames.add(line);
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
    return targetNames;
  }
}
