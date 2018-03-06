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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrHttpRequestRetryHandler implements HttpRequestRetryHandler {

  // 30 with 11 retries will make the total attempt last around 2 minutes
  private static final long TIME_FACTOR = 30L;

  private static final int MAX_RETRY_COUNT = 11;

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrHttpRequestRetryHandler.class);

  private final String coreName;

  public SolrHttpRequestRetryHandler(String coreName) {
    this.coreName = coreName;
  }

  @Override
  public boolean retryRequest(IOException e, int retryCount, HttpContext httpContext) {
    LOGGER.debug("Solr({}): Http client connection #{} failed", coreName, retryCount, e);
    if (e instanceof InterruptedIOException) {
      LOGGER.debug("Solr({}): Http client connection I/O interrupted.", coreName);
      Thread.currentThread().interrupt();
      return false;
    }
    if (e instanceof UnknownHostException) {
      LOGGER.warn("Solr({}): Http Client unknown host.", coreName);
    }
    if (e instanceof SSLException) {
      LOGGER.warn("Solr({}): Http client SSL handshake exception", coreName);
    }
    if (retryCount > SolrHttpRequestRetryHandler.MAX_RETRY_COUNT) {
      return false;
    }
    try {
      long waitTime = (long) Math.pow(2, retryCount) * TIME_FACTOR;
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Solr({}): Http client connection failed, waiting {} before retrying.",
            coreName,
            DurationFormatUtils.formatDurationHMS(waitTime));
      }
      synchronized (this) {
        wait(waitTime);
      }
    } catch (InterruptedException ie) {
      LOGGER.debug("Solr({}): Http client interrupted while waiting.", coreName);
      Thread.currentThread().interrupt();
      return false;
    }
    return true;
  }
}
