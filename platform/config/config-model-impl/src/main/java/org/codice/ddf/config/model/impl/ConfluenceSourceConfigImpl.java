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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.codice.ddf.config.model.ConfluenceSourceConfig;

public class ConfluenceSourceConfigImpl extends SourceConfigImpl implements ConfluenceSourceConfig {

  private String username = "";

  private String password = "";

  private List<String> excludedSpaces = Collections.EMPTY_LIST;

  private List<String> attributeOverrides = Collections.EMPTY_LIST;

  @Override
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  @Override
  public Stream<String> excludedSpaces() {
    return excludedSpaces.stream();
  }

  public void setExcludedSpaces(List<String> excludedSpaces) {
    this.excludedSpaces = excludedSpaces;
  }

  @Override
  public Stream<String> attributeOverrides() {
    return attributeOverrides.stream();
  }

  public void setAttributeOverrides(List<String> attributeOverrides) {
    this.attributeOverrides = attributeOverrides;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode()
        + Objects.hash(username, password, excludedSpaces, attributeOverrides);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ConfluenceSourceConfigImpl) {
      final ConfluenceSourceConfigImpl cfg = (ConfluenceSourceConfigImpl) obj;
      return Objects.equals(username, cfg.username)
          && Objects.equals(password, cfg.password)
          && Objects.equals(excludedSpaces, cfg.excludedSpaces)
          && Objects.equals(attributeOverrides, cfg.attributeOverrides)
          && super.equals(obj);
    }
    return false;
  }
}
