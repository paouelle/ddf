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
package org.codice.ddf.admin.configuration;

import io.fabric8.karaf.core.properties.function.PropertiesFunction;

/**
 * Supports properties of the form <code>if:[condition]?[then]:[else]</code>. <code>[conditions]
 * </code> supports the following boolean operators and/or constants: <code>
 * !, ||, &&, (), true, false</code>.
 */
public class IfPropertiesFunction implements PropertiesFunction {
  private final BooleanEvaluator evaluator = new BooleanEvaluator();

  @Override
  public String getName() {
    return "if";
  }

  @Override
  public String apply(String s) {
    final int i = s.indexOf('?');
    final int j = s.indexOf(':', i);

    if ((i == -1) || (j == -1)) {
      return null;
    }
    final String condition = s.substring(0, i);

    if (Boolean.TRUE.equals(evaluator.evaluate(condition))) {
      return s.substring(i + 1, j);
    }
    return s.substring(j + 1);
  }
}
