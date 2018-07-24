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
package org.codice.ddf.confluence.source.config.mapping;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingUnavailableException;
import org.codice.ddf.config.model.ConfluenceSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfluenceConfigMappingProvider implements ConfigMappingProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ConfluenceConfigMappingProvider.class);

  private static final String ATTRIBUTE_OVERRIDES = "attributeOverrides";

  private static final String AVAILABILITY_POLL_INTERVAL = "availabilityPollInterval";

  private static final String BODY_EXPANSION = "bodyExpansion";

  private static final String DEFAULT_BODY_EXPANSION = "body.view";

  private static final String ENDPOINT_URL = "endpointUrl";

  private static final String EXCLUDE_SPACES = "excludeSpaces";

  private static final String CONFLUENCE_SPACES = "ConfluenceSpaces";

  private static final String ID = "id";

  private static final String INCLUDE_ARCHIVED_SPACES = "includeArchivedSpaces";

  private static final String INCLUDE_PAGE_CONTENT = "includePageContent";

  private static final String SHORTNAME = "shortname";

  private static final String USERNAME = "username";

  private static final String PASSWORD = "password";

  private static final String CONFLUENCE_SOURCE_FACTORY_PID = "Confluence_Federated_Source";

  private static final Long DEFAULT_POLL_INTERVAL = new Long(60000);

  private static final String EXPANDED_SECTIONS = "expandedSections";

  private static final String[] DEFAULT_EXPANDED_SECTIONS = {
      "metadata.labels",
      "space",
      "history.contributors.publishers.users",
      "history.lastUpdated",
      "restrictions.read.restrictions.group",
      "restrictions.read.restrictions.user"
  };

  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
    final boolean canProvideFor = id.getName().equals(CONFLUENCE_SOURCE_FACTORY_PID);
    LOGGER.debug(
        "Can Config Mapping Provider [{}] provide a configuration for [{}]? {}",
        ConfluenceConfigMappingProvider.class.getName(),
        id.getName(),
        canProvideFor);
    return canProvideFor;
  }

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService configService)
      throws ConfigMappingException {
    final String instanceId = getInstanceId(id);
    final ConfluenceSourceConfig config =
        configService.get(ConfluenceSourceConfig.class, instanceId).orElseThrow(
            () ->
                new ConfigMappingUnavailableException(
                    "confluence source config '"
                        + instanceId
                        + "' not found for config mapping object: "
                        + id));

    final URL url = config.getUrl();
    final String username = config.getUsername();
    final String password = config.getPassword();

    final Map<String, Object> properties = new HashMap<>();
    properties.put(ENDPOINT_URL, url.toString());
    properties.put(ID, instanceId);
    properties.put(SHORTNAME, instanceId);
    properties.put(USERNAME, username);
    properties.put(PASSWORD, password);
    properties.put(INCLUDE_ARCHIVED_SPACES, Boolean.FALSE);
    properties.put(INCLUDE_PAGE_CONTENT, Boolean.FALSE);
    properties.put(EXPANDED_SECTIONS, DEFAULT_EXPANDED_SECTIONS);
    properties.put(AVAILABILITY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL);
    properties.put(BODY_EXPANSION, DEFAULT_BODY_EXPANSION);
    setExcludedSpaces(config, properties);
    setAttributeOverrides(config, properties);

    LOGGER.debug(
        "Returning properties map [{}] for Confluence Source [{}].",
        properties.toString(),
        instanceId);

    return properties;
  }

  private String getInstanceId(ConfigMapping.Id id) {
    return id.getInstance()
        .orElseThrow(
            () -> new ConfigMappingUnavailableException(
                "No instance found in Config Mapping ID: " + id));
  }

  private void setExcludedSpaces(ConfluenceSourceConfig config, Map<String, Object> properties) {
    if (config.excludedSpaces().count() > 0) {
      properties.put(EXCLUDE_SPACES, Boolean.TRUE);
    } else {
      properties.put(EXCLUDE_SPACES, Boolean.FALSE);
    }
    properties.put(CONFLUENCE_SPACES, config.excludedSpaces().toArray(String[]::new));
  }

  private void setAttributeOverrides(
      ConfluenceSourceConfig config, Map<String, Object> properties) {
    properties.put(ATTRIBUTE_OVERRIDES, config.attributeOverrides().toArray(String[]::new));
  }
}
