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
    LOGGER.debug("ConfigTracker:install({}, {})", filename, configs);
    updatePrevious();
    fileToConfig.put(filename, configs);
    configs.forEach(this::updateCurrent);
    final ConfigEvent event = diff();

    LOGGER.debug("ConfigTracker:install({}, {}) = {}", filename, configs, event);
    return event;
  }

  @Nullable
  public synchronized ConfigEvent update(String filename, Set<Config> configs) {
    LOGGER.debug("ConfigTracker:update({}, {})", filename, configs);
    updatePrevious();
    final Set<Config> old = fileToConfig.put(filename, configs);

    if (old != null) {
      old.stream().map(ConfigTracker::toKey).forEach(current::remove);
    }
    configs.forEach(this::updateCurrent);
    final ConfigEvent event = diff();

    LOGGER.debug("ConfigTracker:update({}, {}) = {}", filename, configs, event);
    return event;
  }

  @Nullable
  public synchronized ConfigEvent remove(String filename) {
    LOGGER.debug("ConfigTracker:remove({})", filename);
    updatePrevious();
    Set<Config> configs = fileToConfig.remove(filename);

    if (configs != null) {
      configs.stream().map(ConfigTracker::toKey).forEach(current::remove);
    }
    if (configs == null) {
      LOGGER.debug("ConfigTracker:remove({}) = null", filename);
      return null;
    }
    final ConfigEvent event =
        new ConfigEventImpl(Collections.emptySet(), Collections.emptySet(), configs);

    LOGGER.debug("ConfigTracker:remove({}) = {}", filename, event);
    return event;
  }

  @Nullable
  private ConfigEvent diff() {
    LOGGER.debug("ConfigTracker:diff()");
    MapDifference<String, Config> diff = Maps.difference(previous, current);

    Map<String, MapDifference.ValueDifference<Config>> entriesDiffering = diff.entriesDiffering();
    Set<Config> updatedConfigs =
        entriesDiffering.values().stream().map(e -> e.rightValue()).collect(Collectors.toSet());

    Map<String, Config> entriesOnlyOnRight = diff.entriesOnlyOnRight();
    Set<Config> addedConfigs = entriesOnlyOnRight.values().stream().collect(Collectors.toSet());

    Map<String, Config> entriesOnlyOnLeft = diff.entriesOnlyOnLeft();
    Set<Config> removedConfigs = entriesOnlyOnLeft.values().stream().collect(Collectors.toSet());
    final ConfigEvent event;

    if (addedConfigs.isEmpty() && updatedConfigs.isEmpty() && removedConfigs.isEmpty()) {
      event = null;
    } else {
      event = new ConfigEventImpl(addedConfigs, updatedConfigs, removedConfigs);
    }
    LOGGER.debug("ConfigTracker:diff() = {}", event);
    return event;
  }

  private void updatePrevious() {
    LOGGER.debug("ConfigTracker:updatePrevious()");
    this.previous.clear();
    this.previous.putAll(this.current);
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
    LOGGER.debug("ConfigTracker:get({})", type);
    final Optional<T> cfg =
        current.values().stream().filter(type::isInstance).map(type::cast).findFirst();

    LOGGER.debug("ConfigTracker:get({}) = {}", type, cfg);
    return cfg;
  }

  public <T extends ConfigGroup> Optional<T> get(Class<T> type, String id) {
    LOGGER.debug("ConfigTracker:get({}, {})", type, id);
    final Optional<T> cfg = configs(type).filter(c -> c.getId().equals(id)).findFirst();

    LOGGER.debug("ConfigTracker:get({}, {}) = {}", type, id, cfg);
    return cfg;
  }

  public <T extends ConfigGroup> Stream<T> configs(Class<T> type) {
    LOGGER.debug("ConfigTracker:configs({})", type);
    return current
        .values()
        .stream()
        .filter(type::isInstance)
        .map(type::cast)
        .peek(cfg -> LOGGER.debug("ConfigTracker:configs({}) + {}", type, cfg));
  }
}
