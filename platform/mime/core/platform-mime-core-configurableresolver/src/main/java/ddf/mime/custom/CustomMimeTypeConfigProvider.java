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
package ddf.mime.custom;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping.Id;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingUnavailableException;
import org.codice.ddf.config.model.MimeTypeConfig;
import org.codice.ddf.config.model.SchemaMimeTypeConfig;

public class CustomMimeTypeConfigProvider implements ConfigMappingProvider {
  private static final String FACTORY_PID = "DDF_Custom_Mime_Type_Resolver";

  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(Id id) {
    return CustomMimeTypeConfigProvider.FACTORY_PID.equals(id.getName());
  }

  @Override
  public Map<String, Object> provide(Id id, ConfigService config) throws ConfigMappingException {
    final String instance =
        id.getInstance()
            .orElseThrow(
                () ->
                    new ConfigMappingUnavailableException(
                        "missing instance id for config mapping object: " + id));
    final MimeTypeConfig mime =
        config
            .get(MimeTypeConfig.class, instance)
            .orElseThrow(
                () ->
                    new ConfigMappingUnavailableException(
                        "mime config '"
                            + instance
                            + "' not found for config mapping object: "
                            + id));
    final Map<String, Object> properties = new HashMap<>(8);

    properties.put(
        "customMimeTypes",
        mime.mappings().map(CustomMimeTypeConfigProvider::toString).toArray(String[]::new));
    properties.put("name", mime.getName());
    properties.put("priority", mime.getPriority());
    if (mime instanceof SchemaMimeTypeConfig) {
      properties.put("schema", ((SchemaMimeTypeConfig) mime).getSchema());
    }
    return properties;
  }

  private static String toString(MimeTypeConfig.Mapping mapping) {
    return StringUtils.removeStart(mapping.getExtension(), ".") + '=' + mapping.getMimeType();
  }
}
