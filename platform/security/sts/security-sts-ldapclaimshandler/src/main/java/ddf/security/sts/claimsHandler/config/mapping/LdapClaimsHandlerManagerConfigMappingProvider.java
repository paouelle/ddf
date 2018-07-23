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
package ddf.security.sts.claimsHandler.config.mapping;

import java.util.HashMap;
import java.util.Map;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMapping.Id;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.model.LdapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapClaimsHandlerManagerConfigMappingProvider implements ConfigMappingProvider {

  private static final String LDAP_CLAIMS_HANDLER_MANAGER_FACTORY_PID = "Claims_Handler_Manager";
  private static final String LDAPS = "LDAPS";
  private static final String START_TLS_ENCRYPTION_CONFIG = "STARTTLS";

  // configuration attribute names
  private static final String LDAP_URL = "url";
  private static final String START_TLS_ATTRIBUTE = "startTls";
  private static final String BIND_USER_DN = "ldapBindUserDn";
  private static final String BIND_USER_PASS = "password";
  private static final String BIND_METHOD = "bindMethod";
  private static final String LOGIN_USER_ATTRIBUTE = "loginUserAttribute";
  private static final String BASE_USER_DN = "userBaseDn";
  private static final String BASE_GROUP_DN = "groupBaseDn";
  private static final String GROUP_OBJECT_CLASS = "objectClass";
  private static final String GROUP_MEMBERSHIP_USER_ATTRIBUTE = "membershipUserAttribute";

  // default configuration attribute names
  private static final String OVERRIDE_CERT_DN = "overrideCertDn";
  private static final String PROPERTY_FILE_LOCATION = "propertyFileLocation";

  // default configuration values
  private static final boolean OVERRIDE_CERT_DN_DEFAULT = false;
  private static final String PROPERTY_FILE_LOCATION_DEFAULT = "etc/ws-security/attributeMap.properties";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LdapClaimsHandlerManagerConfigMappingProvider.class);

  @Override
  public boolean isPartial() {
    return false;
  }

  @Override
  public boolean canProvideFor(Id id) {
    final boolean canProvideFor = id.getName().equals(LDAP_CLAIMS_HANDLER_MANAGER_FACTORY_PID);
    LOGGER.debug(
        "Can Config Mapping Provider [{}] provide a configuration for [{}]? {}",
        LdapClaimsHandlerManagerConfigMappingProvider.class.getName(),
        id.getName(),
        canProvideFor);
    return canProvideFor;
  }

  @Override
  public Map<String, Object> provide(Id id, ConfigService configService) {
    String instanceId = getInstanceId(id);
    LdapConfig ldapConfig = getLdapConfig(configService, instanceId);

    final Map<String, Object> properties = new HashMap<>();

    properties.put(LDAP_URL, constructLdapUrl(ldapConfig));
    properties.put(START_TLS_ATTRIBUTE, startTls(ldapConfig));
    properties.put(BIND_USER_DN, ldapConfig.getBindUserDn());
    properties.put(BIND_USER_PASS, ldapConfig.getBindUserPass());
    properties.put(BIND_METHOD, ldapConfig.getBindMethod());
    properties.put(LOGIN_USER_ATTRIBUTE, ldapConfig.getLoginUserAttribute());
    properties.put(BASE_USER_DN, ldapConfig.getBaseUserDn());
    properties.put(BASE_GROUP_DN, ldapConfig.getBaseGroupDn());
    properties.put(GROUP_OBJECT_CLASS, ldapConfig.getGroupObjectClass());
    properties.put(GROUP_MEMBERSHIP_USER_ATTRIBUTE, ldapConfig.getGroupMembershipUserAttribute());

    //defaults
    properties.put(OVERRIDE_CERT_DN, OVERRIDE_CERT_DN_DEFAULT);
    properties.put(PROPERTY_FILE_LOCATION, PROPERTY_FILE_LOCATION_DEFAULT);

    LOGGER.debug(
        "Returning properties map [{}] for LDAP Claims Handler Manager [{}].",
        properties,
        instanceId);

    return properties;
  }

  private String constructLdapUrl(LdapConfig config) {
    StringBuilder ldapUrlBuilder = new StringBuilder();
    if (config.getEncryption().equals(LDAPS)) {
      ldapUrlBuilder.append("ldaps://");
    }
    ldapUrlBuilder.append(config.getHostname());
    ldapUrlBuilder.append(":");
    ldapUrlBuilder.append(config.getPort());
    return ldapUrlBuilder.toString();
  }

  private boolean startTls(LdapConfig config) {
    return config.getEncryption().equals(START_TLS_ENCRYPTION_CONFIG);
  }

  private String getInstanceId(ConfigMapping.Id id) {
    return id.getInstance()
        .orElseThrow(
            () ->
                new ConfigMappingException(
                    String.format("No instance id for %s found in Config Mapping.", id)));
  }

  private LdapConfig getLdapConfig(ConfigService configService, String instanceId) {
    return configService
        .get(LdapConfig.class, instanceId)
        .orElseThrow(
            () ->
                new ConfigMappingException(
                    "Unable to find config for ["
                        + LdapConfig.class
                        + "] with id ["
                        + instanceId
                        + "]."));
  }
}
