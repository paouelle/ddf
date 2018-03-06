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
package org.codice.solr.factory.impl;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.Validate;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.schema.IndexSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Maintain embedded Solr configuration and schema files.
 *
 * <p><i>Note:</i> The corresponding {@link SolrConfig} and {@link IndexSchema} will be constructed
 * the first time they are retrieved.
 */
public class EmbeddedSolrFiles {
  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedSolrFiles.class);

  private final String coreName;
  private final String configName;
  private final File configFile;
  private final String schemaName;
  private final File schemaFile;
  private volatile SolrConfig solrConfig = null;
  private volatile IndexSchema schemaIndex = null;

  /**
   * Constructor.
   *
   * @param coreName name of the Solr core
   * @param configXml name of the Solr configuration file
   * @param schemaXmls file names of the Solr core schemas to attempt to load (will start with the
   *     first and fallback to the others in order if unavailable)
   * @param configProxy {@link ConfigurationFileProxy} instance to use
   * @throws IllegalArgumentException if <code>coreName</code>, <code>configXml</code>, <code>
   *     schemaXmls</code>, or <code>configProxy</code> is <code>null</code> or if unable to find
   *     any files
   */
  public EmbeddedSolrFiles(
      String coreName, String configXml, String[] schemaXmls, ConfigurationFileProxy configProxy) {
    Validate.notNull(coreName, "invalid null Solr core name");
    Validate.notNull(configXml, "invalid null Solr configuration file");
    Validate.notNull(schemaXmls, "Invalid null Solr schema files");
    Validate.notEmpty(schemaXmls, "missing Solr schema files");
    Validate.noNullElements(schemaXmls, "invalid null Solr schema file");
    Validate.notNull(configProxy, "invalid null Solr config proxy");
    this.coreName = coreName;
    this.configName = configXml;
    this.configFile = EmbeddedSolrFactory.getConfigFile(configXml, configProxy, coreName);
    Validate.notNull(configFile, "Unable to find Solr configuration file");
    File solrSchemaFile = null;
    String schemaXml = null;

    for (final String s : schemaXmls) {
      schemaXml = s;
      solrSchemaFile = EmbeddedSolrFactory.getConfigFile(schemaXml, configProxy, coreName);
      if (solrSchemaFile != null) {
        break;
      }
    }
    Validate.notNull(solrSchemaFile, "Unable to find Solr schema file.");
    this.schemaFile = solrSchemaFile;
    this.schemaName = schemaXml;
  }

  /**
   * Gets the home directory where the configuration resides.
   *
   * @return the home directory where the configuration resides
   */
  public File getConfigHome() {
    return configFile.getParentFile();
  }

  /**
   * Creates or retrieves the schma index corresponding to this set of Solr files.
   *
   * @return retrieves a corresponding index for the schema
   * @throws IllegalArgumentException if unable to load or parse the corresponding schema or config
   */
  public IndexSchema getSchemaIndex() {
    if (schemaIndex == null) {
      final SolrConfig cfg = getConfig(); // make sure it is initialized

      LOGGER.debug(
          "Loading and creating index for {} schema using file [{} ({})]",
          coreName,
          schemaName,
          schemaFile);
      try {
        this.schemaIndex =
            new IndexSchema(
                cfg, schemaName, new InputSource(FileUtils.openInputStream(schemaFile)));
      } catch (IOException e) {
        LOGGER.debug(
            "failed to parse {} Solr schema file [{} ({})]", coreName, schemaName, schemaFile, e);
        throw new IllegalArgumentException("Unable to parse Solr schema file: " + schemaName, e);
      }
    }
    return schemaIndex;
  }

  /**
   * Creates or retrieves the Solr config for this set of Solr files.
   *
   * @return retrieves a corresponding Solr config
   * @throws IllegalArgumentException if unable to load or parse the corresponding config
   */
  public SolrConfig getConfig() {
    if (solrConfig == null) {
      LOGGER.debug(
          "Loading and creating Solr config for {} using file [{} ({})]",
          coreName,
          configName,
          configFile);
      try {
        this.solrConfig =
            new SolrConfig(
                getConfigHome().getParentFile().toPath(),
                configName,
                new InputSource(FileUtils.openInputStream(configFile)));
      } catch (ParserConfigurationException | IOException | SAXException e) {
        LOGGER.debug(
            "failed to parse {} Solr configuration file [{} ({})]",
            coreName,
            configName,
            configFile,
            e);
        throw new IllegalArgumentException(
            "Unable to parse Solr configuration file: " + configName, e);
      }
    }
    return solrConfig;
  }

  @Override
  public int hashCode() {
    return Objects.hash(coreName, configFile, schemaFile);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof EmbeddedSolrFiles) {
      final EmbeddedSolrFiles f = (EmbeddedSolrFiles) obj;

      return coreName.equals(f.coreName)
          && configFile.equals(f.configFile)
          && schemaFile.equals(f.schemaFile);
    }
    return false;
  }

  @Override
  public String toString() {
    return coreName + ',' + configName + ',' + schemaName;
  }
}
