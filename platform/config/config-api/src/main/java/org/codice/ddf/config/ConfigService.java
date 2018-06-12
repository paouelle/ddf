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
package org.codice.ddf.config;

import java.util.Optional;
import java.util.stream.Stream;

/** Service interface for configuration. */
public interface ConfigService {
  /**
   * Retrieves a configuration of a given type.
   *
   * @param <T> the class of configuration to retrieve
   * @param type the class of configuration to retrieve
   * @return the corresponding configuration or empty if it doesn't exist
   */
  public <T extends Config> Optional<T> get(Class<T> type);

  /**
   * Retrieves a specific configuration instance of a given type.
   *
   * @param <T> the class of configuration to retrieve
   * @param type the class of configuration to retrieve
   * @param id the unique instance id for the configuration to retrieve
   * @return the corresponding configuration instance or empty if none exist
   */
  public <T extends ConfigInstance> Optional<T> get(Class<T> type, String id);

  /**
   * Retrieves all instances of a given type of configuration.
   *
   * @param <T> the class of configuration to retrieve
   * @param type the class of configuration to retrieve
   * @return a stream of all configuration instances of the given type
   */
  public <T extends ConfigInstance> Stream<T> configs(Class<T> type);
}
