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
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.Validate;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.codice.solr.client.solrj.UnavailableSolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrClientStatefulProxy extends SolrClientProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger(SolrClientStatefulProxy.class);

  private static final String FAILED_TO_PING = "Solr({}): Proxy failed to ping";

  private static final String FAILED_TO_PING_WITH_STATUS =
      "Solr({}): Proxy failed to ping Solr client; got status [{}]";

  private static final String SOLR_CLIENT_WAS_CLOSED = "Solr client was closed";

  private static final String PING_STATUS = "'%s' ping status";

  private static final String OK_STATUS = "OK";

  private static final RetryPolicy RETRY_UNTIL_NOT_INTERRUPTED_AND_REACHABLE =
      new RetryPolicy()
          .<OptionalCause<?>>retryIf(OptionalCause::isAvailable)
          .abortOn(InterruptedIOException.class, InterruptedException.class)
          .withBackoff(1L, TimeUnit.MINUTES.toSeconds(2L), TimeUnit.SECONDS);

  private static final long PING_MIN_FREQUENCY = TimeUnit.SECONDS.toMillis(10L);

  private final transient ScheduledExecutorService executor;

  private final transient Consumer<SolrClientProxy> listener;

  private final SolrClient client;

  private final String core;

  /** The state is maintaining the last cause for being temporarily unavailable. */
  private final StatefulCloseable<OptionalCause<? extends Throwable>> state;

  private final AtomicLong lastPing = new AtomicLong();

  SolrClientStatefulProxy(
      ScheduledExecutorService executor,
      Consumer<SolrClientProxy> listener,
      SolrClient client,
      String core) {
    LOGGER.debug("Solr({}): Creating a Solr client proxy with listener [{}]", core, listener);
    this.executor = executor;
    this.listener = listener;
    this.client = client;
    this.core = core;
    this.state =
        new StatefulCloseable<>(
            core,
            "Proxy",
            "connection",
            executor,
            SolrClientStatefulProxy.RETRY_UNTIL_NOT_INTERRUPTED_AND_REACHABLE,
            this::checkIfReachable,
            this::notifyAvailability);
    state.setUnavailable(
        OptionalCause.of(new UnavailableSolrException("initializing '" + core + "' core")));
  }

  @Override
  protected SolrClient getProxiedClient() {
    if (state.isClosed()) {
      if (state.isAvailable()) {
        return client;
      } // else - currently not available so do a spot check to see if it suddenly became reachable
      checkIfReachable("from the API because the proxy is currently unavailable")
          .throwIfNotAvailable();
      if (!state.setAvailable(OptionalCause.empty())) {
        return client;
      }
    }
    throw new UnavailableSolrException(SolrClientStatefulProxy.SOLR_CLIENT_WAS_CLOSED);
  }

  @Override
  protected <T> T handle(Code<T> code) throws SolrServerException, IOException {
    try {
      return code.invoke(client);
    } catch (UnavailableSolrException e) {
      throw e;
    } catch (SolrException e) {
      LOGGER.debug(
          "Solr({}): Proxy API failure with code [{}] and metadata [{}]",
          core,
          e.code(),
          e.getMetadata(),
          e);
      checkIfReachable(e);
      throw e;
    } catch (SolrServerException | IOException e) {
      LOGGER.debug("Solr({}): Proxy API failure", core, e);
      checkIfReachable(e);
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    if (!state.close(OptionalCause.empty())) {
      LOGGER.debug("Solr({}): Proxy is closing the client", core);
      client.close();
    }
  }

  @Override
  public boolean isAvailable() {
    if (state.isAvailable() && lastPingWasNotRecent()) {
      LOGGER.debug(
          "Solr({}): Proxy is starting a background task to ping the client because the last ping was too long ago",
          core);
      executor.submit(this::backgroundPing);
      return true;
    }
    return false;
  }

  @Override
  // overridden to always send the ping to the client; avoiding the intercept from the base class
  // which goes throw getProxiedClient() which would throw back an unavailable error instead of
  // returning the response
  public SolrPingResponse ping() throws SolrServerException, IOException {
    return ping("from the API");
  }

  @Override
  public String toString() {
    return "SolrClientStatefulProxy(" + client + ")";
  }

  private SolrPingResponse backgroundPing() throws SolrServerException, IOException {
    return ping("in the background");
  }

  @SuppressWarnings("squid:S1181" /* bubbling out VirtualMachineError */)
  private SolrPingResponse ping(String how) throws SolrServerException, IOException {
    if (state.isClosed()) { // quick check to avoid pinging
      throw new UnavailableSolrException(SolrClientStatefulProxy.SOLR_CLIENT_WAS_CLOSED);
    }
    LOGGER.debug("Solr({}): Proxy is pinging the client {}", core, how);
    try {
      lastPing.set(System.currentTimeMillis());
      final SolrPingResponse ping = client.ping();
      final Object status = ping.getResponse().get("status");

      if (SolrClientStatefulProxy.OK_STATUS.equals(status)) {
        state.setAvailable(OptionalCause.empty());
      } else {
        LOGGER.debug(SolrClientStatefulProxy.FAILED_TO_PING_WITH_STATUS, core, status);
        state.setUnavailableIfAvailable(
            OptionalCause.of(new UnavailableSolrException(String.format(PING_STATUS, status))));
      }
      return ping;
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      LOGGER.debug(SolrClientStatefulProxy.FAILED_TO_PING, core, t);
      state.setUnavailableIfAvailable(OptionalCause.of(t));
      throw t;
    }
  }

  @SuppressWarnings("squid:S1181" /* bubbling out VirtualMachineError */)
  private OptionalCause<UnavailableSolrException> checkIfReachable(String how) {
    LOGGER.debug("Solr({}): Proxy is pinging the client {}", core, how);
    try {
      lastPing.set(System.currentTimeMillis());
      final Object status = client.ping().getResponse().get("status");

      if (SolrClientStatefulProxy.OK_STATUS.equals(status)) {
        return OptionalCause.empty();
      }
      LOGGER.debug(SolrClientStatefulProxy.FAILED_TO_PING_WITH_STATUS, core, status);
      return OptionalCause.of(
          new UnavailableSolrException(String.format("ping failed with status: %s", status)));
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      LOGGER.debug(SolrClientStatefulProxy.FAILED_TO_PING, core, t);
      return OptionalCause.of(new UnavailableSolrException("ping failed", t));
    }
  }

  private void checkIfReachable(Throwable error) {
    if (state.isClosed()) { // quick check to avoid pinging
      return;
    }
    final OptionalCause<UnavailableSolrException> cause =
        checkIfReachable("from the API after an error was detected");

    if (cause.isAvailable()) {
      state.setAvailable(cause);
    } else {
      state.setUnavailableIfAvailable(OptionalCause.of(error));
    }
  }

  @Nullable
  private OptionalCause checkIfReachable() {
    return checkIfReachable("in the background while trying to reconnect");
  }

  private void notifyAvailability() {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Solr({}): Proxy is notifying its listener [{}] that its availability changed to [{}]",
          core,
          listener,
          SolrClientStatefulProxy.availableToString(state.isAvailable()));
    }
    listener.accept(this);
  }

  private boolean lastPingWasNotRecent() {
    final long now = System.currentTimeMillis();

    return (lastPing.accumulateAndGet(now, SolrClientStatefulProxy::updateIfNotRecent) == now);
  }

  private static long updateIfNotRecent(long previous, long now) {
    return ((now - previous) >= SolrClientStatefulProxy.PING_MIN_FREQUENCY) ? now : previous;
  }

  private static String availableToString(boolean available) {
    return available ? "available" : "not available";
  }

  /**
   * This class is used to track the cause for being unavailable. The cause will be empty if we are
   * available.
   *
   * @param <T> the type of error for this optional cause
   */
  public static class OptionalCause<T extends Throwable> implements StatefulCloseable.Data {
    private static final OptionalCause EMPTY = new OptionalCause();

    /**
     * Returns an empty {@code OptionalCause} instance. No error is present for this optional cause.
     *
     * @param <T> the type of non-existing error for the empty cause
     * @return an empty {@code OptionalCause}
     */
    public static <T extends Throwable> OptionalCause<T> empty() {
      return OptionalCause.EMPTY;
    }

    /**
     * Returns an {@code OptionalCause} with the specified error.
     *
     * @param <T> the type of error for the cause
     * @param error the error associated with the cause
     * @return an {@code OptionalCause} with the specified error
     * @throws IllegalArgumentException if <code>error</code> is <code>null</code>
     */
    private static <T extends Throwable> OptionalCause<T> of(T error) {
      return new OptionalCause(error);
    }

    @Nullable private final T error;

    private OptionalCause() {
      this.error = null;
    }

    private OptionalCause(T error) {
      Validate.notNull(error, "invalid null cause");
      this.error = error;
    }

    /**
     * Gets the optional error associated with this optional cause.
     *
     * @return an optional error assocaited with this optional cause
     */
    @Nullable
    public Optional<T> getError() {
      return Optional.ofNullable(error);
    }

    /**
     * Throws the error associated with this optional cause if any.
     *
     * <p>This method will do nothing if this optional cause doesn't have an error associated with
     * it.
     *
     * @throws T if an error is associated with this optional cause
     */
    public void throwIfNotAvailable() throws T {
      if (error != null) {
        throw error;
      }
    }

    /**
     * Checks if this optional cause has no errors associated with it.
     *
     * @return <code>true</code> if no errors are associated with this optional cause; <code>false
     *     </code> otherwise
     */
    @Override
    public boolean isAvailable() {
      return (error == null);
    }

    @Override
    public void close() {
      // nothing to do
    }
  }
}
