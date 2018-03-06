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

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.codice.ddf.configuration.SystemBaseUrl;
import org.codice.solr.factory.SolrClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class used to create new {@link HttpSolrClient} instances. <br>
 * Uses the following system properties when creating an instance:
 *
 * <ul>
 *   <li>solr.data.dir: Absolute path to the directory where the Solr data will be stored
 *   <li>solr.http.url: Solr server URL
 *   <li>org.codice.ddf.system.threadPoolSize: Solr query thread pool size
 *   <li>https.protocols: Secure protocols supported by the Solr server
 *   <li>https.cipherSuites: Cipher suites supported by the Solr server
 * </ul>
 */
public class HttpSolrClientFactory implements SolrClientFactory {

  private static final String HTTPS_PROTOCOLS = "https.protocols";
  private static final String HTTPS_CIPHER_SUITES = "https.cipherSuites";
  private static final String SOLR_CONTEXT = "/solr";
  private static final String SOLR_DATA_DIR = "solr.data.dir";
  private static final String KEY_STORE_PASS = "javax.net.ssl.keyStorePassword";
  private static final String TRUST_STORE = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASS = "javax.net.ssl.trustStorePassword";
  private static final String KEY_STORE = "javax.net.ssl.keyStore";
  public static final List<String> DEFAULT_PROTOCOLS =
      Collections.unmodifiableList(
          Arrays.asList(StringUtils.split(System.getProperty(HTTPS_PROTOCOLS, ""), ",")));

  public static final List<String> DEFAULT_CIPHER_SUITES =
      Collections.unmodifiableList(
          Arrays.asList(StringUtils.split(System.getProperty(HTTPS_CIPHER_SUITES, ""), ",")));

  public static final String DEFAULT_SCHEMA_XML = "schema.xml";

  public static final String DEFAULT_SOLRCONFIG_XML = "solrconfig.xml";

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpSolrClientFactory.class);

  @Override
  public org.codice.solr.client.solrj.SolrClient newClient(String core) {
    String solrUrl =
        StringUtils.defaultIfBlank(
            System.getProperty("solr.http.url", getDefaultHttpsAddress()),
            SystemBaseUrl.constructUrl("/solr"));
    final String coreUrl = solrUrl + "/" + core;

    if (System.getProperty(SOLR_DATA_DIR) != null) {
      ConfigurationStore.getInstance().setDataDirectoryPath(System.getProperty(SOLR_DATA_DIR));
    }
    LOGGER.debug("Solr({}): Creating an HTTP Solr client using url [{}]", core, coreUrl);
    return new SolrClientAdapter(
        core, () -> HttpSolrClientFactory.createSolrHttpClient(solrUrl, core, null, coreUrl));
  }

  /**
   * Gets the default Solr server secure HTTP address.
   *
   * @return Solr server secure HTTP address
   */
  public static String getDefaultHttpsAddress() {
    return SystemBaseUrl.constructUrl("https", SOLR_CONTEXT);
  }

  private static SolrClient createSolrHttpClient(
      String url, String coreName, String configFile, String coreUrl)
      throws IOException, SolrServerException {
    final HttpClientBuilder builder =
        HttpClients.custom()
            .setDefaultCookieStore(new BasicCookieStore())
            .setMaxConnTotal(128)
            .setMaxConnPerRoute(32);

    if (StringUtils.startsWith(url, "https")) {
      builder.setSSLSocketFactory(
          new SSLConnectionSocketFactory(
              getSslContext(),
              getProtocols(),
              getCipherSuites(),
              SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER));
    }
    createSolrCore(url, coreName, configFile, builder.build());
    try (final Closer closer = new Closer()) {
      final HttpSolrClient noRetryClient =
          closer.with(new HttpSolrClient(coreUrl, builder.build()));
      final HttpSolrClient retryClient =
          closer.with(
              new HttpSolrClient(
                  coreUrl,
                  builder.setRetryHandler(new SolrHttpRequestRetryHandler(coreName)).build()));

      return closer.returning(new PingAwareSolrClientProxy(retryClient, noRetryClient));
    }
  }

  private static String[] getProtocols() {
    if (System.getProperty(HTTPS_PROTOCOLS) != null) {
      return StringUtils.split(System.getProperty(HTTPS_PROTOCOLS), ",");
    } else {
      return DEFAULT_PROTOCOLS.toArray(new String[DEFAULT_PROTOCOLS.size()]);
    }
  }

  private static String[] getCipherSuites() {
    if (System.getProperty(HTTPS_CIPHER_SUITES) != null) {
      return StringUtils.split(System.getProperty(HTTPS_CIPHER_SUITES), ",");
    } else {
      return DEFAULT_CIPHER_SUITES.toArray(new String[DEFAULT_CIPHER_SUITES.size()]);
    }
  }

  private static SSLContext getSslContext() {
    if (System.getProperty(KEY_STORE) == null
        || //
        System.getProperty(KEY_STORE_PASS) == null
        || //
        System.getProperty(TRUST_STORE) == null
        || //
        System.getProperty(TRUST_STORE_PASS) == null) {
      throw new IllegalArgumentException("KeyStore and TrustStore system properties must be set.");
    }

    KeyStore trustStore =
        getKeyStore(System.getProperty(TRUST_STORE), System.getProperty(TRUST_STORE_PASS));
    KeyStore keyStore =
        getKeyStore(System.getProperty(KEY_STORE), System.getProperty(KEY_STORE_PASS));

    SSLContext sslContext = null;

    try {
      sslContext =
          SSLContexts.custom()
              .loadKeyMaterial(keyStore, System.getProperty(KEY_STORE_PASS).toCharArray())
              .loadTrustMaterial(trustStore)
              .useTLS()
              .build();
    } catch (UnrecoverableKeyException
        | NoSuchAlgorithmException
        | KeyStoreException
        | KeyManagementException e) {
      throw new IllegalArgumentException(
          "Unable to use javax.net.ssl.keyStorePassword to load key material to create SSL context for Solr client.");
    }

    sslContext.getDefaultSSLParameters().setNeedClientAuth(true);
    sslContext.getDefaultSSLParameters().setWantClientAuth(true);

    return sslContext;
  }

  private static KeyStore getKeyStore(String location, String password) {
    LOGGER.debug("Loading keystore from {}", location);
    KeyStore keyStore = null;

    try (FileInputStream storeStream = new FileInputStream(location)) {
      keyStore = KeyStore.getInstance(System.getProperty("javax.net.ssl.keyStoreType"));
      keyStore.load(storeStream, password.toCharArray());
    } catch (CertificateException | IOException | NoSuchAlgorithmException | KeyStoreException e) {
      LOGGER.warn("Unable to load keystore at {}", location, e);
    }

    return keyStore;
  }

  private static void createSolrCore(
      String url, String coreName, String configFileName, HttpClient httpClient)
      throws IOException, SolrServerException {

    try (HttpSolrClient client =
        (httpClient != null ? new HttpSolrClient(url, httpClient) : new HttpSolrClient(url))) {

      HttpResponse ping = client.getHttpClient().execute(new HttpHead(url));
      if (ping != null && ping.getStatusLine().getStatusCode() == 200) {
        ConfigurationFileProxy configProxy =
            new ConfigurationFileProxy(ConfigurationStore.getInstance());
        configProxy.writeSolrConfiguration(coreName);
        if (!solrCoreExists(client, coreName)) {
          LOGGER.debug("Solr({}): Creating Solr core", coreName);

          String configFile = StringUtils.defaultIfBlank(configFileName, DEFAULT_SOLRCONFIG_XML);

          String solrDir;
          if (System.getProperty(SOLR_DATA_DIR) != null) {
            solrDir = System.getProperty(SOLR_DATA_DIR);
          } else {
            solrDir = Paths.get(System.getProperty("karaf.home"), "data", "solr").toString();
          }

          String instanceDir = Paths.get(solrDir, coreName).toString();

          String dataDir = Paths.get(instanceDir, "data").toString();

          CoreAdminRequest.createCore(
              coreName, instanceDir, client, configFile, DEFAULT_SCHEMA_XML, dataDir, dataDir);
        } else {
          LOGGER.debug("Solr({}): Solr core already exists; reloading it", coreName);
          CoreAdminRequest.reloadCore(coreName, client);
        }
      } else {
        LOGGER.debug("Solr({}): Unable to ping Solr core at {}", coreName, url);
        throw new SolrServerException("Unable to ping Solr core");
      }
    }
  }

  private static boolean solrCoreExists(SolrClient client, String coreName)
      throws IOException, SolrServerException {
    CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, client);
    return response.getCoreStatus(coreName).get("instanceDir") != null;
  }
}
