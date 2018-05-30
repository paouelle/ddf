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

import io.fabric8.karaf.core.properties.function.PropertiesFunction;
import java.util.Objects;

/** $[cfg:id.key] or $[cfg:id.key:default] */
public class ConfigAbstractionPropertiesFunction implements PropertiesFunction {
  private final ConfigAbstractionAgent config;

  public ConfigAbstractionPropertiesFunction(ConfigAbstractionAgent config) {
    this.config = config;
  }

  @Override
  public String getName() {
    return "cfg";
  }

  @Override
  public String apply(String s) {
    final int i = s.indexOf('.');
    final int j = s.indexOf(':', i);

    if (i == -1) {
      return null;
    }
    final String id = s.substring(0, i);
    final String key;
    final String dflt;

    if (j == -1) { // no defaults
      key = s.substring(i + 1);
      dflt = null;
    } else {
      key = s.substring(i + 1, j);
      dflt = s.substring(j);
    }
    return Objects.toString(config.get(id, key, Object.class), dflt);
  }
}
