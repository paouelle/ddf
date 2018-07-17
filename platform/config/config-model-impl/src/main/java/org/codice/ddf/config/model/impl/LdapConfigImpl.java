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

import org.codice.ddf.config.model.LdapConfig;

public class LdapConfigImpl implements LdapConfig {
  private String hostname;
  private int port;
  private String encryption;
  private String bindUserDn;
  private String bindUserPass;
  private String bindMethod;
  private String loginUserAttribute;
  private String baseUserDn;
  private String baseGroupDn;
  private String groupObjectClass;
  private String groupMembershipUserAttribute;
  private String id;
  private String version;

  @Override
  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  @Override
  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public String getEncryption() {
    return encryption;
  }

  public void setEncryption(String encryption) {
    this.encryption = encryption;
  }

  @Override
  public String getBindUserDn() {
    return bindUserDn;
  }

  public void setBindUserDn(String bindUserDn) {
    this.bindUserDn = bindUserDn;
  }

  @Override
  public String getBindUserPass() {
    return bindUserPass;
  }

  public void setBindUserPass(String bindUserPass) {
    this.bindUserPass = bindUserPass;
  }

  @Override
  public String getBindMethod() {
    return bindMethod;
  }

  public void setBindMethod(String bindMethod) {
    this.bindMethod = bindMethod;
  }

  @Override
  public String getLoginUserAttribute() {
    return loginUserAttribute;
  }

  public void setLoginUserAttribute(String loginUserAttribute) {
    this.loginUserAttribute = loginUserAttribute;
  }

  @Override
  public String getBaseUserDn() {
    return baseUserDn;
  }

  public void setBaseUserDn(String baseUserDn) {
    this.baseUserDn = baseUserDn;
  }

  @Override
  public String getBaseGroupDn() {
    return baseGroupDn;
  }

  public void setBaseGroupDn(String baseGroupDn) {
    this.baseGroupDn = baseGroupDn;
  }

  @Override
  public String getGroupObjectClass() {
    return groupObjectClass;
  }

  public void setGroupObjectClass(String groupObjectClass) {
    this.groupObjectClass = groupObjectClass;
  }

  @Override
  public String getGroupMembershipUserAttribute() {
    return groupMembershipUserAttribute;
  }

  public void setGroupMembershipUserAttribute(String groupMembershipUserAttribute) {
    this.groupMembershipUserAttribute = groupMembershipUserAttribute;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}
