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
package org.codice.ddf.config.mapping.groovy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingInformation;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GroovyConfigMappingProvider implements ConfigMappingProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(GroovyConfigMappingProvider.class);

  private final Set<String> names;

  private final Set<String> instances;

  private final int rank;

  private final Map<String, Object> rules;

  /**
   * Constructs a default groovy config mapping provider.
   *
   * <p><i>Note:</i> This constructor is primarly defined for Json deserialization such that we end
   * up initializing the lists with empty collections. This will be helpful, for example, in case
   * where no instance ids were serialized in which case Boon would not be setting this attribute.
   */
  GroovyConfigMappingProvider() {
    this.names = new HashSet<>();
    this.instances = new HashSet<>();
    this.rank = 0;
    this.rules = new HashMap<>();
  }

  @VisibleForTesting
  GroovyConfigMappingProvider(
      HashSet<String> names, HashSet<String> instance, int rank, Map<String, Object> rules) {
    this.names = names;
    this.instances = instance;
    this.rank = rank;
    this.rules = rules;
  }

  @Override
  public int getRank() {
    return rank;
  }

  @Override
  public boolean canProvideFor(ConfigMapping mapping) {
    return canProvideFor(mapping.getId());
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
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

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService config)
      throws ConfigMappingException {
    LOGGER.debug("GroovyConfigMappingProvider::provide({}, {})", id, config);
    final Map<String, Object> bindings =
        ImmutableMap.of(
            "name",
            id.getName(),
            "instance",
            id.getInstance().orElse(null),
            "config",
            config,
            "env",
            System.getenv(),
            "system",
            System.getProperties());
    final Map<String, Object> properties;

    synchronized (rules) {
      properties = new HashMap<>(rules);
      for (Iterator<Entry<String, Object>> i = properties.entrySet().iterator(); i.hasNext(); ) {
        final Map.Entry<String, Object> e = i.next();
        final Object value = e.getValue();

        if (value instanceof String) {
          try {
            e.setValue(new GroovyShell(new Binding(bindings)).evaluate((String) value));
          } catch (CompilationFailedException ge) {
            LOGGER.error(
                "failed to compile groovy script [{}] for config mapping [{}]: {}", value, id, ge);
            throw new ConfigMappingException("invalid groovy script: " + value, ge);
          } catch (GroovyRuntimeException ge) {
            LOGGER.error(
                "failed to evaluate groovy script [{}] for config mapping [{}]: {}", value, id, ge);
            throw new ConfigMappingException("error while evaluating groovy script: " + value, ge);
          }
        }
      }
    }
    LOGGER.debug("provided properties for config mapping [{}] = {}", id, properties);
    return properties;
  }

  @Override
  public int compareTo(ConfigMappingProvider provider) {
    if (provider instanceof ConfigMappingInformation) {
      return Integer.compare(getRank(), ((ConfigMappingInformation) provider).getRank());
    } // else - since we have a rank and they don't, we have higher priority
    return 1;
  }

  @Override
  public String toString() {
    return "GroovyConfigMappingProvider[names="
        + names
        + ", instances="
        + instances
        + ", rank="
        + rank
        + ", rules="
        + rules
        + "]";
  }
}
