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
package org.codice.ddf.config.mapping.groovy;

import javax.annotation.Nullable;
import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.ConfigSingleton;

public class GroovyConfigService {
  private final ConfigService config;

  public GroovyConfigService(ConfigService config) {
    this.config = config;
  }

  @Nullable
  public ConfigSingleton get(Class<? extends ConfigSingleton> clazz) {
    return config.get(clazz).orElse(null);
  }

  @Nullable
  public ConfigGroup get(Class<? extends ConfigGroup> clazz, String id) {
    return config.get(clazz, id).orElse(null);
  }

  public ConfigGroup[] getAll(Class<? extends ConfigGroup> clazz) {
    return config.configs(clazz).toArray(ConfigGroup[]::new);
  }
}
