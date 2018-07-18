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
package org.codice.ddf.config.service.impl;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.codice.ddf.config.Config;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigSingleton;
import org.codice.ddf.config.service.eventing.impl.ConfigEventImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConfigTracker {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigTracker.class);

  private final Map<String, Config> previous = Maps.newHashMap();

  private final Map<String, Config> current = Maps.newHashMap();

  private final Map<String, Set<Config>> fileToConfig = Maps.newHashMap();

  @Nullable
  public synchronized ConfigEvent install(String filename, Set<Config> configs) {
    LOGGER.error("##### Start ConfigServiceImpl::install");
    LOGGER.error("##### Received configs: {}", configs);
    fileToConfig.put(filename, configs);
    updatePrevious();
    configs.forEach(this::updateCurrent);
    LOGGER.error("##### End ConfigServiceImpl::install");
    return diff();
  }

  @Nullable
  public synchronized ConfigEvent update(String filename, Set<Config> configs) {
    LOGGER.error("##### Start ConfigServiceImpl::update");
    LOGGER.error("##### Received configs: {}", configs);
    updatePrevious();
    processRemoval(filename);
    configs.forEach(this::updateCurrent);
    LOGGER.error("##### End ConfigServiceImpl::update");
    return diff();
  }

  @Nullable
  public synchronized ConfigEvent remove(String filename) {
    LOGGER.error("##### Start ConfigServiceImpl::remove");
    updatePrevious();
    final Set<Config> configs = processRemoval(filename);

    LOGGER.error("##### End ConfigServiceImpl::remove");
    if (configs == null) {
      return null;
    }
    return new ConfigEventImpl(Collections.emptySet(), Collections.emptySet(), configs);
  }

  @Nullable
  private Set<Config> processRemoval(String filename) {
    Set<Config> configs = fileToConfig.remove(filename);

    if (configs != null) {
      configs.stream().map(ConfigTracker::toKey).forEach(current::remove);
    }
    return configs;
  }

  @Nullable
  private ConfigEvent diff() {
    LOGGER.error("##### Start ConfigServiceImpl::diff");
    MapDifference<String, Config> diff = Maps.difference(previous, current);

    Map<String, MapDifference.ValueDifference<Config>> entriesDiffering = diff.entriesDiffering();
    Set<Config> updatedConfigs =
        entriesDiffering.values().stream().map(e -> e.rightValue()).collect(Collectors.toSet());

    Map<String, Config> entriesOnlyOnRight = diff.entriesOnlyOnRight();
    Set<Config> addedConfigs = entriesOnlyOnRight.values().stream().collect(Collectors.toSet());

    Map<String, Config> entriesOnlyOnLeft = diff.entriesOnlyOnLeft();
    Set<Config> removedConfigs = entriesOnlyOnLeft.values().stream().collect(Collectors.toSet());

    LOGGER.error("##### End ConfigServiceImpl::diff");
    if (addedConfigs.isEmpty() && updatedConfigs.isEmpty() && removedConfigs.isEmpty()) {
      return null;
    }
    return new ConfigEventImpl(addedConfigs, updatedConfigs, removedConfigs);
  }

  private void updatePrevious() {
    LOGGER.error("##### Start ConfigServiceImpl::updatePrevious()");
    this.previous.clear();
    this.previous.putAll(this.current);
    LOGGER.error("##### End ConfigServiceImpl::updatePrevious()");
  }

  private void updateCurrent(Config c) {
    current.put(ConfigTracker.toKey(c), c);
  }

  private static String toKey(Config config) {
    final String type = config.getType().getName();

    if (config instanceof ConfigGroup) {
      return type + "-" + ((ConfigGroup) config).getId();
    }
    return type;
  }

  public <T extends ConfigSingleton> Optional<T> get(Class<T> type) {
    LOGGER.error("##### ConfigServiceImpl::get(type)");
    return current.values().stream().filter(type::isInstance).map(type::cast).findFirst();
  }

  public <T extends ConfigGroup> Optional<T> get(Class<T> type, String id) {
    LOGGER.error("##### ConfigServiceImpl::get(type, id)");
    return configs(type).filter(c -> c.getId().equals(id)).findFirst();
  }

  public <T extends ConfigGroup> Stream<T> configs(Class<T> type) {
    LOGGER.error("##### ConfigServiceImpl::configs(type)");
    return current.values().stream().filter(type::isInstance).map(type::cast);
  }
}
