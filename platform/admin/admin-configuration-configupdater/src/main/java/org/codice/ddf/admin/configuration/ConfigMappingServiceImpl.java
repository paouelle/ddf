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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMappingServiceImpl
    implements ConfigMappingService, ConfigAbstractionListener, ArtifactInstaller {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingServiceImpl.class);

  private final Map<String, ConfigMappingImpl> mappings = new ConcurrentHashMap<>();

  private final ConfigAbstractionAgent agent;

  private final List<ConfigMappingListener> listeners;

  private final Path path;

  public ConfigMappingServiceImpl(
      ConfigAbstractionAgent agent, List<ConfigMappingListener> listeners) {
    LOGGER.debug("ConfigMappingServiceImpl()");
    this.agent = agent;
    this.listeners = listeners;
    this.path = Paths.get(System.getProperty("ddf.home"), "etc", "mapping");
  }

  @SuppressWarnings("unused" /* called by blueprint */)
  public void init() {
    LOGGER.debug("ConfigMappingServiceImpl::init()");
    Stream.of(path.toFile().list()) // pre-load all mappings
        .map(FilenameUtils::getBaseName)
        .forEach(name -> mappings.put(name, new ConfigMappingImpl(agent, name)));
  }

  @Override
  public Optional<ConfigMapping> getMapping(String id) {
    return Optional.ofNullable(mappings.get(id));
  }

  @Override
  public void handleInstall(Map<String, Set<String>> entries) {
    LOGGER.debug("ConfigMappingServiceImpl::handleInstall({})", entries);
    updated(entries);
  }

  @Override
  public void handleUpdate(Map<String, Set<String>> entries) {
    LOGGER.debug("ConfigMappingServiceImpl::handleUpdate({})", entries);
    updated(entries);
  }

  @Override
  public void handleUninstall(Map<String, Set<String>> entries) {
    LOGGER.debug("ConfigMappingServiceImpl::handleUninstall({})", entries);
    updated(entries);
  }

  @Override
  public boolean canHandle(File artifact) {
    final Path artifactPath = artifact.toPath();
    final String name = artifact.getName();

    return artifactPath.startsWith(path) && name.endsWith(".mapping");
  }

  @Override
  public void install(File artifact) throws Exception {
    final String id = FilenameUtils.getBaseName(artifact.getName());

    LOGGER.debug("ConfigMappingServiceImpl::install({}) - {}", artifact, id);
    updated(id, artifact);
  }

  @Override
  public void update(File artifact) throws Exception {
    final String id = FilenameUtils.getBaseName(artifact.getName());

    LOGGER.debug("ConfigMappingServiceImpl::update({}) - {}", artifact, id);
    updated(id, artifact);
  }

  @Override
  public void uninstall(File artifact) throws Exception {
    final String id = FilenameUtils.getBaseName(artifact.getName());

    LOGGER.debug("ConfigMappingServiceImpl::uninstall({}) - {}", artifact, id);
    final ConfigMapping mapping = mappings.remove(id);

    if (mapping != null) {
      notifyRemoved(mapping);
    }
  }

  private void updated(Map<String, Set<String>> ids) {
    LOGGER.debug("ConfigMappingServiceImpl::updated({})", ids);
    mappings.values().stream().filter(m -> m.shouldBeUpdated(ids)).forEach(this::notifyUpdated);
  }

  private void updated(String id, File artifact) throws IOException {
    try {
      notifyUpdated(
          mappings.compute(
              id,
              (i, m) -> {
                if (m == null) {
                  m = new ConfigMappingImpl(agent, artifact);
                } else {
                  m.loadRules();
                }
                return m;
              }));
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private void notifyUpdated(ConfigMapping mapping) {
    LOGGER.debug("ConfigMappingServiceImpl::notifyUpdated({})", mapping);
    listeners.forEach(l -> l.updated(mapping));
  }

  private void notifyRemoved(ConfigMapping mapping) {
    LOGGER.debug("ConfigMappingServiceImpl::notifyRemoved({})", mapping);
    listeners.forEach(l -> l.removed(mapping));
  }
}
