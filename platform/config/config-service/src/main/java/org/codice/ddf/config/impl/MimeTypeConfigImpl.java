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
package org.codice.ddf.config.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.codice.ddf.config.MimeTypeConfig;

public class MimeTypeConfigImpl implements MimeTypeConfig {
  private final String id;
  private final String name;
  private final int priority;
  private final MultiValuedMap<String, String> mimesToExtensions = new HashSetValuedHashMap<>();
  private final Map<String, String> extensionsToMimes = new HashMap<>();

  public MimeTypeConfigImpl(String id, String name, int priority, String... extsToMimes) {
    this.id = id;
    this.name = name;
    this.priority = priority;
    for (int i = 0; i < extsToMimes.length; i++) {
      final String ext = StringUtils.prependIfMissing(extsToMimes[i], ".");
      final String mime = extsToMimes[++i];

      extensionsToMimes.putIfAbsent(ext, mime);
      mimesToExtensions.put(mime, ext);
    }
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public Optional<String> getExtensionFor(String mimeType) {
    return mimesToExtensions.get(mimeType).stream().findFirst();
  }

  @Override
  public Optional<String> getMimeTypeFor(String extension) {
    return Optional.ofNullable(extensionsToMimes.get(StringUtils.prependIfMissing(extension, ".")));
  }

  @Override
  public Stream<Mapping> mappings() {
    return extensionsToMimes.entrySet().stream().map(MappingImpl::new);
  }

  private static class MappingImpl implements MimeTypeConfig.Mapping {
    private final String extension;

    private final String mimeType;

    MappingImpl(Map.Entry<String, String> entry) {
      this.extension = entry.getKey();
      this.mimeType = entry.getValue();
    }

    @Override
    public String getExtension() {
      return extension;
    }

    @Override
    public String getMimeType() {
      return mimeType;
    }
  }
}
