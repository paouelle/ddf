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
package org.codice.ddf.config.agent.osgi;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.commons.lang.ArrayUtils;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigListener;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingEvent;
import org.codice.ddf.config.mapping.ConfigMappingEvent.Type;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingListener;
import org.codice.ddf.config.mapping.ConfigMappingService;
import org.codice.ddf.configuration.DictionaryMap;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.SynchronousConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigAdminAgent
    implements SynchronousConfigurationListener,
        ServiceListener,
        ConfigMappingListener,
        ConfigListener,
        Closeable {
  public static final String INSTANCE_KEY = "org.codice.ddf.config.instance";

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigAdminAgent.class);

  private final ConfigurationAdmin configAdmin;

  private final ConfigMappingService mapper;

  private final ConfigService config;

  // keyed by config group type with the corresponding lists of factory pids to create objects for
  private final Map<Class<? extends ConfigGroup>, String> factories =
      Collections.synchronizedMap(new HashMap<>());

  private final Map<String, Dictionary<String, Object>> cache = new ConcurrentHashMap<>();

  public ConfigAdminAgent(
      ConfigurationAdmin configAdmin, ConfigMappingService mapper, ConfigService config) {
    this.configAdmin = configAdmin;
    this.mapper = mapper;
    this.config = config;
  }

  @SuppressWarnings("unused" /* called by blueprint */)
  public void init() {
    LOGGER.debug("ConfigAdminAgent:init()");
    final BundleContext context = getBundleContext();

    // start by registering a service listener
    context.addServiceListener(this);
    final Set<Optional<ConfigMapping>> processed = new HashSet<>();

    try {
      // then process all existing config objects
      configurations().map(this::updateConfiguration).forEach(processed::add);
    } catch (IOException | ConfigMappingException e) { // ignore
      LOGGER.error("failed to initialize existing config objects: {}", e.getMessage());
      LOGGER.debug("initialization failure: {}", e, e);
    }
    try {
      // finally process all registered services for the PIDs they identify
      ConfigAdminAgent.serviceReferences(context)
          .flatMap(ConfigAdminAgent::servicePids)
          .map(mapper::getMapping)
          .filter(m -> !processed.contains(m)) // filter out those we already processed above
          .filter(Optional::isPresent)
          .map(Optional::get)
          .forEach(this::updateConfigObjectFor);
    } catch (ConfigMappingException e) { // ignore
      LOGGER.error("failed to initialize registered services: {}", e.getMessage());
      LOGGER.debug("initialization failure: {}", e, e);
    }
  }

  @Override
  public void close() {
    LOGGER.debug("ConfigAdminAgent::close()");
    final BundleContext context = getBundleContext();

    context.removeServiceListener(this);
  }

  @SuppressWarnings("unused" /* called from blueprint */)
  public void setFactories(Map<Class<? extends ConfigGroup>, String> factories) {
    // TODO: determine what mappings no longer exist so we can remove the corresponding config objs
    // process all configured config instances we have to monitor
    synchronized (this.factories) {
      this.factories.clear();
      this.factories.putAll(factories);
    }
    factories.forEach(this::findConfigMappingsFor);
  }

  @Override
  public void serviceChanged(ServiceEvent event) {
    final ServiceReference<?> ref = event.getServiceReference();
    final int type = event.getType();

    LOGGER.debug("ConfigAdminAgent::serviceChanged() - type = [{}], service = [{}]", type, ref);
    if ((type == ServiceEvent.REGISTERED) || (type == ServiceEvent.MODIFIED)) {
      ConfigAdminAgent.servicePids(ref)
          .map(mapper::getMapping)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .forEach(this::updateConfigObjectFor);
    }
  }

  @Override
  public void configurationEvent(ConfigurationEvent event) {
    LOGGER.debug("ConfigAdminAgent:configurationEvent({})", event);
    final String pid = event.getPid();

    try {
      switch (event.getType()) {
        case ConfigurationEvent.CM_UPDATED:
        case ConfigurationEvent.CM_LOCATION_CHANGED:
          final ConfigurationAdmin cfgAdmin = getService(event.getReference());

          getConfiguration(cfgAdmin, event.getPid()).ifPresent(this::updateConfiguration);
          break;
        case ConfigurationEvent.CM_DELETED:
          cache.remove(pid);
          return;
        default:
          return;
      }
    } catch (InvalidSyntaxException | IOException | ConfigMappingException e) { // ignore
      LOGGER.warn("failed to process event for config object '{}': {}", pid, e.getMessage());
      LOGGER.debug("config event failure: {}", e, e);
    }
  }

  @Override
  public void mappingChanged(ConfigMappingEvent event) {
    LOGGER.debug("ConfigAdminAgent:mappingChanged({})", event);
    if (event.getType() == Type.REMOVED) {
      // just leave it alone - TODO: should we delete the corresponding config object???
      return;
    }
    updateConfigObjectFor(event.getMapping());
  }

  @Override
  public void configChanged(ConfigEvent event) {
    LOGGER.debug("ConfigAdminAgent:configChanged({})", event);
    // only check updates for config instances that maps to factory pids we are monitoring
    // start with config instances that were removed
    event
        .removedConfigs()
        .filter(ConfigGroup.class::isInstance)
        .map(ConfigGroup.class::cast)
        .forEach(this::removeConfigObjectFor);
    // handle new and updated ones the same way to make sure we have corresponding cfg objects if
    // for whatever reasons we had missed them
    // for updates of existing mappings, these will be handled by the mapper and we will get
    // notified if there is any changes, what we care about here is simply to detect those for which
    // we do not have a corresponding config object
    Stream.concat(event.addedConfigs(), event.updatedConfigs())
        .filter(ConfigGroup.class::isInstance)
        .map(ConfigGroup.class::cast)
        .forEach(this::findConfigMappingFor);
  }

  BundleContext getBundleContext() {
    final Bundle bundle = FrameworkUtil.getBundle(ConfigAdminAgent.class);

    if (bundle != null) {
      return bundle.getBundleContext();
    }
    throw new IllegalStateException("missing bundle for ConfigAdminAgent");
  }

  private void findConfigMappingsFor(Class<? extends ConfigGroup> type, String factoryPid) {
    LOGGER.debug("ConfigAdminAgent:findConfigMappingsFor({}, {})", type, factoryPid);
    config.configs(type).forEach(c -> findConfigMappingFor(c, factoryPid));
  }

  private void findConfigMappingFor(ConfigGroup cfgInstance) {
    LOGGER.debug("ConfigAdminAgent:findConfigMappingFor({})", cfgInstance);
    final String factoryPid = factories.get(cfgInstance.getType());

    if (factoryPid == null) { // not monitoring those so ignore
      return;
    }
    findConfigMappingFor(cfgInstance, factoryPid);
  }

  private void findConfigMappingFor(ConfigGroup cfgInstance, String factoryPid) {
    final String type = cfgInstance.getType().getName();
    final String id = cfgInstance.getId();

    LOGGER.debug("ConfigAdminAgent:findConfigMappingFor({}-{}, {})", type, id, factoryPid);
    final ConfigMapping mapping = mapper.getMapping(factoryPid, id).orElse(null);

    if (mapping != null) {
      LOGGER.debug(
          "found config mapping [{}] for config instance [{}-{}]", mapping.getId(), type, id);
      updateConfigObjectFor(mapping);
    } else {
      LOGGER.debug("no config mappings found for config instance [{}-{}]", type, id);
    }
  }

  private void removeConfigObjectFor(ConfigGroup cfgInstance) {
    LOGGER.debug("ConfigAdminAgent:removeConfigObjectFor({})", cfgInstance);
    final Class<? extends ConfigGroup> type = cfgInstance.getType();
    final String factoryPid = factories.get(type);

    if (factoryPid == null) { // not monitoring those so ignore
      return;
    }
    final String typeName = type.getName();
    final String id = cfgInstance.getId();

    try {
      final Configuration cfg = getConfiguration(factoryPid, id);

      if (cfg != null) {
        cfg.delete(); // cache will be cleanup when we get the even back from cfg admin
        LOGGER.debug("deleted config object '{}-{}' as {}", factoryPid, id, cfg.getPid());
      }
    } catch (InvalidSyntaxException | IOException e) {
      LOGGER.warn(
          "failed to remove config object for config instance '{}-{}': {}",
          typeName,
          id,
          e.getMessage());
      LOGGER.debug("config object removal failure: {}", e, e);
    }
  }

  private void updateConfigObjectFor(ConfigMapping mapping) {
    LOGGER.debug("ConfigAdminAgent:updateConfigObjectFor({})", mapping);
    try {
      final String instance = mapping.getId().getInstance().orElse(null);
      final String pid = mapping.getId().getName();
      final Configuration cfg;

      if (instance != null) { // a managed service factory
        cfg = getOrCreateConfiguration(pid, instance);
      } else {
        // get or create the first version
        // location as null to make sure it is bound to the first bundles that registers the
        // managed service
        cfg = configAdmin.getConfiguration(pid, null);
        LOGGER.debug("created/updated config object '{}'", pid);
      }
      updateConfigurationWithMapping(cfg, mapping);
    } catch (InvalidSyntaxException | ConfigMappingException | IOException e) {
      LOGGER.warn(
          "failed to update config object for config mapping '{}': {}",
          mapping.getId(),
          e.getMessage());
      LOGGER.debug("config object update failure: {}", e, e);
    }
  }

  private Optional<ConfigMapping> updateConfiguration(Configuration cfg) {
    LOGGER.debug("ConfigAdminAgent:updateConfiguration({})", cfg);
    final String factoryPid = cfg.getFactoryPid();
    final Optional<ConfigMapping> mapping;
    final String pid = cfg.getPid();

    if (factoryPid != null) { // see if we know its instance id
      final String instance =
          Objects.toString(cfg.getProperties().get(ConfigAdminAgent.INSTANCE_KEY), null);

      if (instance == null) {
        // we cannot handle that one specifically, check if we have mappings for the factory and
        // if we do, log this as an error since we should have an instance for the factories we
        // handled
        if (mapper.getMapping(factoryPid).isPresent()) {
          LOGGER.error(
              "unable to map managed service factory '{}'; missing instance from config object '{}'",
              factoryPid,
              pid);
        } else {
          LOGGER.debug(
              "unknown managed service factory; missing instance from config object [{}]", pid);
        }
        return Optional.empty();
      } else {
        LOGGER.debug(
            "found instance id from config object [{}]; handling it as [{}-{}]",
            pid,
            factoryPid,
            instance);
        mapping = mapper.getMapping(factoryPid, instance);
      }
    } else {
      mapping = mapper.getMapping(pid);
    }
    mapping.ifPresent(m -> updateConfigurationWithMapping(cfg, m));
    return mapping;
  }

  private void updateConfigurationWithMapping(Configuration cfg, ConfigMapping mapping) {
    LOGGER.debug("ConfigAdminAgent:updateConfigurationWithMapping({}, {})", cfg, mapping);
    final String pid = cfg.getPid();

    try {
      final Dictionary<String, Object> properties = ConfigAdminAgent.getProperties(cfg);
      final String instance = mapping.getId().getInstance().orElse(null);

      // compute the new mapping values
      mapping.resolve().forEach((k, v) -> ConfigAdminAgent.putOrRemove(properties, k, v));
      updateConfigurationProperties(cfg, instance, properties);
    } catch (IOException | ConfigMappingException e) {
      LOGGER.error("failed to update config object '{}': {}", pid, e.getMessage());
      LOGGER.debug("config object update failure", e);
    }
  }

  private void updateConfigurationProperties(
      Configuration cfg, @Nullable String instance, @Nullable Dictionary<String, Object> properties)
      throws IOException {
    final String pid = cfg.getPid();
    final Dictionary<String, Object> cachedProperties = cache.get(pid);

    if (properties == null) { // initialize the properties
      properties = new DictionaryMap<>();
    }
    // keep the instance up to date
    if (instance != null) {
      properties.put(ConfigAdminAgent.INSTANCE_KEY, instance);
    } else {
      properties.remove(ConfigAdminAgent.INSTANCE_KEY);
    }
    // only update configAdmin if the dictionary content has changed
    if ((cachedProperties == null) || !ConfigAdminAgent.equals(cachedProperties, properties)) {
      LOGGER.debug("updating config object [{}] with: {}", pid, properties);
      Dictionary<String, Object> old = null;

      try {
        old = cache.put(pid, properties);
        cfg.update(properties);
      } catch (IOException e) {
        if (old == null) {
          cache.remove(pid);
        } else {
          cache.put(pid, old);
        }
        throw e;
      }
    }
  }

  private Optional<Configuration> getConfiguration(ConfigurationAdmin configAdmin, String pid)
      throws InvalidSyntaxException, IOException {
    // we use listConfigurations to not bind the config object to our bundle if it was bound yet
    // as we want to make sure that it will be bounded to its corresponding service
    final String filter = String.format("(%s=%s)", org.osgi.framework.Constants.SERVICE_PID, pid);
    final Configuration[] configs = configAdmin.listConfigurations(filter);

    return ArrayUtils.isNotEmpty(configs) ? Optional.of(configs[0]) : Optional.empty();
  }

  @Nullable
  private Configuration getConfiguration(String factoryPid, String instance)
      throws InvalidSyntaxException, IOException {
    final Configuration[] cfgs =
        configAdmin.listConfigurations(
            String.format(
                "(&(service.factoryPid=%s)(%s=%s))",
                factoryPid,
                ConfigAdminAgent.INSTANCE_KEY,
                ConfigAdminAgent.escapeFilterValue(instance)));

    if (ArrayUtils.isNotEmpty(cfgs)) {
      LOGGER.debug("found config object '{}-{}' as {}", factoryPid, instance, cfgs[0].getPid());
      return cfgs[0];
    }
    LOGGER.debug("config object '{}-{}' not found", factoryPid, instance);
    return null;
  }

  private Configuration getOrCreateConfiguration(String factoryPid, String instance)
      throws InvalidSyntaxException, IOException {
    Configuration cfg = getConfiguration(factoryPid, instance);

    if (cfg != null) {
      return cfg;
    }
    // create the first version
    // location as null to make sure it is bound to the first bundles that registers the
    // managed service factory
    cfg = configAdmin.createFactoryConfiguration(factoryPid, null);
    LOGGER.debug("created new config object '{}-{}' as {}", factoryPid, instance, cfg.getPid());
    return cfg;
  }

  private Stream<Configuration> configurations() throws IOException {
    try {
      final Configuration[] configurations = configAdmin.listConfigurations(null);

      return (configurations != null) ? Stream.of(configurations) : Stream.empty();
    } catch (InvalidSyntaxException e) { // should never happen
      LOGGER.error("failed to retrieved existing configurations: {}", e.getMessage());
      LOGGER.debug("configuration retrieval failure: {}", e, e);
      return Stream.empty();
    }
  }

  private <S> S getService(ServiceReference<S> serviceReference) {
    return AccessController.doPrivileged(
        (PrivilegedAction<S>) () -> getBundleContext().getService(serviceReference));
  }

  private static void putOrRemove(Dictionary<String, Object> properties, String key, Object value) {
    if (value != null) {
      properties.put(key, value);
    } else {
      properties.remove(key);
    }
  }

  private static Dictionary<String, Object> getProperties(Configuration cfg) {
    final Dictionary<String, Object> properties = cfg.getProperties();

    return (properties != null) ? properties : new DictionaryMap<>();
  }

  private static boolean equals(Dictionary<String, Object> x, Dictionary<String, Object> y) {
    if (x.size() != y.size()) {
      return false;
    }
    for (final Enumeration<String> e = x.keys(); e.hasMoreElements(); ) {
      final String key = e.nextElement();

      if (!Objects.deepEquals(x.get(key), y.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static String escapeFilterValue(String s) {
    return s.replaceAll("[(]", "\\\\(")
        .replaceAll("[)]", "\\\\)")
        .replaceAll("[=]", "\\\\=")
        .replaceAll("[\\*]", "\\\\*");
  }

  private static Stream<ServiceReference<?>> serviceReferences(BundleContext context) {
    try {
      final ServiceReference<?>[] refs = context.getServiceReferences((String) null, null);

      return (refs != null) ? Stream.of(refs) : Stream.empty();
    } catch (InvalidSyntaxException e) { // should never happen
      LOGGER.error("failed to retrieved existing services: {}", e.getMessage());
      LOGGER.debug("service retrieval failure: {}", e, e);
      return Stream.empty();
    }
  }

  private static Stream<String> servicePids(ServiceReference<?> ref) {
    final Object prop = ref.getProperty(Constants.SERVICE_PID);

    if (prop instanceof String) {
      return Stream.of((String) prop);
    } else if (prop instanceof Collection) {
      return ((Collection<?>) prop).stream().map(String::valueOf);
    } else if ((prop != null) && prop.getClass().isArray()) {
      final int length = Array.getLength(prop);

      return StreamSupport.stream(
          Spliterators.spliterator(
              new Iterator<String>() {
                private int i = 0;

                @Override
                public boolean hasNext() {
                  return i < length;
                }

                @Override
                public String next() {
                  if (!hasNext()) {
                    throw new NoSuchElementException();
                  }
                  return String.valueOf(Array.get(prop, i++));
                }

                @Override
                public void remove() {
                  throw new UnsupportedOperationException();
                }
              },
              length,
              Spliterator.ORDERED),
          false);
    } // else - unsupported type or null so return empty stream
    return Stream.empty();
  }
}
