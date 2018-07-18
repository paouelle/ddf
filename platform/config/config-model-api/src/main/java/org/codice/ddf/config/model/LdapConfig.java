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
package org.codice.ddf.config.model;

import org.codice.ddf.config.ConfigGroup;
import org.codice.ddf.config.ConfigType;

@ConfigType
public interface LdapConfig extends ConfigGroup {

  @Override
  public default Class<LdapConfig> getType() {
    return LdapConfig.class;
  }

  /**
   * Gets the hostname for the LDAP server.
   *
   * @return the hostname for the LDAP server.
   */
  public String getHostname();

  /**
   * Gets the port for the LDAP server.
   *
   * @return the port for the LDAP server.
   */
  public int getPort();

  /**
   * Gets the encryption method used by the LDAP server.
   *
   * @return the encryption method used by the LDAP server.
   */
  public String getEncryption();

  /**
   * Gets the username used to bind user with the LDAP server.
   *
   * @return the username used to bind user with the LDAP server.
   */
  public String getBindUserDn();

  /**
   * Gets the password used to bind user with the LDAP server.
   *
   * @return the password used to bind user with the LDAP server.
   */
  public String getBindUserPass();

  /**
   * Gets the bind method to bind user with the LDAP server.
   *
   * @return the bind method to bind user with the LDAP server.
   */
  public String getBindMethod();

  /**
   * Gets the login user attribute for the LDAP server.
   *
   * @return the login user attribute for the LDAP server.
   */
  public String getLoginUserAttribute();

  /**
   * Gets the full LDAP path where users can be found.
   *
   * @return the full LDAP path where users can be found.
   */
  public String getBaseUserDn();

  /**
   * Gets the full LDAP path where groups can be found.
   *
   * @return the full LDAP path where groups can be found.
   */
  public String getBaseGroupDn();

  /**
   * Gets the object class that defines structure for group membership in the LDAP server.
   *
   * @return the object class that defines structure for group membership in the LDAP server.
   */
  public String getGroupObjectClass();

  /**
   * Gets the attribute used as the membership attribute for users in the group.
   *
   * @return the attribute used as the membership attribute for users in the group.
   */
  public String getGroupMembershipUserAttribute();
}
