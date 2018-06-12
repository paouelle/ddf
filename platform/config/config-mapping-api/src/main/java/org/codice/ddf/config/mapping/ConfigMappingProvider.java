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

import java.util.Map;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping.Id;

/**
 * Provides access to mapped configuration properties for either a configuration mapping or for a
 * given instance of a configuration mapping. This interface can be registered as a service with
 * {@link #MAPPING_NAME} and optionally {@link #MAPPING_INSTANCE} service properties. Any names
 * and/or instances mentioned must be valid arguments to the {@link #provide} method.
 *
 * <p>The {@linkConfigMappingService} will apply priority on a per property-basis which would allow
 * additional registered providers to override some but not all properties. Comparison of providers
 * is based on ranking. A provider with a ranking of {@link Integer#MAX_VALUE} will have its mapped
 * properties override all others, whereas a service with a ranking of {@link Integer#MIN_VALUE} is
 * very likely to have its mapped properties overridden. When a tie in ranking exist, whichever
 * provider was first bound to the {@link ConfigMappingService} directly will be considered to have
 * a higher priority.
 *
 * <p>For providers registered as OSGI services, ranking and which configuration mappings as
 * returned by the {@link #getRank()}, {@link #canProvideFor(ConfigMapping)}, and {@link
 * #canProvideFor(Id)} methods are ignored in favor of using the service ranking and {@link
 * #MAPPING_NAME}, and {@link #MAPPING_INSTANCE} service properties registered with the service.
 * When a ranking tie in exist, whichever service that was first registered with the service
 * registry will be considered to have a higher priority.
 */
public interface ConfigMappingProvider extends Comparable<ConfigMappingProvider> {
  /**
   * Service property to signal that this service is able to provide mapped dictionaries for the
   * given mapping name (e.g. managed service factory PID). The type of this service property is
   * <code>String+</code>.
   */
  public static final String MAPPING_NAME = "mapping.name";

  /**
   * Optional service property to signal that this service is able to provide mapped dictionaries
   * for the given instance of the corresponding configuration mapping name (e.g. instance of a
   * given managed service factory). If not specified, this service can provide mapped dictionaries
   * for all instances of the corresponding configuration mapping name. The type of this service
   * property is <code>String+</code>.
   */
  public static final String MAPPING_INSTANCE = "mapping.instance";

  /**
   * Gets a ranking priority for this provider (see class description for more details).
   *
   * <p><i>Note:</i> The provider's rank is not expected to change during the life of this provider
   * unless the provider is first unbound from the {@link ConfigMappingService} and then rebound.
   *
   * @return this provider's ranking priority
   */
  public int getRank();

  /**
   * Checks if this provider can provide mapped dictionaries for a given configuration mapping.
   *
   * <p><i>Note:</i> A provider is expected not to change which configuration mappings it provides
   * for unless the provider is first unbound from the {@link ConfigMappingService} and then
   * rebound. Rebinding a provider will re-compute which config mapping is impacted by this change.
   *
   * @param mapping the config mapping to check if this provider can provide for
   * @return <code>true</code> if this provider can provide mapped dictionaries for the specified
   *     config mapping; <code>false</code> otherwise
   */
  public boolean canProvideFor(ConfigMapping mapping);

  /**
   * Checks if this provider can provide mapped dictionaries for a given configuration mapping.
   *
   * <p><i>Note:</i> A provider is expected not to change which configuration mappings it provides
   * for unless the provider is first unbound from the {@link ConfigMappingService} and then
   * rebound. Rebinding a provider will re-compute which config mapping is impacted by this change.
   *
   * @param id the id of the config mapping to check if this provider can provide for
   * @return <code>true</code> if this provider can provide mapped dictionaries for the specified
   *     config mapping; <code>false</code> otherwise
   */
  public boolean canProvideFor(ConfigMapping.Id id);

  /**
   * Provides the mapped dictionary for a given configuration mapping.
   *
   * @param id the unique config mapping id for the mapped properties to provide
   * @param config the configuration to use when computing the mapper properties
   * @return a map corresponding to all mapped properties and their current values after
   *     re-evaluating any internal rules defined
   * @throws ConfigMappingException if a failure occurred while resolving this config mapping
   */
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService config)
      throws ConfigMappingException;

  /**
   * {@inheritDoce}
   *
   * <p>The comparison must support the ranking priority described in the class description.
   *
   * @param provider the other provider to compare this provider with
   * @return <code>1</code> if this provider has a higher ranking priority than the one provided;
   *     <code>-1</code> if it has a lower ranking priority and <code>0</code> if they have equal
   *     priorities
   */
  @Override
  int compareTo(ConfigMappingProvider provider);
}
