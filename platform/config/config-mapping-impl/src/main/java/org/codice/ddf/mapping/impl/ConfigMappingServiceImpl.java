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
package org.codice.ddf.mapping.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigListener;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingEvent;
import org.codice.ddf.config.mapping.ConfigMappingEvent.Type;
import org.codice.ddf.config.mapping.ConfigMappingListener;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingServiceImpl implements ConfigMappingService, ConfigListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingServiceImpl.class);

  private final ConfigService config;

  private final SortedSet<ConfigMappingProvider> providers =
      Collections.synchronizedSortedSet(new TreeSet<>());

  private final List<ConfigMappingListener> listeners;

  private final Map<ConfigMapping.Id, ConfigMappingImpl> mappings = new ConcurrentHashMap<>();

  public ConfigMappingServiceImpl(ConfigService config, List<ConfigMappingListener> listeners) {
    this.config = config;
    this.listeners = listeners;
  }

  @Override
  public void bind(ConfigMappingProvider provider) {
    LOGGER.debug("ConfigMappingServiceImpl::bind({})", provider);
    // first check if it already bound as in such condition, we need to first unbind it and then
    // rebind it
    if (unbind0(provider)) {
      LOGGER.debug("rebound provider: {}", provider);
    } else {
      LOGGER.debug("bound provider: {}", provider);
    }
    providers.add(provider);
    // find config mapping that needs to be updated
    mappings.values().stream().filter(provider::canProvideFor).forEach(m -> bind(provider, m));
  }

  @Override
  public boolean unbind(ConfigMappingProvider provider) {
    LOGGER.debug("ConfigMappingServiceImpl::unbind({})", provider);
    if (unbind0(provider)) {
      LOGGER.debug("unbound provider: {}", provider);
      return true;
    }
    return false;
  }

  @Override
  public Optional<ConfigMapping> getMapping(String name) {
    LOGGER.debug("ConfigMappingServiceImpl::getMapping({})", name);
    return getMapping(ConfigMapping.Id.of(name));
  }

  @Override
  public Optional<ConfigMapping> getMapping(String name, String instance) {
    LOGGER.debug("ConfigMappingServiceImpl::getMapping({}, name)", name, instance);
    return getMapping(ConfigMapping.Id.of(name, instance));
  }

  @Override
  public Optional<ConfigMapping> getMapping(ConfigMapping.Id id) {
    LOGGER.debug("ConfigMappingServiceImpl::getMapping({})", id);
    return Optional.ofNullable(mappings.computeIfAbsent(id, this::newMapping))
        .filter(ConfigMappingImpl::isAvailable)
        .map(ConfigMapping.class::cast);
  }

  @Override
  public void configChanged(ConfigEvent event) {
    LOGGER.debug("ConfigMappingServiceImpl::configChanged({})", event);
    mappings
        .values()
        .stream()
        .filter(ConfigMappingImpl::isAvailable)
        .filter(m -> m.isAffectedBy(event))
        .forEach(this::notifyUpdated);
  }

  private boolean unbind0(ConfigMappingProvider provider) {
    if (providers.remove(provider)) {
      // find config mapping that needs to be updated
      mappings.forEach((id, m) -> unbind(provider, m));
      return true;
    }
    return false;
  }

  @Nullable
  private ConfigMappingImpl newMapping(ConfigMapping.Id id) {
    LOGGER.debug("ConfigMappingServiceImpl::newMapping({})", id);
    // search all registered providers to find those that supports the specified mapping
    final ConfigMappingImpl mapping =
        new ConfigMappingImpl(config, id, providers.stream().filter(p -> p.canProvideFor(id)));

    if (mapping.isAvailable()) {
      notify(Type.CREATED, mapping);
    }
    return mapping;
  }

  private void bind(ConfigMappingProvider provider, ConfigMappingImpl mapping) {
    final boolean wasAvailable = mapping.isAvailable();

    if (mapping.bind(provider)) {
      notify(!wasAvailable ? Type.CREATED : Type.UPDATED, mapping);
    }
  }

  private void unbind(ConfigMappingProvider provider, ConfigMappingImpl mapping) {
    if (mapping.unbind(provider)) {
      notify(mapping.isAvailable() ? Type.UPDATED : Type.REMOVED, mapping);
    }
  }

  private void notifyUpdated(ConfigMapping mapping) {
    notify(Type.UPDATED, mapping);
  }

  private void notify(ConfigMappingEvent.Type type, ConfigMapping mapping) {
    LOGGER.debug("ConfigMappingServiceImpl::notify({}, {})", type, mapping);
    final ConfigMappingEventImpl event = new ConfigMappingEventImpl(type, mapping);

    listeners.forEach(l -> l.mappingChanged(event));
  }
}
