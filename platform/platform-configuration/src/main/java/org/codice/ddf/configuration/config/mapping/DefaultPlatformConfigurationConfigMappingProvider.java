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
package org.codice.ddf.configuration.config.mapping;

import java.util.HashMap;
import java.util.Map;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.configuration.PlatformUiConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: This is an example of a Config Mapping Provider that only provides default values. These
 * values will be overridden by upstream projects.
 */
public class DefaultPlatformConfigurationConfigMappingProvider implements ConfigMappingProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultPlatformConfigurationConfigMappingProvider.class);

  private static final String SYS_USAGE_ENABLED = "systemUsageEnabled";

  private static final String DEFAULT_BACKGROUND_COLOR = "GREEN";

  private static final String DEFAULT_COLOR = "WHITE";

  private static final String DEFAULT_FOOTER = "UNCLASSIFIED";

  private static final String DEFAULT_HEADER = "UNCLASSIFIED";

  private static final boolean DEFAULT_SYS_USAGE_ENABLED = true;

  private static final String DEFAULT_SYS_USAGE_MESSAGE = "Default system usage message";

  private static final boolean DEFAULT_USAGE_ONCE_PER_SESSION = false;

  private static final String DEFAULT_SYS_USAGE_TITLE = "Default system usage title";

  private static final String PID = "ddf.platform.ui.config";

  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
    LOGGER.debug(
        "Received: {}; can provide for: {}; returning: {}",
        id.getName(),
        PID,
        id.getName().equals(PID));
    return id.getName().equals(PID);
  }

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService configService)
      throws ConfigMappingException {
    final Map<String, Object> properties = new HashMap<>();
    properties.put(PlatformUiConfiguration.BACKGROUND_CONFIG_KEY, DEFAULT_BACKGROUND_COLOR);
    properties.put(PlatformUiConfiguration.COLOR_CONFIG_KEY, DEFAULT_COLOR);
    properties.put(PlatformUiConfiguration.FOOTER_CONFIG_KEY, DEFAULT_FOOTER);
    properties.put(PlatformUiConfiguration.HEADER_CONFIG_KEY, DEFAULT_HEADER);
    properties.put(SYS_USAGE_ENABLED, DEFAULT_SYS_USAGE_ENABLED);
    properties.put(
        PlatformUiConfiguration.SYSTEM_USAGE_MESSAGE_CONFIG_KEY, DEFAULT_SYS_USAGE_MESSAGE);
    properties.put(
        PlatformUiConfiguration.SYSTEM_USAGE_ONCE_PER_SESSION_CONFIG_KEY,
        DEFAULT_USAGE_ONCE_PER_SESSION);
    properties.put(PlatformUiConfiguration.SYSTEM_USAGE_TITLE_CONFIG_KEY, DEFAULT_SYS_USAGE_TITLE);
    return properties;
  }
}
