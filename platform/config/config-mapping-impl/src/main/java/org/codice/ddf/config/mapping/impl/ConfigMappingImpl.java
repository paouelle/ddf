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
package org.codice.ddf.config.mapping.impl;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.codice.ddf.config.Config;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.ConfigSingleton;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingEvent;
import org.codice.ddf.config.mapping.ConfigMappingEvent.Type;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingImpl implements ConfigMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingImpl.class);

  private static final String CONFIG_MAPPING_IMPL_RECOMPUTE_RESULT =
      "ConfigMappingImpl[{}].recompute() provider [{}, partial={}] = {}";

  // marker to indicate that a config mapping depends on all instances/ids of a given config type
  private static final Set<String> ALL = Collections.emptySet();

  // marker to representing a fake instance id representing any instances
  private static final String ANY = "*";

  private final ConfigMappingServiceImpl service;

  private final ConfigService config;

  private final ConfigMapping.Id id;

  private final Object lock = new Object();

  private final Set<ConfigMappingProvider> providers;

  private ConfigMappingEvent.Type state = ConfigMappingEvent.Type.REMOVED;

  private final Map<String, Object> properties = new HashMap<>();

  private RuntimeException error = null;

  // keyed by config type with corresponding set of instances or ALL if not a group or dependent on
  // all instances
  private final Map<Class<? extends Config>, Set<String>> dependents = new ConcurrentHashMap<>();

  public ConfigMappingImpl(
      ConfigMappingServiceImpl service,
      ConfigService config,
      ConfigMapping.Id id,
      Stream<ConfigMappingProvider> providers) {
    this.service = service;
    this.config = new DependentConfigService(config);
    this.id = id;
    this.providers = providers.collect(Collectors.toCollection(TreeSet::new));
    // first resolution to compute the initial dependents, use the ANY instance if none defined
    // just in case the providers are referencing it. This will be useful for providers capable of
    // providing for any instances
    final ConfigMapping.Id tempId =
        ConfigMapping.Id.of(id.getName(), id.getInstance().orElse(ConfigMappingImpl.ANY));

    for (final ConfigMappingProvider provider : this.providers) {
      try {
        provider.provide(tempId, config);
      } catch (Exception e) { // ignore
      }
    }
    // finally, do another pass where we actually compute the properties and state
    recompute();
  }

  @Override
  public Id getId() {
    return id;
  }

  /**
   * Gets the current state for this configuration mapping.
   *
   * @return the current state for this config mapping
   */
  public Type getState() {
    synchronized (lock) {
      return state;
    }
  }

  /**
   * Checks if this config mapping currently has any providers capable of providing for it.
   *
   * @return <code>true</code> if at least one provider is capable of providing all mapped
   *     properties for this config mapping; <code>false</code> otherwise
   */
  public boolean isAvailable() {
    synchronized (lock) {
      return state != Type.REMOVED;
    }
  }

  /**
   * Recomputes this configuration mapping.
   *
   * @return the new state for this config mapping or <code>null</code> if it hasn't changed
   */
  @Nullable
  public Type recompute() {
    synchronized (lock) {
      boolean complete = false;
      final RuntimeException oldError = error;
      final Type oldState = state;

      // reset cached results
      properties.clear();
      this.error = null;
      // process them from lowest priority to highest such that higher one can override
      for (final ConfigMappingProvider provider : providers) {
        try {
          // NOTE: Should we first clear the accumulated properties so far if this provider can
          // provide all?
          final Map<String, Object> props = provider.provide(id, config);

          LOGGER.debug(
              ConfigMappingImpl.CONFIG_MAPPING_IMPL_RECOMPUTE_RESULT,
              id,
              provider,
              provider.isPartial(),
              props);
          properties.putAll(props);
          if (!provider.isPartial()) {
            // clear whatever error was recorded so far since we were able to provide all
            // and continue on (possibly accumulating more errors)
            this.error = null;
            complete = true;
          }
        } catch (ConfigMappingUnavailableException e) {
          // indication that this mapping should be reported as unavailable so force to not complete
          // and break out so the state be set to REMOVED
          complete = false;
          LOGGER.debug(
              ConfigMappingImpl.CONFIG_MAPPING_IMPL_RECOMPUTE_RESULT,
              id,
              provider,
              provider.isPartial(),
              e,
              e);
          break;
        } catch (RuntimeException e) {
          // record this exception as the error but continue in case we encounter another
          // non-partial provider which would reset everything
          if (error == null) { // keep only the first errors
            this.error = e;
          }
          if (!provider.isPartial()) {
            complete = true;
          }
          LOGGER.debug(
              ConfigMappingImpl.CONFIG_MAPPING_IMPL_RECOMPUTE_RESULT,
              id,
              provider,
              provider.isPartial(),
              e,
              e);
        }
      }
      // we should have at least 1 non partial provider to be available, otherwise we report REMOVED
      if (!complete) {
        this.state = Type.REMOVED;
        properties.clear();
        this.error = null;
      } else if ((oldError == null) || (error == null)) {
        this.state = (state == Type.REMOVED) ? Type.CREATED : Type.UPDATED;
      } // else - don't change the state if we had an error before and we still have an error now
      final Type result = (oldState != state) ? state : null;

      LOGGER.debug(
          "ConfigMappingImpl[{}].recompute() old = {}, new = {}, returning {}",
          id,
          oldState,
          state,
          result);
      return result;
    }
  }

  /**
   * Binds the specified provider as a new provider to use when resolving mapped properties.
   *
   * @param provider the new provider to use when resolving mapped properties
   * @return the new state for this config mapping or <code>null</code> if it hasn't changed
   */
  Type bind(ConfigMappingProvider provider) {
    synchronized (lock) {
      final boolean bound = providers.add(provider);
      final Type result = bound ? recompute() : null;

      LOGGER.debug("ConfigMappingImpl[{}].bind({}) = {}", id, provider, result);
      return result;
    }
  }

  /**
   * Rebinds an existing provider with a new one to this mapping.
   *
   * @param old the old provider to be unbound
   * @param provider the new provider to be bound (if it can provide for this mapping)
   * @return the new state for this config mapping or <code>null</code> if it hasn't changed
   */
  Type rebind(ConfigMappingProvider old, ConfigMappingProvider provider) {
    synchronized (lock) {
      boolean updated = providers.remove(old);

      if (updated) {
        LOGGER.debug("ConfigMappingImpl[{}].rebind({}, {}) - unbound", id, old, provider);
        dependents.clear(); // we got to start fresh since we removed a provider
      }
      if (provider.canProvideFor(id)) {
        LOGGER.debug("ConfigMappingImpl[{}].rebind({}, {}) - bound", id, old, provider);
        providers.add(provider);
        updated = true;
      }
      final Type result = updated ? recompute() : null;

      LOGGER.debug("ConfigMappingImpl[{}].rebind({}) = {}", id, provider, result);
      return result;
    }
  }

  /**
   * Unbinds the specified provider from this mapping such that it no longer participates in
   * resolving mapped properties.
   *
   * @param provider the provider to remove from this mapping
   * @return the new state for this config mapping or <code>null</code> if it hasn't changed
   */
  Type unbind(ConfigMappingProvider provider) {
    synchronized (lock) {
      final boolean unbound = providers.remove(provider);
      final Type result;

      if (unbound) {
        dependents.clear(); // we got to start fresh since we removed a provider
        LOGGER.debug("ConfigMappingImpl[{}].unbind({})", id, provider);
        result = recompute();
      } else {
        result = null;
      }
      LOGGER.debug("ConfigMappingImpl[{}].unbind({}) = {}", id, provider, result);
      return result;
    }
  }

  boolean isAffectedBy(ConfigEvent event) {
    final boolean affected =
        Stream.of(event.addedConfigs(), event.updatedConfigs(), event.removedConfigs())
            .flatMap(Function.identity())
            .anyMatch(this::isAffectedBy);

    LOGGER.debug("ConfigMappingImpl[{}].isAffectedBy({}) = {}", id, event, affected);
    return affected;
  }

  private boolean isAffectedBy(Config config) {
    final Set<String> instances = dependents.get(config.getType());

    if (instances == null) {
      LOGGER.debug(
          "ConfigMappingImpl[{}].isAffectedBy({}) = false; type not supported", id, config);
      return false;
    } else if (!(config instanceof ConfigGroup)) {
      LOGGER.debug("ConfigMappingImpl[{}].isAffectedBy({}) = true; type supported", id, config);
      return true;
    } else if (instances == ConfigMappingImpl.ALL) { // identity check
      LOGGER.debug(
          "ConfigMappingImpl[{}].isAffectedBy({}) = true; all instances supported", id, config);
      return true;
    }
    final String instanceId = ((ConfigGroup) config).getId();

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
    synchronized (lock) {
      if (error != null) {
        // even though we cached that it failed on the last computation and even though nothing has
        // changed since, we will still call recompute(), such that the error we get has a proper
        // stack trace in it when we throw it back
        // notify in case something changed - although nothing should since we recompute whenever
        // a provider or a dependent config object is updated, added, or removed
        service.notify(recompute(), this);
        if (error != null) { // should always be true as stated above but just in case
          // we do not want the stack trace here
          LOGGER.debug("ConfigMappingImpl[{}].resolve() = {}", id, error.toString());
          throw error;
        }
      }
      if (state == Type.REMOVED) {
        LOGGER.debug("ConfigMappingImpl[{}].resolve() = [] as state is REMOVED", id);
        return Collections.emptyMap();
      } else {
        LOGGER.debug("ConfigMappingImpl[{}].resolve() = {}", id, properties);
      }
      // got to deep clone this map to make sure changes done by the client to it are not reflected
      // in our cached version
      return ConfigMappingImpl.deepClone(properties);
    }
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
    return String.format("ConfigMappingImpl[%s, state=%s, providers=%s]", id, state, providers);
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public static <T> T deepClone(@Nullable T obj) {
    if (obj == null) {
      return null;
    }
    final Class<?> clazz = obj.getClass();

    if (clazz.isArray()) {
      final int len = Array.getLength(obj);
      final T array = (T) Array.newInstance(clazz.getComponentType(), len);

      for (int i = 0; i < len; i++) {
        Array.set(array, i, ConfigMappingImpl.deepClone(Array.get(obj, i)));
      }
      return array;
    } else if (obj instanceof Map) {
      final Map map;

      if (obj instanceof ConcurrentHashMap) {
        map = new ConcurrentHashMap<>();
      } else if (obj instanceof NavigableMap) {
        map = new TreeMap<>();
      } else {
        map = new HashMap<>();
      }
      // streams collector don't support null values :-(
      ((Map) obj).forEach((k, v) -> map.put(k, ConfigMappingImpl.deepClone(v)));
      return (T) map;
    } else if (obj instanceof Set) {
      final Supplier creator;

      if (obj instanceof LinkedHashSet) {
        creator = LinkedHashSet::new;
      } else if (obj instanceof SortedSet) {
        creator = TreeSet::new;
      } else {
        creator = HashSet::new;
      }
      return (T)
          ((Set) obj)
              .stream()
              .map(ConfigMappingImpl::deepClone)
              .collect(Collectors.toCollection(creator));
    } else if (obj instanceof List) {
      return (T)
          ((List) obj).stream().map(ConfigMappingImpl::deepClone).collect(Collectors.toList());
    } else if (obj instanceof Cloneable) {
      try {
        return (T) clazz.getMethod("clone", (Class<?>[]) null).invoke(obj, (Object[]) null);
      } catch (NoSuchMethodException | IllegalAccessException e) { // continue with obj
      } catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();

        if (!(cause instanceof CloneNotSupportedException)) {
          throw new ConfigMappingException("Unexpected exception", cause);
        } // else - was not cloneable so continue with obj
      }
    }
    // everything else we do nothing. We are safe for primitive types or their wrappers, strings or
    // other immutable classes. For all others, well!
    return obj;
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
    public <T extends ConfigSingleton> Optional<T> get(Class<T> clazz) {
      // insert or replace the entry with an indicator that we depend on all instances for `type`
      dependents.put(Config.getType(clazz), ConfigMappingImpl.ALL);
      return config.get(clazz);
    }

    @Override
    public <T extends ConfigGroup> Optional<T> get(Class<T> clazz, String id) {
      // insert this specific id for the given type unless we already depend on all
      dependents.compute(
          Config.getType(clazz),
          (t, set) -> {
            if (set == ConfigMappingImpl.ALL) { // identity check
              // this type is dependent on all instances so nothing to do
              return set;
            }
            if (set == null) {
              set = new ConcurrentSkipListSet<>();
            }
            if (id != ConfigMappingImpl.ANY) { // identity check
              set.add(id);
            } // else - id used during first resolution when none defined; don't cache it
            return set;
          });
      return config.get(clazz, id);
    }

    @Override
    public <T extends ConfigGroup> Stream<T> configs(Class<T> type) {
      // insert or replace the entry with an indicator that we depend on all instances for `type`
      // even though we might only care about a subclass of the actual config object type, we will
      // still mark the whole config object type with ALL since we cannot validate which actual
      // subclasses might come in later - at worst, we might recompute more often then required
      dependents.put(Config.getType(type), ConfigMappingImpl.ALL);
      return config.configs(type);
    }
  }
}
