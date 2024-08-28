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
package com.ericsson.oss.adc.emsnc.tools;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class AssemblyGenerator {

  private static final String EMSNC_PREFIX = "emsnc-";

  public static void main(String[] args) throws IOException {
    // no validation, use with correct arguments
    // 0. target directory for the assembly descriptors
    // 1. location of the generated HTML docs
    if (args.length != 2) {
      throw new IllegalArgumentException("Takes exactly 2 arguments");
    }
    String assembliesDir = args[0];
    String generatedDocumentsDir = args[1];

    log.info(
        "Generating assemblies for {} HTML content to {}", generatedDocumentsDir, assembliesDir);
    final Yaml yaml = new Yaml();
    Map loadedYaml =
        yaml.loadAs(
            AssemblyGenerator.class
                .getClassLoader()
                .getResourceAsStream("marketplace_upload_config.yaml"),
            Map.class);
    List<Object> documents = (List<Object>) loadedYaml.get("documents");
    log.info("YAML descriptor has {} documents", documents.size());

    Handlebars hbs = new Handlebars();
    Template template = hbs.compile("assembly-template.xml");

    if (!documents.isEmpty()) {
      new File(assembliesDir).mkdirs();
    }

    for (Object docObject : documents) {
      Map docMap = (Map) docObject;

      String content = template.apply(createHandlebarsContext(docMap, generatedDocumentsDir));
      File assemblyFile =
          new File(assembliesDir + File.separator + getFileName(docMap) + "-assembly.xml");
      log.info("Generating {}", assemblyFile.getAbsolutePath());
      Files.write(assemblyFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static Map<String, Object> createHandlebarsContext(
      Map docMap, String generatedDocumentsDir) {
    Map<String, Object> context = new HashMap<>();

    context.put("data", docMap);
    context.put("docDir", generatedDocumentsDir);
    context.put("fileName", getFileName(docMap));

    return context;
  }

  private static String getFileName(Map docMap) {
    String filePath = String.valueOf(docMap.get("filepath"));
    return filePath.substring(
        filePath.indexOf(EMSNC_PREFIX) + EMSNC_PREFIX.length(), filePath.indexOf('.'));
  }
}
