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

import io.fabric8.karaf.core.properties.PlaceholderResolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingImpl implements ConfigMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingImpl.class);

  private static final String DEPENDENT_CONFIGS = "dependent.configs";

  private final PlaceholderResolver resolver;
  private final Path path;
  private final String id;
  private final Map<String, String> rules = new HashMap<>();
  private final Map<String, Set<String>> dependentConfigs = new HashMap<>();

  public ConfigMappingImpl(PlaceholderResolver resolver, String id) {
    this(resolver, Paths.get(System.getProperty("ddf.home"), "etc", "mapping", id + ".mapping"));
  }

  public ConfigMappingImpl(PlaceholderResolver resolver, File file) {
    this(resolver, file.toPath());
  }

  public ConfigMappingImpl(PlaceholderResolver resolver, Path path) {
    LOGGER.debug("ConfigMappingImpl({}, {})", resolver, path);
    this.resolver = resolver;
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
  public Map<String, String> resolve() {
    final Map<String, String> properties;

    synchronized (rules) {
      properties = new HashMap<>(rules);
    }
    resolver.replaceAll((Map<String, Object>) (Map) properties);
    LOGGER.debug("ConfigMappingImpl[{}].resolve()", id, properties);
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
      final Map<String, String> props = (Map<String, String>) (Map) p;
      final String dependentString = props.remove(ConfigMappingImpl.DEPENDENT_CONFIGS);

      if (dependentString == null) {
        throw new IOException("missing '" + ConfigMappingImpl.DEPENDENT_CONFIGS + "'");
      }
      final Map<String, Set<String>> dependents =
          Stream.of(dependentString.split(","))
              .map(c -> c.split("\\."))
              .collect(
                  Collectors.groupingBy(
                      s -> s[0], HashMap::new, Collectors.mapping(s -> s[1], Collectors.toSet())));

      synchronized (rules) {
        rules.clear();
        rules.putAll(props);
        dependentConfigs.clear();
        dependentConfigs.putAll(dependents);
      }
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - rules = {}", id, rules);
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - dependent = {}", id, dependentConfigs);
    } catch (IOException e) {
      LOGGER.debug("ConfigMappingImpl[{}].loadRules() - failed to load rules: {}", id, e, e);
      throw new UncheckedIOException(e);
    }
  }
}
