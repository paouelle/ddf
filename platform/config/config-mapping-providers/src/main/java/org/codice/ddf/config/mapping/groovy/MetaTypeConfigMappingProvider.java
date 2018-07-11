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

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaTypeConfigMappingProvider implements ConfigMappingProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetaTypeConfigMappingProvider.class);

  private final MetaTypeInformation metatypeInfo;

  private final Set<String> names;

  public MetaTypeConfigMappingProvider(MetaTypeInformation metatypeInfo) {
    this.metatypeInfo = metatypeInfo;
    final String[] pids = metatypeInfo.getPids();
    final String[] factoryPids = metatypeInfo.getFactoryPids();

    this.names =
        Stream.concat(
                (pids != null) ? Stream.of(pids) : Stream.<String>empty(),
                (factoryPids != null) ? Stream.of(factoryPids) : Stream.<String>empty())
            .collect(Collectors.toSet());
  }

  @Override
  public int getRanking() {
    // lowest ranking as it only provides default values
    return Integer.MIN_VALUE;
  }

  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
    return names.contains(id.getName());
  }

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService config)
      throws ConfigMappingException {
    LOGGER.debug("MetaTypeConfigMappingProvider::provide({}, {})", id, config);
    final Map<String, Object> properties = new HashMap<>();
    final String name = id.getName();
    final ObjectClassDefinition metatype;

    try {
      metatype = metatypeInfo.getObjectClassDefinition(name, Locale.getDefault().toString());
    } catch (IllegalArgumentException e) {
      LOGGER.debug("unexpected pid/factory pid '{}': {}", name, e, e);
      return properties;
    }
    try {
      for (final AttributeDefinition def :
          metatype.getAttributeDefinitions(ObjectClassDefinition.ALL)) {
        if (def == null) {
          continue;
        }
        final String key = def.getID();
        final int type = def.getType();
        final Class<?> clazz = MetaTypeConfigMappingProvider.getType(key, type);
        final String[] defaultValues = def.getDefaultValue();
        final Object value;

        if (defaultValues != null) {
          final int cardinality = def.getCardinality();

          if (cardinality == 0) {
            properties.put(
                key,
                (defaultValues.length > 0)
                    ? MetaTypeConfigMappingProvider.parse(key, defaultValues[0], type)
                    : null);
          } else {
            final Stream<Object> defaults =
                Stream.of(defaultValues)
                    .limit(Math.min(Math.abs(cardinality), defaultValues.length))
                    .map(v -> MetaTypeConfigMappingProvider.parse(key, v, type));

            if (cardinality < 0) {
              // Felix creates those as ArrayList and not as Vector.
              // We do the same in the AdminConsoleService
              properties.put(key, defaults.collect(Collectors.toList()));
            } else { // cardinality > 0
              // make sure the create an array of the proper element type and not an Object[]
              properties.put(
                  key,
                  defaults.toArray(
                      length -> MetaTypeConfigMappingProvider.newArray(clazz, length)));
            }
          }
        } else {
          properties.put(key, null);
        }
      }
    } catch (ConfigMappingException e) {
      LOGGER.error("failed to provide metatype default values for '{}': {}", id, e.getMessage());
      LOGGER.debug("parsing failure: {}", e, e);
      throw e;
    }
    LOGGER.debug("providing properties [{}] for mapping [{}]", properties, id);
    return properties;
  }

  private static <T> T[] newArray(Class<T> clazz, int length) {
    return (T[]) Array.newInstance(clazz, length);
  }

  @Nullable
  private static Object parse(String id, @Nullable String value, int type)
      throws ConfigMappingException {
    if ((type == AttributeDefinition.PASSWORD) || (type == AttributeDefinition.STRING)) {
      return value;
    }
    if (StringUtils.isNotEmpty(value)) {
      try {
        switch (type) {
          case AttributeDefinition.BOOLEAN:
            return Boolean.valueOf(value);
          case AttributeDefinition.BYTE:
            return Byte.valueOf(value);
          case AttributeDefinition.DOUBLE:
            return Double.valueOf(value);
          case AttributeDefinition.CHARACTER:
            return Character.valueOf(value.charAt(0));
          case AttributeDefinition.FLOAT:
            return Float.valueOf(value);
          case AttributeDefinition.INTEGER:
            return Integer.valueOf(value);
          case AttributeDefinition.LONG:
            return Long.valueOf(value);
          case AttributeDefinition.SHORT:
            return Short.valueOf(value);
          default:
            throw new ConfigMappingException(
                String.format(
                    "failure to convert '%s' to type '%d' for metatype attribute: %s; unknown type",
                    value, type, id));
        }
      } catch (NumberFormatException e) {
        throw new ConfigMappingException(
            String.format(
                "failure to convert '%s' to type '%d' for metatype attribute: %s", value, type, id),
            e);
      }
    }
    return null;
  }

  private static Class<?> getType(String id, int type) throws ConfigMappingException {
    switch (type) {
      case AttributeDefinition.BOOLEAN:
        return Boolean.class;
      case AttributeDefinition.BYTE:
        return Byte.class;
      case AttributeDefinition.DOUBLE:
        return Double.class;
      case AttributeDefinition.CHARACTER:
        return Character.class;
      case AttributeDefinition.FLOAT:
        return Float.class;
      case AttributeDefinition.INTEGER:
        return Integer.class;
      case AttributeDefinition.LONG:
        return Long.class;
      case AttributeDefinition.SHORT:
        return Short.class;
      case AttributeDefinition.PASSWORD:
      case AttributeDefinition.STRING:
        return String.class;
      default:
        throw new ConfigMappingException(
            String.format("unknown type '%d' for metatype attribute: %s", type, id));
    }
  }
}
