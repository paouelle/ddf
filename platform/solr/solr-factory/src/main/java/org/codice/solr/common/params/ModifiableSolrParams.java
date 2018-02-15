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
package org.codice.solr.common.params;

import java.util.Map;
import java.util.Set;

public interface ModifiableSolrParams extends SolrParams {
  public int size();

  public Map<String, String[]> getMap();

  /**
   * Replace any existing parameter with the given name. if val==null remove key from params
   * completely.
   */
  public ModifiableSolrParams set(String name, String... val);

  public ModifiableSolrParams set(String name, int val);

  public ModifiableSolrParams set(String name, boolean val);

  /**
   * Add the given values to any existing name
   *
   * @param name Key
   * @param val Array of value(s) added to the name. NOTE: If val is null or a member of val is
   *     null, then a corresponding null reference will be included when a get method is called on
   *     the key later.
   * @return this
   */
  public ModifiableSolrParams add(String name, String... val);

  public void add(SolrParams params);

  /** remove a field at the given name */
  public String[] remove(String name);

  /** clear all parameters */
  public void clear();

  /**
   * remove the given value for the given name
   *
   * @return true if the item was removed, false if null or not present
   */
  public boolean remove(String name, String value);

  public Set<String> getParameterNames();
}
