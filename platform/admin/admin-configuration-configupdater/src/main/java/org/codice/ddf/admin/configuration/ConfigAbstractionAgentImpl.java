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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.codice.ddf.platform.io.internal.PersistenceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigAbstractionAgentImpl implements ConfigAbstractionAgent, ArtifactInstaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigAbstractionAgentImpl.class);

  private final List<ConfigAbstractionListener> listeners;

  private final PersistenceStrategy configReader;

  private final Map<String, Map<String, Object>> previousConfigs = new HashMap<>();

  private final Map<String, Map<String, Object>> currentConfigs = new HashMap<>();

  public ConfigAbstractionAgentImpl(
      List<ConfigAbstractionListener> listeners, PersistenceStrategy reader) {
    this.listeners = listeners;
    this.configReader = reader;
  }

  @Override
  public void install(File abstractConfig) throws Exception {
    LOGGER.error("##### Start ConfigAbstractionAgent.install({})", abstractConfig);
    setCurrentConfig(abstractConfig, readConfig(abstractConfig));
    notifyListenersOfNewFile(getEntries(abstractConfig));
    LOGGER.error("##### End ConfigAbstractionAgent.install()");
  }

  @Override
  public void update(File abstractConfig) throws Exception {
    LOGGER.error("##### Start ConfigAbstractionAgent.update({})", abstractConfig);
    setPreviousConfig(abstractConfig);
    setCurrentConfig(abstractConfig, readConfig(abstractConfig));
    LOGGER.error("##### Calling listeners: {}", listeners);
    notifyListenersOfUpdatedFile(getEntries(abstractConfig));
    LOGGER.error("##### End ConfigAbstractionAgent.update()");
  }

  @Override
  public void uninstall(File abstractConfig) throws Exception {
    LOGGER.error("##### Start ConfigAbstractionAgent.uninstall({})", abstractConfig);
    LOGGER.error("##### Previous Configs before remove: {}", previousConfigs);
    previousConfigs.remove(FilenameUtils.getBaseName(abstractConfig.getName()));
    LOGGER.error("##### Previous Configs after remove: {}", previousConfigs);
    LOGGER.error("##### Current Configs before remove: {}", currentConfigs);
    currentConfigs.remove(FilenameUtils.getBaseName(abstractConfig.getName()));
    LOGGER.error("##### Current Configs after remove: {}", currentConfigs);
    LOGGER.error("##### End ConfigAbstractionAgent.uninstall()");
  }

  @Override
  public boolean canHandle(File abstractConfig) {
    return abstractConfig.getName().endsWith(".yml") || abstractConfig.getName().endsWith(".yaml");
  }

  @Override
  public <T> T get(String basename, String property, Class<T> clazz) {
    LOGGER.error("##### Start ConfigAbstractionAgent.get({}, {}, {})", basename, property, clazz);
    final Map<String, Object> properties = currentConfigs.get(basename);

    if (properties == null) {
      LOGGER.error("##### Returning: null");
      LOGGER.error("##### End ConfigAbstractionAgent.get()");
      return null;
    }
    LOGGER.error("##### Returning: {}", properties.get(property));
    LOGGER.error("##### End ConfigAbstractionAgent.get()");
    return (T) properties.get(property);
  }

  private Map<String, Set<String>> getEntries(File file) {
    String basename = FilenameUtils.getBaseName(file.getName());
    Set<String> entries = new HashSet<>();
    Set<String> diff = difference(basename);
    Set<String> newEntries = newEntries(basename);
    Set<String> removedEntries = removedEntries(basename);
    entries.addAll(diff);
    entries.addAll(newEntries);
    entries.addAll(removedEntries);
    Map<String, Set<String>> map = new HashMap<>();
    map.put(basename, entries);
    return map;
  }

  private void setCurrentConfig(File file, Map<String, Object> config) {
    String basename = FilenameUtils.getBaseName(file.getName());
    currentConfigs.put(basename, config);
    LOGGER.error("##### Put {}={} in currentConfigs.", basename, currentConfigs.get(basename));
  }

  private void setPreviousConfig(File file) {
    String basename = FilenameUtils.getBaseName(file.getName());
    previousConfigs.put(basename, currentConfigs.get(basename));
    LOGGER.error("##### Put {}={} in previousConfigs.", basename, previousConfigs.get(basename));
  }

  private void notifyListenersOfNewFile(Map<String, Set<String>> entries) {
    LOGGER.error("##### In ConfigAbstractionAgent.notifyListenersOfNewFile()");
    listeners.forEach(l -> l.handleInstall(entries));
  }

  private void notifyListenersOfUpdatedFile(Map<String, Set<String>> entries) {
    LOGGER.error("##### In ConfigAbstractionAgent.notifyListenersOfUpdatedFile()");
    listeners.forEach(l -> l.handleUpdate(entries));
  }

  private void notifyListenersOfDeletedFile(Map<String, Set<String>> entries) {
    LOGGER.error("##### In ConfigAbstractionAgent.notifyListenersOfDeletedFile()");
    listeners.forEach(l -> l.handleUninstall(entries));
  }

  private Map<String, Object> readConfig(File abstractConfig) throws IOException {
    LOGGER.error("##### Loading abstract config file: {}", abstractConfig.getCanonicalFile());
    try (InputStream is = new FileInputStream(abstractConfig.getCanonicalFile())) {
      Dictionary<String, Object> dictionary = configReader.read(is);
      return convertToMap(abstractConfig.getName(), dictionary);
    }
  }

  private Map<String, Object> convertToMap(String filename, Dictionary<String, Object> dictionary) {
    Enumeration<String> keys = dictionary.keys();
    Map<String, Object> map = new HashMap<>();
    while (keys.hasMoreElements()) {
      String key = keys.nextElement();
      //      map.put(FilenameUtils.getBaseName(filename) + "." + key, dictionary.get(key));
      map.put(key, dictionary.get(key));
    }
    LOGGER.error("##### Abstract configuration map: {}", map.toString());
    return ImmutableMap.<String, Object>builder().putAll(map).build();
  }

  private Set<String> difference(String basename) {
    MapDifference<String, Object> difference =
        Maps.difference(
            previousConfigs.getOrDefault(basename, Collections.emptyMap()),
            currentConfigs.get(basename));
    Set<String> entries = difference.entriesDiffering().keySet();
    LOGGER.error("##### Entries with Differing Values: {}", entries.toString());
    return entries;
  }

  private Set<String> newEntries(String basename) {
    MapDifference<String, Object> difference =
        Maps.difference(
            previousConfigs.getOrDefault(basename, Collections.emptyMap()),
            currentConfigs.get(basename));
    Set<String> newEntries = difference.entriesOnlyOnRight().keySet();
    LOGGER.error("##### New Entries: {}", newEntries.toString());
    return newEntries;
  }

  private Set<String> removedEntries(String basename) {
    MapDifference<String, Object> difference =
        Maps.difference(
            previousConfigs.getOrDefault(basename, Collections.emptyMap()),
            currentConfigs.get(basename));
    Set<String> removedEntries = difference.entriesOnlyOnLeft().keySet();
    LOGGER.error("##### Removed Entries: {}", removedEntries.toString());
    return removedEntries;
  }
}
