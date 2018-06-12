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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.config.mapping.ConfigMappingProvider;

/** Reader for Groovy-based rule definitions for mapped configuration properties. */
// FYI, Boon has a bug when dealing with streams where it stops reading the stream at whatever is
// returned from the first read with a buffer of 8K. If the stream returns less or 8K, the stream
// is closed. So if you are dealing with large Json files, that chops the file.
// See https://github.com/boonproject/boon/issues/320 describes the problem
// if you follow the suggested instructions then it stops at 1K and never goes back.
public class GroovyConfigMappingReader {
  private static final ObjectMapper MAPPER =
      JsonFactory.create(
          new JsonParserFactory().useAnnotations(), new JsonSerializerFactory().useAnnotations());

  /**
   * Parses the Json document provided by the given url.
   *
   * @param url the <code>URL</code> providing access to the Json document
   * @return a corresponding config mapping provider
   * @throws IOException if an I/O error occurred while parsing the document
   */
  public ConfigMappingProvider parse(URL url) throws IOException {
    InputStream is = null;

    try {
      is = url.openStream();
      return parse(is);
    } finally {
      IOUtils.closeQuietly(is); // we do not care if we fail to close the stream
    }
  }

  /**
   * Parses the Json document provided by the given file.
   *
   * @param file the <code>File</code> providing access to the Json document
   * @return a corresponding config mapping provider
   * @throws IOException if an I/O error occurred while parsing the document
   */
  @SuppressWarnings("squid:S2093" /* we do not want to fail if the input stream cannot be closed */)
  public ConfigMappingProvider parse(File file) throws IOException {
    InputStream is = null;

    try {
      is = new FileInputStream(file);
      return parse(is);
    } finally {
      IOUtils.closeQuietly(is); // we do not care if we fail to close the stream
    }
  }

  /**
   * Parses the Json document provided by the given input stream.
   *
   * <p>The stream is not closed by this method.
   *
   * @param is the <code>InputStream</code> providing access to the Json document
   * @return a corresponding config mapping provider
   * @throws IOException if an I/O error occurred while parsing the document
   */
  public ConfigMappingProvider parse(InputStream is) throws IOException {
    return parse(IOUtils.toString(is, StandardCharsets.UTF_8));
  }

  /**
   * Parses the Json document provided by the given string.
   *
   * @param json the Json document
   * @return a corresponding config mapping provider
   * @throws IOException if an I/O error occurred while parsing the document
   */
  public ConfigMappingProvider parse(String json) throws IOException {
    return GroovyConfigMappingReader.MAPPER.fromJson(json, GroovyConfigMappingProvider.class);
  }
}
