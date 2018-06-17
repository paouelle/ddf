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
package org.codice.ddf.config.mapping;

import java.util.Optional;

public interface ConfigMappingService {
  /**
   * Location in an OSGI bundle for mapping rule documents used to provided default mapped
   * properties. The configuration mapping service will process each entry in the mappings document
   * directory.
   */
  public static final String MAPPINGS_DOCUMENTS_LOCATION = "OSGI-INF/mappings";

  /**
   * Binds a new provider to this mapping service or rebind it if it was already bound.
   *
   * @param provider the provider to be bound
   */
  public void bind(ConfigMappingProvider provider);

  /**
   * Unbinds a provider from this mapping service.
   *
   * @param provider the provider to be unbound
   * @return <code>true</code> if the provider was unbound; <code>false</code> if it wasn't already
   *     bound
   */
  public boolean unbind(ConfigMappingProvider provider);

  /**
   * Gets a configuration mapping given its name.
   *
   * @param name the unique name for the config mapping to retrieve
   * @return the corresponding config mapping or empty if none available
   */
  public Optional<ConfigMapping> getMapping(String name);

  /**
   * Gets a configuration mapping given its name and specific instance.
   *
   * @param name the unique name for the config mapping to retrieve
   * @param instance the identifier of the specific instance to retrieve the config mapping for
   * @return the corresponding config mapping or empty if none available
   */
  public Optional<ConfigMapping> getMapping(String name, String instance);

  /**
   * Gets a configuration mapping given its identifier.
   *
   * @param id the unique id for the config mapping to retrieve
   * @return the corresponding config mapping or empty if none available
   */
  public Optional<ConfigMapping> getMapping(ConfigMapping.Id id);
}
