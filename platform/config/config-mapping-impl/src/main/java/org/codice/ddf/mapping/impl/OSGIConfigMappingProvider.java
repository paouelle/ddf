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

import java.io.Closeable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMapping.Id;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingInformation;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public class OSGIConfigMappingProvider implements ConfigMappingInformation, Closeable {
  private final BundleContext context;

  private final Object lock = new Object();

  private final ServiceReference<ConfigMappingProvider> reference;

  private final ConfigMappingProvider provider;

  private final Set<String> names;

  private final Set<String> instances;

  private int rank;

  OSGIConfigMappingProvider(BundleContext context, ServiceReference<ConfigMappingProvider> ref) {
    this.context = context;
    this.reference = ref;
    this.provider = context.getService(ref);
    this.names = OSGIConfigMappingProvider.getStringPlus(ref, ConfigMappingProvider.MAPPING_NAME);
    this.instances =
        OSGIConfigMappingProvider.getStringPlus(ref, ConfigMappingProvider.MAPPING_INSTANCE);
    this.rank = OSGIConfigMappingProvider.getInteger(ref, Constants.SERVICE_RANKING);
  }

  @Override
  public void close() {
    context.ungetService(reference);
  }

  void reinit() {
    final Set<String> newNames =
        OSGIConfigMappingProvider.getStringPlus(reference, ConfigMappingProvider.MAPPING_NAME);
    final Set<String> newInstances =
        OSGIConfigMappingProvider.getStringPlus(reference, ConfigMappingProvider.MAPPING_INSTANCE);

    synchronized (lock) {
      names.clear();
      names.addAll(newNames);
      instances.clear();
      instances.addAll(newInstances);
      this.rank = OSGIConfigMappingProvider.getInteger(reference, Constants.SERVICE_RANKING);
    }
  }

  @Override
  public int getRank() {
    synchronized (lock) {
      return rank;
    }
  }

  @Override
  public boolean canProvideFor(ConfigMapping mapping) {
    return canProvideFor(mapping.getId());
  }

  @Override
  public boolean canProvideFor(Id id) {
    synchronized (lock) {
      final String name = id.getName();

      if (!names.contains(name)) {
        return false;
      }
      final String instance = id.getInstance().orElse(null);

      if (instance == null) {
        if (instances.isEmpty()) {
          return true;
        } // else - we only provide specific instances
      } else if (instances.isEmpty() || instances.contains(instance)) {
        // we either provide all instances or the specified one
        return true;
      }
      return false;
    }
  }

  @Override
  public Map<String, Object> provide(Id id, ConfigService config) throws ConfigMappingException {
    return provider.provide(id, config);
  }

  @Override
  public int compareTo(ConfigMappingProvider provider) {
    synchronized (lock) {
      if (provider instanceof OSGIConfigMappingProvider) {
        // rely on service reference comparison which will use ranking and service order
        return reference.compareTo(((OSGIConfigMappingProvider) provider).reference);
      } else if (provider instanceof ConfigMappingInformation) {
        return Integer.compare(getRank(), ((ConfigMappingInformation) provider).getRank());
      } // else - since we have a rank and they don't, we have higher priority
      return 1;
    }
  }

  @Override
  public String toString() {
    return "OSGIConfigMappingProvider[names="
        + names
        + ", instances="
        + instances
        + ", rank="
        + rank
        + ", provider="
        + provider
        + ", reference="
        + reference
        + "]";
  }

  private static Set<String> getStringPlus(ServiceReference<?> ref, String propertyName) {
    final Set<String> set = new HashSet<>();
    final Object prop = ref.getProperty(propertyName);

    if (prop instanceof String) {
      set.add((String) prop);
    } else if (prop instanceof Collection) {
      ((Collection<?>) prop).stream().map(String::valueOf).forEach(set::add);
    } else if ((prop != null) && prop.getClass().isArray()) {
      final int length = Array.getLength(prop);

      for (int i = 0; i < length; i++) {
        set.add(String.valueOf(Array.get(prop, i)));
      }
    } // else - unsupported type or null so return empty set
    return set;
  }

  private static int getInteger(ServiceReference<?> ref, String propertyName) {
    final Object prop = ref.getProperty(propertyName);

    if (prop instanceof Number) {
      return ((Number) prop).intValue();
    }
    return 0;
  }
}
