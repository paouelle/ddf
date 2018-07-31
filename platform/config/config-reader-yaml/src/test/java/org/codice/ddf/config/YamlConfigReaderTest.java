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
package org.codice.ddf.config;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Set;
import org.codice.ddf.config.model.NetworkProfileConfig;
import org.codice.ddf.config.reader.impl.YamlConfigReaderImpl;
import org.junit.Test;

public class YamlConfigReaderTest {

  @Test
  public void testNetworkProfile() throws Exception {
    YamlConfigReaderImpl yc = new YamlConfigReaderImpl();
    File config = new File(getClass().getClassLoader().getResource("networkProfile.yml").getFile());
    Set<Config> configs = yc.read(config);
    System.out.println("configs: " + configs);
    assertEquals(1, configs.size());
    for (Config c : configs) {
      System.out.println("class: " + c.getClass());
      System.out.println(((NetworkProfileConfig) c).getProfile());
      System.out.println(((NetworkProfileConfig) c).getVersion());
      System.out.println("hash code: " + c.hashCode());
    }
  }
}
