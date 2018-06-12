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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.codice.ddf.config.Config;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigInstance;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingImpl implements ConfigMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingImpl.class);

  // marker to indicate that a config mapping depends on all instances/ids of a given config type
  private static final Set<String> ALL = Collections.emptySet();

  private final ConfigService config;

  private final ConfigMapping.Id id;

  private final SortedSet<ConfigMappingProvider> providers;

  private final Map<Class<? extends Config>, Set<String>> dependents = new ConcurrentHashMap<>();

  public ConfigMappingImpl(
      ConfigService config, ConfigMapping.Id id, Stream<ConfigMappingProvider> providers) {
    this.config = new DependentConfigService(config);
    this.id = id;
    this.providers =
        Collections.synchronizedSortedSet(providers.collect(Collectors.toCollection(TreeSet::new)));
    resolve(); // first resolution to compute the initial dependents
  }

  @Override
  public Id getId() {
    return id;
  }

  /**
   * Checks if this config mapping currently has any providers for it.
   *
   * @return <code>true</code> if at least one provider is capable of providing mapped properties
   *     for this config mapping; <code>false</code> otherwise
   */
  public boolean isAvailable() {
    return !providers.isEmpty();
  }

  /**
   * Binds the specified provider as a new provider to use when resolving mapped properties.
   *
   * @param provider the new provider to use when resolving mapped properties
   * @return <code>true</code> if the provider wasn't already bound; <code>false</code> otherwise
   */
  boolean bind(ConfigMappingProvider provider) {
    final boolean bound = providers.add(provider);

    LOGGER.debug("ConfigMappingImpl[{}].bind({}) = {}", id, provider, bound);
    return bound;
  }

  /**
   * Unbinds the specified provider from this mapping such that it no longer participates in
   * resolving mapped properties.
   *
   * @param provider the provider to remove from this mapping
   * @return <code>true</code> if the provider was already bound; <code>false</code> otherwise
   */
  boolean unbind(ConfigMappingProvider provider) {
    final boolean unbound = providers.remove(provider);

    LOGGER.debug("ConfigMappingImpl[{}].unbind({}) = {}", id, provider, unbound);
    return unbound;
  }

  boolean isAffectedBy(ConfigEvent event) {
    return Stream.of(event.addedConfigs(), event.updatedConfigs(), event.removedConfigs())
        .flatMap(Function.identity())
        .anyMatch(this::isAffectedBy);
  }

  boolean isAffectedBy(Config config) {
    final Set<String> instances = dependents.get(config.getClass());

    if (instances == null) {
      LOGGER.debug(
          "ConfigMappingImpl[{}].isAffectedBy({}) = false; class not supported", id, config);
      return false;
    } else if (!(config instanceof ConfigInstance)) {
      LOGGER.debug("ConfigMappingImpl[{}].isAffectedBy({}) = true; class supported", id, config);
      return true;
    } else if (instances == ConfigMappingImpl.ALL) {
      LOGGER.debug(
          "ConfigMappingImpl[{}].isAffectedBy({}) = true; all instances supported", id, config);
      return true;
    }
    final String instanceId = ((ConfigInstance) config).getId();

    if (instances.contains(instanceId)) {
      LOGGER.debug(
          "ConfigMappingImpl[{}].isAffectedBy({}) = true; instance [{}] supported",
          id,
          config,
          instanceId);
      return true;
    }
    LOGGER.debug(
        "ConfigMappingImpl[{}].isAffectedBy({}) = false; instance [{}] not supported",
        id,
        config,
        instanceId);
    return false;
  }

  @Override
  public Map<String, Object> resolve() throws ConfigMappingException {
    final Map<String, Object> properties = new HashMap<>();

    synchronized (providers) {
      // process them from lowes priority to highest such that higher one can override
      providers.stream().map(p -> p.provide(id, config)).forEach(properties::putAll);
    }
    LOGGER.debug("ConfigMappingImpl[{}].resolve() = {}", id, properties);
    return properties;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ConfigMappingImpl) {
      return id.equals(((ConfigMappingImpl) obj).id);
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("ConfigMappingImpl[%s, providers=%s]", id, providers);
  }

  /**
   * Proxy config service class use to intercept config retrieval in order to help identify what
   * this config mapping depends on.
   */
  class DependentConfigService implements ConfigService {
    private final ConfigService config;

    DependentConfigService(ConfigService config) {
      this.config = config;
    }

    @Override
    public <T extends Config> Optional<T> get(Class<T> type) {
      // insert or replace the entry with an indicator that we depend on all instances for `type`
      dependents.put(type, ConfigMappingImpl.ALL);
      return config.get(type);
    }

    @Override
    public <T extends ConfigInstance> Optional<T> get(Class<T> type, String id) {
      // insert this specific id for the given type unless we already depend on all
      dependents.compute(
          type,
          (t, set) -> {
            if (set == null) {
              set = new ConcurrentSkipListSet<>();
              set.add(id);
            } else if (set != ConfigMappingImpl.ALL) {
              set.add(id);
            } // else - this type is dependent on ALL so nothing to do
            return set;
          });
      return config.get(type, id);
    }

    @Override
    public <T extends ConfigInstance> Stream<T> configs(Class<T> type) {
      // insert or replace the entry with an indicator that we depend on all instances for `type`
      dependents.put(type, ConfigMappingImpl.ALL);
      return config.configs(type);
    }
  }
}
