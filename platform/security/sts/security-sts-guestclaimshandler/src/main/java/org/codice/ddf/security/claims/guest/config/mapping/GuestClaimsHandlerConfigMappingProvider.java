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
package org.codice.ddf.security.claims.guest.config.mapping;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.boon.Boon;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.ConfigSingleton;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.model.NetworkProfileConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuestClaimsHandlerConfigMappingProvider implements ConfigMappingProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GuestClaimsHandlerConfigMappingProvider.class);

  private static final String PID = "ddf.security.sts.guestclaims";

  private static final String ATTRIBUTES = "attributes";

  private static final String PROFILE = "profile";

  private static final String GUEST_CLAIMS = "guestClaims";

  private static final String[] DEFAULT_CLAIMS =
      new String[] {
        "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier=guest",
        "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role=guest"
      };

  private final String profilesFilePath;

  private Map<String, Object> profiles;

  public GuestClaimsHandlerConfigMappingProvider(String profilesFilePath) {
    this.profilesFilePath = profilesFilePath;
  }

  public void init() {
    try (InputStream inputStream = new FileInputStream(profilesFilePath)) {
      String json = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      LOGGER.debug("profiles.json: {}", json);
      this.profiles = (Map<String, Object>) Boon.fromJson(json);
      LOGGER.debug("profiles: {}", this.profiles);
    } catch (IOException e) {
      LOGGER.debug(
          "Could not find {} during installation. Using default profile.", profilesFilePath, e);
      this.profiles = Collections.EMPTY_MAP;
    }
  }

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
    final Optional<NetworkProfileConfig> optionalNetworkProfileConfig =
        configService.get(NetworkProfileConfig.class);

    if (useDefaultProfile(optionalNetworkProfileConfig)) {
      LOGGER.debug("Default profile in use.");
      final Map<String, Object> properties = getPropertiesForDefaultProfile();
      LOGGER.debug("Returning {} to Config Mapping", properties);
      return properties;
    }

    final NetworkProfileConfig networkProfileConfig = optionalNetworkProfileConfig.get();
    final String profile = networkProfileConfig.getProfile();
    final String version = networkProfileConfig.getVersion();
    Class<? extends ConfigSingleton> clazz = networkProfileConfig.getType();
    LOGGER.debug("profile: {}", profile);
    LOGGER.debug("version: {}", version);
    LOGGER.debug("class: {}", clazz);
    final Map<String, Object> properties = getPropertiesForProfile(profile);
    LOGGER.debug("Returning {} to Config Mapping", properties);
    return properties;
  }

  private Map<String, Object> getProfileConfiguration(String profile) {
    final Map<String, Object> profileConfig = (Map<String, Object>) profiles.get(profile);
    LOGGER.debug("Configuration for profile {} is: {}", profile, profileConfig);
    return profileConfig;
  }

  private String[] getGuestClaims(String profile) {
    final Map<String, Object> profileConfig = getProfileConfiguration(profile);
    final Map<String, Object> guestClaims = (Map<String, Object>) profileConfig.get(GUEST_CLAIMS);

    final String[] guestClaimsArray =
        guestClaims
            .entrySet()
            .stream()
            .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
            .toArray(String[]::new);

    LOGGER.debug("Returning guest claims: {}", guestClaimsArray);
    return guestClaimsArray;
  }

  private String[] getDefaultGuestClaims() {
    LOGGER.debug("Returning default guest claims: {}", DEFAULT_CLAIMS);
    return DEFAULT_CLAIMS;
  }

  private boolean useDefaultProfile(Optional<NetworkProfileConfig> networkProfileConfig) {
    return !profilesJsonExists() || !existsInAbstractConfiguration(networkProfileConfig);
  }

  private boolean profilesJsonExists() {
    return !profiles.isEmpty();
  }

  private boolean existsInAbstractConfiguration(
      Optional<NetworkProfileConfig> networkProfileConfig) {
    return networkProfileConfig.isPresent();
  }

  private Map<String, Object> getPropertiesForProfile(String profile) {
    final Map<String, Object> properties = new HashMap<>();
    properties.put(PROFILE, profile);
    properties.put(ATTRIBUTES, getGuestClaims(profile));
    return properties;
  }

  private Map<String, Object> getPropertiesForDefaultProfile() {
    final Map<String, Object> properties = new HashMap<>();
    final String[] defaultGuestClaims = getDefaultGuestClaims();
    properties.put(ATTRIBUTES, defaultGuestClaims);
    return properties;
  }
}
