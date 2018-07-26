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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: This is an example of a Config Mapping Provider that only provides default values. These
 * values will be overridden by upstream projects.
 */
public class PlatformConfigurationConfigMappingProvider implements ConfigMappingProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PlatformConfigurationConfigMappingProvider.class);

  private static final String BACKGROUND = "background";

  private static final String COLOR = "color";

  private static final String FOOTER = "footer";

  private static final String HEADER = "header";

  private static final String SYS_USAGE_ENABLED = "systemUsageEnabled";

  private static final String SYS_USAGE_MESSAGE = "systemUsageMessage";

  private static final String SYS_USAGE_ONCE_PER_SESSION = "systemUsageOncePerSession";

  private static final String SYS_USAGE_TITLE = "systemUsageTitle";

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
    properties.put(BACKGROUND, DEFAULT_BACKGROUND_COLOR);
    properties.put(COLOR, DEFAULT_COLOR);
    properties.put(FOOTER, DEFAULT_FOOTER);
    properties.put(HEADER, DEFAULT_HEADER);
    properties.put(SYS_USAGE_ENABLED, DEFAULT_SYS_USAGE_ENABLED);
    properties.put(SYS_USAGE_MESSAGE, DEFAULT_SYS_USAGE_MESSAGE);
    properties.put(SYS_USAGE_ONCE_PER_SESSION, DEFAULT_USAGE_ONCE_PER_SESSION);
    properties.put(SYS_USAGE_TITLE, DEFAULT_SYS_USAGE_TITLE);
    return properties;
  }
}
