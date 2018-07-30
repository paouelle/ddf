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
package org.codice.ddf.config.model.impl;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.codice.ddf.config.ConfigSingleton;

public abstract class AbstractConfigSingleton extends AbstractConfig implements ConfigSingleton {

  @Override
  public int hashCode() {
    return 31 * super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof AbstractConfigSingleton) {
      final AbstractConfigSingleton cfg = (AbstractConfigSingleton) obj;
      return super.equals(obj);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    // Temporary while prototyping
    return ToStringBuilder.reflectionToString(
        this, ToStringStyle.DEFAULT_STYLE, false, AbstractConfigSingleton.class);
  }
}
