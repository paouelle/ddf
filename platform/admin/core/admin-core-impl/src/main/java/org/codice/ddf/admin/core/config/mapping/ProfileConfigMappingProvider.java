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
package org.codice.ddf.admin.core.config.mapping;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.boon.Boon;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.model.NetworkProfileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileConfigMappingProvider implements ConfigMappingProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileConfigMappingProvider.class);

  private static final String CONFIGS_KEY = "configs";

  private final String profilesFilePath;

  private Map<String, Object> profiles;

  private Map<String, List<ConfigEntry>> configs = new HashMap<>();

  private Set<String> pids = new HashSet<>();

  public ProfileConfigMappingProvider(String profilesFilePath) {
    this.profilesFilePath = profilesFilePath;
  }

  public void init() {
    try (InputStream inputStream = new FileInputStream(profilesFilePath)) {
      String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      LOGGER.debug("profiles.json: {}", json);
      this.profiles = (Map<String, Object>) Boon.fromJson(json);
      LOGGER.debug("profiles: {}", this.profiles);
      processProfileConfigSections();
      LOGGER.debug("configs: {}", configs);
      LOGGER.debug("pids: {}", pids);
    } catch (IOException e) {
      LOGGER.debug(
          "Could not find {} during installation. Using default profile.", profilesFilePath, e);
      this.profiles = Collections.EMPTY_MAP;
    }
  }

  @Override
  public boolean isPartial() {
    return true;
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
    LOGGER.debug(
        "Received {}; can provide for: {}; returning: {}",
        id.getName(),
        pids.toString(),
        pids.contains(id.getName()));
    return pids.contains(id.getName());
  }

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService configService)
      throws ConfigMappingException {
    LOGGER.debug("Providing for: {}", id.getName());
    final Optional<NetworkProfileConfig> optionalNetworkProfileConfig =
        configService.get(NetworkProfileConfig.class);

    if (!optionalNetworkProfileConfig.isPresent()) {
      return Collections.EMPTY_MAP;
    }

    final NetworkProfileConfig networkProfileConfig = optionalNetworkProfileConfig.get();
    final String profile = networkProfileConfig.getProfile();
    final String version = networkProfileConfig.getVersion();
    LOGGER.debug("profile: {}", profile);
    LOGGER.debug("version: {}", version);

    final List<ConfigEntry> profileConfigs = configs.get(profile);
    LOGGER.debug("configs for profile [{}]: {}", profile, profileConfigs);
    final Optional<ConfigEntry> optConfigEntry =
        profileConfigs.stream().filter(c -> c.hasPid(id.getName())).findFirst();

    if (!optConfigEntry.isPresent()) {
      return Collections.EMPTY_MAP;
    }

    final ConfigEntry configEntry = optConfigEntry.get();
    final Map<String, Object> properties = configEntry.getProperties();
    LOGGER.debug("Providing mappings: {}", properties);
    return properties;
  }

  private void processProfileConfigSections() {
    profiles.entrySet().forEach(this::processProfileConfigs);
  }

  private void processProfileConfigs(Map.Entry<String, Object> profileEntry) {
    final String profile = profileEntry.getKey();
    LOGGER.debug("Process config sections for profile [{}].", profile);
    final List<Map<String, Object>> profileConfigs = getConfigsFromProfileEntry(profileEntry);
    LOGGER.debug("profile configs: {}", profileConfigs);
    final List<ConfigEntry> configEntries =
        profileConfigs.stream().map(ConfigEntry::new).collect(Collectors.toList());
    configs.put(profile, configEntries);
    this.pids.addAll(
        configs
            .values()
            .stream()
            .flatMap(Collection::stream)
            .map(ConfigEntry::getPid)
            .collect(Collectors.toSet()));
  }

  private List<Map<String, Object>> getConfigsFromProfileEntry(
      Map.Entry<String, Object> profileEntry) {
    final List<Map<String, Object>> profileConfigs =
        (List<Map<String, Object>>)
            ((Map<String, Object>) profileEntry.getValue()).get(CONFIGS_KEY);
    return profileConfigs;
  }

  private static class ConfigEntry {

    private static final String PID_KEY = "pid";

    private static final String PROPERTIES_KEY = "properties";

    private final String pid;

    private final Map<String, Object> properties;

    public ConfigEntry(Map<String, Object> map) {
      this.pid = (String) map.get(PID_KEY);
      this.properties = (Map<String, Object>) map.get(PROPERTIES_KEY);
    }

    public String getPid() {
      return pid;
    }

    public boolean hasPid(String pid) {
      return this.pid.equals(pid);
    }

    public Map<String, Object> getProperties() {
      return properties;
    }

    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("ConfigEntry: pid: ").append(pid).append("; properties: ").append(properties);
      return builder.toString();
    }
  }
}
