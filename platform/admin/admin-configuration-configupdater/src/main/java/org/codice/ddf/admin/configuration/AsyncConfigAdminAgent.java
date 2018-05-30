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

import com.google.common.collect.Iterators;
import ddf.security.encryption.EncryptionService;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.admin.core.api.ConfigurationAdmin;
import org.codice.ddf.platform.io.internal.PersistenceStrategy;
import org.codice.felix.cm.internal.ConfigurationContext;
import org.codice.felix.cm.internal.ConfigurationPersistencePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncConfigAdminAgent implements ConfigurationPersistencePlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncConfigAdminAgent.class);

  private final ConfigurationAdmin ddfConfigAdmin;

  private final PersistenceStrategy strategy;

  private final EncryptionService encryptionService;

  public AsyncConfigAdminAgent(
      ConfigurationAdmin ddfConfigAdmin,
      PersistenceStrategy strategy,
      EncryptionService encryptionService) {
    this.ddfConfigAdmin = ddfConfigAdmin;
    this.strategy = strategy;
    this.encryptionService = encryptionService;
  }

  @Override
  public void initialize(Set<ConfigurationContext> state) {
    LOGGER.debug("AsyncConfigAdminAgent:initialize(set, creator)");
    state.forEach(this::initialize);
  }

  @Override
  public boolean handleExist(String pid) {
    LOGGER.debug("AsyncConfigAdminAgent:handleExist({})", pid);
    return hasMetaType(pid);
  }

  @Override
  public void handleLoad(ConfigurationContext context) throws IOException {
    LOGGER.debug("AsyncConfigAdminAgent:handleLoad({})", context);
    final ObjectClassDefinition metaType = getMetaType(context.getServicePid());

    if (metaType == null) { // we don't care about non-metatypes
      return;
    }
    initializeDefaults(context, metaType); // start by loading default values from the meta type
    // then apply abstracted configuration
    // finalize with overridden values
  }

  @Override
  public Enumeration<String> handleGet() throws IOException {
    LOGGER.debug("AsyncConfigAdminAgent:handleGet()");
    return Iterators.asEnumeration(
        Stream.concat(pids(ManagedService.class), pids(ManagedServiceFactory.class))
            .filter(this::hasMetaType)
            .peek(cfg -> LOGGER.debug("AsyncConfigAdminAgent:handleGet() -> " + cfg))
            .iterator());
  }

  @Override
  public void handleStore(ConfigurationContext context) throws IOException {
    LOGGER.debug("AsyncConfigAdminAgent:handleStore({})", context);
    if (!hasMetaType(context.getServicePid())) { // we don't care about non-metatypes
      return;
    }
    // TODO: figure out what to override on disk (only what has changed from before)
  }

  @Override
  public void handleDelete(String pid) throws IOException {
    LOGGER.debug("AsyncConfigAdminAgent:handleDelete({})", pid);
    if (!hasMetaType(pid)) { // we don't care about non-metatypes
      return;
    }
    // TODO: delete the override
  }

  BundleContext getBundleContext() {
    final Bundle bundle = FrameworkUtil.getBundle(AsyncConfigAdminAgent.class);

    if (bundle != null) {
      return bundle.getBundleContext();
    }
    throw new IllegalStateException("missing bundle for AsyncConfigAdminAgent");
  }

  private void initialize(ConfigurationContext context) {
    LOGGER.debug("AsyncConfigAdminAgent:initialize({})", context);
    final ObjectClassDefinition metaType = getMetaType(context.getServicePid());

    if (metaType == null) { // we don't care about non-metatypes
      return;
    }
    // TODO: changes have to be propagated to config admin directly instead of updating the provided
    // context
    // since these have already been loaded in the system before we were here
    // we could technically, simply add a temp property and update which would trigger the
    // persistence
    // manager to call our handleStore(). However would we know to reload it all there (maybe if we
    // look for that property first)
  }

  /**
   * Initializes a given context with the default values defined by the provided metatype.
   *
   * @param context the context to be updated
   * @param metatype the metatype to get the default values from
   * @throws NumberFormatException if unable to convert a default value
   */
  private void initializeDefaults(ConfigurationContext context, ObjectClassDefinition metatype) {
    LOGGER.debug("AsyncConfigAdminAgent:initializeDefaults({}, {})", context, metatype);
    for (final AttributeDefinition def :
        metatype.getAttributeDefinitions(ObjectClassDefinition.ALL)) {
      if (def == null) {
        continue;
      }
      final String[] sdefaults = def.getDefaultValue();

      if (sdefaults == null) {
        continue;
      }
      final String id = def.getID();

      if (id == null) {
        continue;
      }
      final Object[] defaults = new Object[sdefaults.length];

      for (int i = 0; i < sdefaults.length; i++) {
        defaults[i] = AsyncConfigAdminAgent.parseDefault(id, sdefaults[i], def.getType());
      }
      if (def.getCardinality() == 0) {
        context.setProperty(id, defaults[0]);
      } else if (def.getCardinality() > 0) {
        context.setProperty(id, defaults);
      } else { // negative cardinality means it should be a vector
        context.setProperty(id, new Vector(Arrays.asList(defaults)));
      }
    }
  }

  @Nullable
  private ObjectClassDefinition getMetaType(String pid) {
    return AccessController.doPrivileged(
        (PrivilegedAction<ObjectClassDefinition>)
            () -> ddfConfigAdmin.getObjectClassDefinition(pid));
  }

  private boolean hasMetaType(String pid) {
    return getMetaType(pid) != null;
  }

  private Stream<String> pids(Class<?> serviceClass) throws IOException {
    try {
      return AccessController.doPrivileged(
          (PrivilegedExceptionAction<Stream<String>>)
              () ->
                  Stream.of(
                          getBundleContext()
                              .getAllServiceReferences(
                                  serviceClass.getName(), "(" + Constants.SERVICE_PID + "=*)"))
                      .map(ref -> ref.getProperty(Constants.SERVICE_PID))
                      .filter(AsyncConfigAdminAgent::isAllowedPid)
                      .map(String.class::cast));
    } catch (PrivilegedActionException e) {
      throw new IOException(e.getException()); // should never happen since we hardcoded it
    }
  }

  private static boolean isAllowedPid(@Nullable Object pid) {
    if (pid instanceof String) {
      for (final char c : ((String) pid).toCharArray()) {
        if ((c == '&') || (c == '<') || (c == '>') || (c == '"') || (c == '\'')) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static Object parseDefault(String id, String value, int type) {
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
}
