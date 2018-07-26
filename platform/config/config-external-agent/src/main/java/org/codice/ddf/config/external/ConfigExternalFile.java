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
package org.codice.ddf.config.external;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.felix.utils.properties.Properties;
import org.boon.Boon;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigExternalFile {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigExternalFile.class);

  private final Path path;

  private final Type type;

  private final boolean overwrite;

  public ConfigExternalFile(Map.Entry<String, List<Object>> cfg) {
    this.path = ConfigExternalAgent.DDF_DIR.resolve(FilenameUtils.separatorsToSystem(cfg.getKey()));
    this.type = Type.valueOf((String) cfg.getValue().get(0));
    this.overwrite = (Boolean) cfg.getValue().get(1);
  }

  public ConfigExternalFile(Path path, Type type, boolean overwrite) {
    this.path = path;
    this.type = type;
    this.overwrite = overwrite;
  }

  public String getName() {
    return path.getFileName().toString();
  }

  public Path getPath() {
    return path;
  }

  public File getFile() {
    return path.toFile();
  }

  public boolean isOverwrite() {
    return overwrite;
  }

  public void update(ConfigMappingService mapping) {
    LOGGER.debug("ConfigExternalFile[" + path + "]:update(" + mapping + ")");
    final String name = getName();

    try {
      final Map<String, Object> properties =
          mapping.getMapping(name).map(ConfigMapping::resolve).orElse(null);

      if (mapping == null) {
        LOGGER.error("No configuration mapping found for file '{}'", name);
        return;
      }
      type.update(this, properties);
    } catch (IOException e) {
      LOGGER.error("Failed to update file '{}': {}", name, e.toString());
      LOGGER.debug("mapping failure: {}", e, e);
      throw new IOError(e);
    } catch (RuntimeException e) {
      LOGGER.error("Failed to update file '{}': {}", name, e.toString());
      LOGGER.debug("mapping failure: {}", e, e);
      throw e;
    }
  }

  @Override
  public String toString() {
    return "ConfigExternalFile[path=" + path + ", type=" + type + ", overwrite=" + overwrite + "]";
  }

  public enum Type {
    JSON {
      @Override
      public void update(ConfigExternalFile file, Map<String, Object> properties)
          throws IOException {
        LOGGER.debug("ConfigExternalFile.Type[JSON]::update(" + file + ", " + properties + ")");
        Map<String, Object> map = new TreeMap<>();

        if (!file.isOverwrite()) {
          map.putAll(
              ConfigExternalAgent.MAPPER
                  .parser()
                  .parseMap(FileUtils.readFileToString(file.getFile(), Charset.defaultCharset())));
        }
        properties.forEach((k, v) -> Type.putOrRemove(map, k, v));
        FileUtils.writeStringToFile(
            file.getFile(), Boon.toPrettyJson(map), Charset.defaultCharset());
      }
    },

    PROPERTIES {
      @Override
      public void update(ConfigExternalFile file, Map<String, Object> properties)
          throws IOException {
        LOGGER.debug(
            "ConfigExternalFile.Type[PROPERTIES]::update(" + file + ", " + properties + ")");
        Properties props;

        if (!file.isOverwrite()) {
          props = new Properties(file.getFile());
        } else {
          props = new Properties();
        }
        properties.forEach((k, v) -> Type.putOrRemove(props, k, Objects.toString(v, null)));
        props.save(file.getFile());
      }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(Type.class);

    public abstract void update(ConfigExternalFile file, Map<String, Object> properties)
        throws IOException;

    private static <T> void putOrRemove(Map<String, T> properties, String key, T value) {
      if (value != null) {
        properties.put(key, value);
      } else {
        properties.remove(key);
      }
    }
  }
}
