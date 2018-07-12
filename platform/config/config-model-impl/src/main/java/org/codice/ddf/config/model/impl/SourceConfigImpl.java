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

import java.net.URL;
import java.util.Objects;
import org.codice.ddf.config.model.SourceConfig;

public abstract class SourceConfigImpl extends AbstractConfigGroup implements SourceConfig {

  private URL url;

  public SourceConfigImpl() {}

  public SourceConfigImpl(String id, URL url, String version) {
    super(id, version);
    this.url = url;
  }

  @Override
  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + Objects.hash(url);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof SourceConfigImpl) {
      final SourceConfigImpl cfg = (SourceConfigImpl) obj;
      return Objects.equals(url, cfg.url) && super.equals(obj);
    }
    return false;
  }
}
