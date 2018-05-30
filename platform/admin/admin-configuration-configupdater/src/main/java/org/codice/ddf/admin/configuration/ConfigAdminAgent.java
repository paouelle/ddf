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
package org.codice.ddf.admin.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.configuration.DictionaryMap;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.SynchronousConfigurationListener;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ConfigAdminAgent implements SynchronousConfigurationListener, ConfigMappingListener {
  public static final String INSTANCE_ID_KEY = "instance.id";

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigAdminAgent.class);

  private final org.codice.ddf.admin.core.api.ConfigurationAdmin ddfConfigAdmin;

  private final ConfigurationAdmin configAdmin;

  private final ConfigMappingService mapper;

  private final Map<String, Dictionary<String, Object>> cache = new ConcurrentHashMap<>();

  public ConfigAdminAgent(
      ConfigurationAdmin configAdmin,
      org.codice.ddf.admin.core.api.ConfigurationAdmin ddfConfigAdmin,
      ConfigMappingService mapper) {
    this.configAdmin = configAdmin;
    this.ddfConfigAdmin = ddfConfigAdmin;
    this.mapper = mapper;
  }

  @SuppressWarnings("unused" /* called by blueprint */)
  public void init() {
    LOGGER.debug("ConfigAdminAgent:init()");
    // let's update all existing config objects
  }

  @Override
  public void configurationEvent(ConfigurationEvent event) {
    LOGGER.debug("ConfigAdminAgent:configurationEvent({})", event);
    try {
      switch (event.getType()) {
        case ConfigurationEvent.CM_UPDATED:
        case ConfigurationEvent.CM_LOCATION_CHANGED:
          final Configuration cfg =
              getConfiguration(getService(event.getReference()), event.getPid());

          if (cfg != null) {
            updated(cfg);
          }
          break;
        case ConfigurationEvent.CM_DELETED:
          cache.remove(event.getPid());
          return;
        default:
          return;
      }
    } catch (NumberFormatException
        | InvalidSyntaxException
        | IOException
        | UncheckedIOException e) { // ignore
      LOGGER.debug("failed to process configuration event: {}", e, e);
    }
  }

  @Override
  public void updated(ConfigMapping mapping) {
    LOGGER.debug("ConfigAdminAgent:updated({})", mapping);
    try {
      final Configuration cfg;
      final String pid = mapping.getId();
      final String instanceId;
      final int i = pid.indexOf('-');

      if (i != -1) { // a managed service factory
        final String factoryPid = pid.substring(0, i);

        instanceId = pid.substring(i + 1);
        final Configuration[] cfgs =
            configAdmin.listConfigurations(
                String.format(
                    "(&(service.factoryPid=%s)(%s=%s))",
                    factoryPid,
                    ConfigAdminAgent.INSTANCE_ID_KEY,
                    ConfigAdminAgent.escapeFilterValue(instanceId)));

        if (ArrayUtils.isNotEmpty(cfgs)) {
          cfg = cfgs[0];
        } else {
          // get or create the first version
          // location as null to make sure it is bound to the first bundles that registers the
          // managed service factory
          cfg = configAdmin.createFactoryConfiguration(factoryPid, null);
          LOGGER.debug(
              "created a new managed service factory for '{}-{}' as {}",
              factoryPid,
              instanceId,
              cfg.getPid());
        }
      } else {
        // get or create the first version
        // location as null to make sure it is bound to the first bundles that registers the
        // managed service
        cfg = configAdmin.getConfiguration(pid, null);
        instanceId = null;
      }
      updated(cfg, mapping, instanceId);
    } catch (InvalidSyntaxException
        | NumberFormatException
        | IOException
        | UncheckedIOException e) {
      LOGGER.debug("failed to process mapping update: {}", e, e);
    }
  }

  @Override
  public void removed(ConfigMapping mapping) {
    LOGGER.debug("ConfigAdminAgent:removed({})", mapping);
    // just leave it alone
  }

  BundleContext getBundleContext() {
    final Bundle bundle = FrameworkUtil.getBundle(ConfigAdminAgent.class);

    if (bundle != null) {
      return bundle.getBundleContext();
    }
    throw new IllegalStateException("missing bundle for ConfigAdminAgent");
  }

  @Nullable
  private Configuration getConfiguration(ConfigurationAdmin configAdmin, String pid)
      throws InvalidSyntaxException, IOException {
    // we use listConfigurations to not bind the config object to our bundle if it was bound yet
    final String filter = String.format("(%s=%s)", org.osgi.framework.Constants.SERVICE_PID, pid);
    final Configuration[] configs = configAdmin.listConfigurations(filter);

    if (ArrayUtils.isNotEmpty(configs)) {
      return configs[0];
    }
    return null;
  }

  private <S> S getService(ServiceReference<S> serviceReference) {
    return AccessController.doPrivileged(
        (PrivilegedAction<S>) () -> getBundleContext().getService(serviceReference));
  }

  @Nullable
  private ObjectClassDefinition getMetaType(Configuration cfg) {
    return AccessController.doPrivileged(
        (PrivilegedAction<ObjectClassDefinition>)
            () -> ddfConfigAdmin.getObjectClassDefinition(cfg));
  }

  private void updated(Configuration cfg) {
    LOGGER.debug("ConfigAdminAgent:updated({})", cfg);
    final String factoryPid = cfg.getFactoryPid();
    final String instanceId;
    String pid = cfg.getPid();

    if (factoryPid != null) { // see if we know its instance id
      instanceId =
          Objects.toString(cfg.getProperties().get(ConfigAdminAgent.INSTANCE_ID_KEY), null);
      if (instanceId == null) { // we cannot handle that one since there is no instance id available
        LOGGER.debug(
            "unknown managed service factory; missing instance id from config object: {}", pid);
        return;
      }
      pid = factoryPid + '-' + instanceId;
    } else {
      instanceId = null;
    }
    mapper.getMapping(pid).ifPresent(m -> updated(cfg, m, instanceId));
  }

  private void updated(Configuration cfg, ConfigMapping mapping, @Nullable String instanceId) {
    LOGGER.debug("ConfigAdminAgent:updated({}, {}, {})", cfg, mapping, instanceId);
    Dictionary<String, Object> cfgProperties = cfg.getProperties();

    if (cfgProperties == null) {
      LOGGER.debug(
          "ConfigAdminAgent:updated({}, {}, {}) - no configuration properties",
          cfg,
          mapping,
          instanceId);
      cfgProperties = new DictionaryMap<>();
    }
    // compute the new mapping values
    final Map<String, String> mappedProperties = mapping.resolve();
    // convert mapped values based on metatype definition for this config
    final ObjectClassDefinition metatype = getMetaType(cfg);

    if (metatype != null) {
      for (final AttributeDefinition def :
          metatype.getAttributeDefinitions(ObjectClassDefinition.ALL)) {
        if (def == null) {
          continue;
        }
        final String key = def.getID();

        if (mappedProperties.containsKey(key)) {
          cfgProperties.put(
              key, ConfigAdminAgent.parse(key, mappedProperties.remove(key), def.getType()));
        }
      }
    }
    // anything left is a simple pass-thru as a string and hope for the best
    mappedProperties.forEach(cfgProperties::put);
    // finally keep the instance id up to date
    if (instanceId != null) {
      cfgProperties.put(ConfigAdminAgent.INSTANCE_ID_KEY, instanceId);
    } else {
      cfgProperties.remove(ConfigAdminAgent.INSTANCE_ID_KEY);
    }
    // only update config admin if the dictionary content has changed
    final String pid = cfg.getPid();
    final Dictionary<String, Object> cachedProperties = cache.get(pid);

    if ((cachedProperties == null) || !ConfigAdminAgent.equals(cachedProperties, cfgProperties)) {
      LOGGER.debug("updating config admin with: {}" + cfgProperties);
      Dictionary<String, Object> old = null;

      try {
        old = cache.put(pid, cfgProperties);
        cfg.update(cfgProperties);
      } catch (IOException e) {
        if (old == null) {
          cache.remove(pid);
        } else {
          cache.put(pid, old);
        }
        throw new UncheckedIOException(e);
      }
    }
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

  private static Object parse(String id, @Nullable String value, int type) {
    if ((type == AttributeDefinition.PASSWORD) || (type == AttributeDefinition.STRING)) {
      return value;
    }
    if (StringUtils.isNotEmpty(value)) {
      switch (type) {
        case AttributeDefinition.BOOLEAN:
          return Boolean.valueOf(value);
        case AttributeDefinition.BYTE:
          return Byte.valueOf(value);
        case AttributeDefinition.DOUBLE:
          return Double.valueOf(value);
        case AttributeDefinition.CHARACTER:
          return Character.valueOf(value.charAt(0));
        case AttributeDefinition.FLOAT:
          return Float.valueOf(value);
        case AttributeDefinition.INTEGER:
          return Integer.valueOf(value);
        case AttributeDefinition.LONG:
          return Long.valueOf(value);
        case AttributeDefinition.SHORT:
          return Short.valueOf(value);
        default:
          throw new NumberFormatException(
              String.format("unknown type '%d' for metatype attribute: %s", type, id));
      }
    }
    return null;
  }

  private static String escapeFilterValue(String s) {
    return s.replaceAll("[(]", "\\\\(")
        .replaceAll("[)]", "\\\\)")
        .replaceAll("[=]", "\\\\=")
        .replaceAll("[\\*]", "\\\\*");
  }
}
