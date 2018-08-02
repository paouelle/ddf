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
package ddf.ldap.ldaplogin.config.mapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.model.LdapConfig;
import org.junit.Before;
import org.junit.Test;

public class LdapLoginConfigMappingProviderTest {

  private LdapLoginConfigMappingProvider ldapLoginConfigMappingProvider;
  private static final String LDAP_LOGIN_FACTORY_PID = "Ldap_Login_Config";

  // configuration attribute names
  private static final String LDAP_URL = "ldapUrl";
  private static final String START_TLS_ATTRIBUTE = "startTls";
  private static final String BIND_USER_DN = "ldapBindUserDn";
  private static final String BIND_USER_PASS = "ldapBindUserPass";
  private static final String BIND_METHOD = "bindMethod";
  private static final String LOGIN_USER_ATTRIBUTE = "loginUserAttribute";
  private static final String BASE_USER_DN = "userBaseDn";
  private static final String BASE_GROUP_DN = "groupBaseDn";
  private static final String GROUP_MEMBERSHIP_USER_ATTRIBUTE = "membershipUserAttribute";

  @Before
  public void setup() {
    ldapLoginConfigMappingProvider = new LdapLoginConfigMappingProvider();
  }

  @Test
  public void testCanProvide() {
    ConfigMapping mockConfigMapping = mock(ConfigMapping.class);
    ConfigMapping.Id mockConfigMappingId = mock(ConfigMapping.Id.class);
    doReturn(mockConfigMappingId).when(mockConfigMapping).getId();
    doReturn(LDAP_LOGIN_FACTORY_PID).when(mockConfigMappingId).getName();

    assertTrue(ldapLoginConfigMappingProvider.canProvideFor(mockConfigMapping));
    assertTrue(ldapLoginConfigMappingProvider.canProvideFor(mockConfigMappingId));
  }

  @Test
  public void testProvide() {
    String instanceId = "instance123";

    ConfigMapping.Id mockConfigMappingId = mock(ConfigMapping.Id.class);
    doReturn(LDAP_LOGIN_FACTORY_PID).when(mockConfigMappingId).getName();
    doReturn(Optional.of(instanceId)).when(mockConfigMappingId).getInstance();

    ConfigService mockConfigService = mock(ConfigService.class);
    doReturn(Optional.of(getMockLdapConfig()))
        .when(mockConfigService)
        .get(LdapConfig.class, instanceId);

    Map<String, Object> result =
        ldapLoginConfigMappingProvider.provide(mockConfigMappingId, mockConfigService);

    assertEquals("ldaps://example.com:8181", result.get(LDAP_URL));
    assertEquals(false, result.get(START_TLS_ATTRIBUTE));
    assertEquals("cn\\=admin", result.get(BIND_USER_DN));
    assertEquals("dolphins", result.get(BIND_USER_PASS));
    assertEquals("SIMPLE", result.get(BIND_METHOD));
    assertEquals("uid", result.get(LOGIN_USER_ATTRIBUTE));
    assertEquals("ou=users,dc=example,dc=com", result.get(BASE_USER_DN));
    assertEquals("ou=groups,dc=example,dc=com", result.get(BASE_GROUP_DN));
    assertEquals("uid", result.get(GROUP_MEMBERSHIP_USER_ATTRIBUTE));
  }

  private LdapConfig getMockLdapConfig() {
    LdapConfig ldapConfig = mock(LdapConfig.class);

    doReturn("ldap123").when(ldapConfig).getId();
    doReturn("example.com").when(ldapConfig).getHostname();
    doReturn(8181).when(ldapConfig).getPort();
    doReturn("LDAPS").when(ldapConfig).getEncryption();
    doReturn("cn\\=admin").when(ldapConfig).getBindUserDn();
    doReturn("dolphins").when(ldapConfig).getBindUserPass();
    doReturn("SIMPLE").when(ldapConfig).getBindMethod();
    doReturn("uid").when(ldapConfig).getLoginUserAttribute();
    doReturn("ou=users,dc=example,dc=com").when(ldapConfig).getBaseUserDn();
    doReturn("ou=groups,dc=example,dc=com").when(ldapConfig).getBaseGroupDn();
    doReturn("uid").when(ldapConfig).getGroupMembershipUserAttribute();

    return ldapConfig;
  }
}
