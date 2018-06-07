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

import groovy.lang.GroovyRuntimeException;
import groovy.util.Eval;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingImpl implements ConfigMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingImpl.class);

  private static final String DEPENDENT_CONFIGS = "dependent.configs";

  private final ConfigAbstractionAgent agent;
  private final Path path;
  private final String id;
  private final Configuration cfg = new Configuration();
  private final Map<String, String> rules = new HashMap<>();
  private final Map<String, Set<String>> dependentConfigs = new HashMap<>();

  public ConfigMappingImpl(ConfigAbstractionAgent agent, String id) {
    this(agent, Paths.get(System.getProperty("ddf.home"), "etc", "mapping", id + ".mapping"));
  }

  public ConfigMappingImpl(ConfigAbstractionAgent agent, File file) {
    this(agent, file.toPath());
  }

  public ConfigMappingImpl(ConfigAbstractionAgent agent, Path path) {
    LOGGER.debug("ConfigMappingImpl(agent, {})", path);
    this.agent = agent;
    this.path = path;
    this.id = FilenameUtils.getBaseName(path.toString());
    loadRules();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isDependentOn(String id, String key) {
    final Set<String> keys;

    synchronized (rules) {
      keys = dependentConfigs.get(id);
    }
    final boolean dependent = (keys != null) && keys.contains(key);

    LOGGER.debug("ConfigMappingImpl[{}]:isDependentOn({}, {}) = {}", this.id, id, key, dependent);
    return dependent;
  }

  @Override
  public boolean shouldBeUpdated(Map<String, Set<String>> ids) {
    synchronized (rules) {
      for (final Map.Entry<String, Set<String>> e : ids.entrySet()) {
        final Set<String> keys = dependentConfigs.get(e.getKey());

        if (keys != null) {
          if (e.getValue().stream().anyMatch(keys::contains)) {
            LOGGER.debug("ConfigMappingImpl[{}].shouldBeUpdated({}) = true", id, ids);
            return true;
          }
        }
      }
    }
    LOGGER.debug("ConfigMappingImpl[{}].shouldBeUpdated({}) = false", id, ids);
    return false;
  }

  @Override
  public Map<String, Object> resolve() throws IOException {
    final Map<String, Object> properties;

    synchronized (rules) {
      properties = new HashMap<>(rules);
      for (Iterator<Entry<String, Object>> i = properties.entrySet().iterator(); i.hasNext(); ) {
        final Map.Entry<String, Object> e = i.next();
        final Object value = e.getValue();

        if (value instanceof String) {
          try {
            e.setValue(Eval.me("cfg", cfg, (String) value));
          } catch (CompilationFailedException ge) {
            LOGGER.error(
                "failed to compile groovy script [{}] for mapping [{}]: {}", value, id, ge);
            throw new IOException("invalid groovy script: " + value, ge);
          } catch (GroovyRuntimeException ge) {
            LOGGER.error(
                "failed to evaluate groovy script [{}] for mapping [{}]: {}", value, id, ge);
            throw new IOException("error while evaluating groovy script: " + value, ge);
          }
        }
      }
    }
    LOGGER.debug("ConfigMappingImpl[{}].resolve() = {}", id, properties);
    return properties;
  }

  @Override
  public String toString() {
    return String.format(
        "ConfigMappingImpl[id=%s, rules=%s, dependents=%s]", id, rules, dependentConfigs);
  }

  void loadRules() {
    LOGGER.debug("ConfigMappingImpl[{}].loadRules()", id);
    final Properties p = new Properties();
    final File f = path.toFile();

    try (final Reader r = new FileReader(f);
        final BufferedReader br = new BufferedReader(r)) {
      p.load(r);
      synchronized (rules) {
        rules.clear();
        rules.putAll((Map<String, String>) (Map) p);
        dependentConfigs.clear();
        resolve(); // first resolution to compute the initial dependent configuration
      }
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - rules = {}", id, rules);
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - dependent = {}", id, dependentConfigs);
    } catch (IOException e) {
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - failed to load rules: {}", id, e, e);
      throw new UncheckedIOException(e);
    }
  }

  public class Configuration {
    private Configuration() {}

    public Object get(String id, String key) {
      dependentConfigs.compute(
          id,
          (i, keys) -> {
            if (keys == null) {
              keys = new HashSet<>();
            }
            keys.add(key);
            return keys;
          });
      return agent.get(id, key, Object.class);
    }
  }
}
