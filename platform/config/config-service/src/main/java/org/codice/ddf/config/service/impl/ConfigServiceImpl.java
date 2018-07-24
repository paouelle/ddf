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
package org.codice.ddf.config.service.impl;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.codice.ddf.config.Config;
import org.codice.ddf.config.ConfigEvent;
import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigListener;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.ConfigSingleton;
import org.codice.ddf.config.reader.ConfigReader;
import org.codice.ddf.platform.util.StandardThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigServiceImpl implements ConfigService, ArtifactInstaller {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigServiceImpl.class);

  private static final int THREAD_POOL_DEFAULT_SIZE = 16;

  private static final ExecutorService EXECUTOR = ConfigServiceImpl.createExecutor();

  private final ConfigTracker configTracker = new ConfigTracker();

  private final ConfigReader configReader;

  private final List<ConfigListener> configListeners;

  private final Set<Path> paths;

  public ConfigServiceImpl(
      ConfigReader configReader, List<ConfigListener> configListeners, Set<String> paths) {
    LOGGER.debug("ConfigServiceImpl({}, {}, {})", configReader, configListeners, paths);
    this.configReader = configReader;
    this.configListeners = configListeners;
    this.paths = paths.stream().map(File::new).map(File::toPath).collect(Collectors.toSet());
  }

  @Override
  public <T extends ConfigSingleton> Optional<T> get(Class<T> clazz) {
    final Optional<T> cfg = configTracker.get(clazz);

    LOGGER.debug("ConfigServiceImpl:get({}) = {}", clazz, cfg);
    return cfg;
  }

  @Override
  public <T extends ConfigGroup> Optional<T> get(Class<T> clazz, String id) {
    final Optional<T> cfg = configTracker.get(clazz, id);

    LOGGER.debug("ConfigServiceImpl:get({}, {}) = {}", clazz, id, cfg);
    return cfg;
  }

  @Override
  public <T extends ConfigGroup> Stream<T> configs(Class<T> clazz) {
    LOGGER.debug("ConfigServiceImpl:configs({})", clazz);
    return configTracker.configs(clazz);
  }

  @Override
  public void install(File config) {
    LOGGER.debug("ConfigServiceImpl:install({})", config);
    Set<Config> configs = read(config);
    ConfigEvent configEvent = configTracker.install(config.getName(), configs);
    configChanged(configEvent);
  }

  @Override
  public void update(File config) {
    LOGGER.debug("ConfigServiceImpl:update({})", config);
    Set<Config> configs = read(config);
    ConfigEvent configEvent = configTracker.update(config.getName(), configs);
    configChanged(configEvent);
  }

  @Override
  public void uninstall(File config) {
    LOGGER.debug("ConfigServiceImpl:uninstall({})", config);
    ConfigEvent configEvent = configTracker.remove(config.getName());
    configChanged(configEvent);
  }

  @Override
  public boolean canHandle(File config) {
    final Path path = config.toPath();
    final String name = config.getName();

    return (name.endsWith(".yml") || name.endsWith(".yaml"))
        && paths.stream().anyMatch(path::startsWith);
  }

  private void configChanged(@Nullable ConfigEvent configEvent) {
    LOGGER.debug("ConfigServiceImpl:configChanged({})", configEvent);
    if (configEvent != null) {
      for (ConfigListener listener : configListeners) {
        ConfigServiceImpl.EXECUTOR.execute(() -> listener.configChanged(configEvent));
      }
    }
  }

  private Set<Config> read(File config) {
    LOGGER.debug("ConfigServiceImpl:read({})", config);
    try {
      final Set<Config> cfgs = configReader.read(config);

      LOGGER.debug("ConfigServiceImpl:read({}) = {}", config, cfgs);
      return cfgs;
    } catch (IOException e) {
      LOGGER.error("Unable to read configuration file: {}", config.getAbsoluteFile());
      return ImmutableSet.of();
    }
  }

  private static ExecutorService createExecutor() throws NumberFormatException {
    return Executors.newFixedThreadPool(
        ConfigServiceImpl.THREAD_POOL_DEFAULT_SIZE,
        StandardThreadFactoryBuilder.newThreadFactory("ConfigService"));
  }
}
