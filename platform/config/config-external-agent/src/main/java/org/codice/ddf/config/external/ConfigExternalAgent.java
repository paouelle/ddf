/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.config.external;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.boon.json.JsonFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping.Id;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingService;
import org.codice.ddf.config.mapping.impl.ConfigMappingServiceImpl;
import org.codice.ddf.config.reader.impl.YamlConfigReaderImpl;
import org.codice.ddf.config.service.impl.ConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigExternalAgent {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigExternalAgent.class);

  static final Path DDF_DIR = Paths.get(System.getProperty("ddf.home"));

  static final ObjectMapper MAPPER = JsonFactory.create();

  private static final Path ETC_DIR = ConfigExternalAgent.DDF_DIR.resolve("etc");

  private static final Path CONFIG_DIR = ConfigExternalAgent.ETC_DIR.resolve("configs");

  private static final Path CONFIG_FILE = ConfigExternalAgent.ETC_DIR.resolve("external.json");

  private final List<ConfigExternalFile> files;

  public static void main(String[] args) throws Exception {
    new ConfigExternalAgent(args).start();
  }

  private final ConfigMappingService mapping;

  @VisibleForTesting
  ConfigExternalAgent(String[] args) throws Exception {
    LOGGER.debug("ConfigExternalAgent(" + Arrays.toString(args) + ")");
    // load the configuration file
    final Map<String, Object> map =
        ConfigExternalAgent.MAPPER
            .parser()
            .parseMap(FileUtils.readFileToString(CONFIG_FILE.toFile(), Charset.defaultCharset()));

    this.files =
        ((Map<String, List<Object>>) map.get("files"))
            .entrySet()
            .stream()
            .map(ConfigExternalFile::new)
            .collect(Collectors.toList());
    // initialize the framework
    final ConfigServiceImpl config =
        new ConfigServiceImpl(
            new YamlConfigReaderImpl(),
            Collections.emptyList(),
            Collections.singleton(ConfigExternalAgent.CONFIG_DIR.toString()));
    final ConfigMappingServiceImpl mapping =
        new ConfigMappingServiceImpl(config, Collections.emptyList());

    config.init();
    this.mapping = mapping;
    // bind the providers we need
    for (final String clazz : (List<String>) map.get("providers")) {
      mapping.bind((ConfigMappingProvider) Class.forName(clazz).newInstance());
    }
  }

  public void start() {
    LOGGER.debug("ConfigExternalAgent::start");
    files.forEach(f -> f.update(mapping));
  }
}

class TempProvider implements ConfigMappingProvider {
  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(Id id) {
    return id.getName().equals("users.attributes");
  }

  @Override
  public Map<String, Object> provide(Id id, ConfigService config) throws ConfigMappingException {
    final Map<String, Object> properties = new HashMap<>();

    properties.put(
        "bob",
        ImmutableMap.of(
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
            "bob@localhost.local"));
    properties.put(
        "admin",
        ImmutableMap.of(
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
            "admin@localhost.local"));
    properties.put(
        "localhost",
        ImmutableMap.of(
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
            "system@localhost.local"));
    return properties;
  }
}
