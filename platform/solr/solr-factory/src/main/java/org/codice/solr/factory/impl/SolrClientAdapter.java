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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.codice.ddf.platform.util.StandardThreadFactoryBuilder;
import org.codice.solr.factory.impl.StatefulCloseable.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides an implementation for the {@link org.codice.solr.client.solrj.SolrClient}
 * interface that adapts to {@link SolrClient}.
 */
public class SolrClientAdapter extends SolrClientProxy
    implements org.codice.solr.client.solrj.SolrClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(SolrClientAdapter.class);

  private static final int THREAD_POOL_DEFAULT_SIZE = 128;

  private static final RetryPolicy RETRY_UNTIL_NOT_INTERRUPTED_AND_NO_ERROR_AND_NOT_NULL =
      new RetryPolicy()
          .retryWhen(null)
          .retryOn(SolrServerException.class, SolrException.class, IOException.class)
          .abortOn(InterruptedIOException.class, InterruptedException.class)
          .withBackoff(10L, TimeUnit.MINUTES.toMillis(1L), TimeUnit.MILLISECONDS);

  private static final long ERROR_MIN_FREQUENCY = TimeUnit.MINUTES.toMillis(1L);

  private static final UnavailableSolrClient CLOSED_CLIENT =
      new UnavailableSolrClient("Solr client was closed");

  private static final ScheduledExecutorService EXECUTOR = SolrClientAdapter.createExecutor();

  private final Object lock = new Object();

  private final String core;

  private final Creator creator;

  private final List<Listener> listeners = new CopyOnWriteArrayList<>();

  private final Queue<Initializer> initializers = new ConcurrentLinkedQueue<>();

  /** The state is maintaining the reference to the current client we proxy to. */
  private final StatefulCloseable<SolrClientProxy> state;

  /**
   * Constructs a new client adapter for the specified code and using the specified creator to
   * create new Solr client instances.
   *
   * <p><i>Note:</i> There is no need to implement any retry behavior in the creator as this will be
   * handled by this class. Simply attempt the creation and fail fast. The creation of the client
   * can be interrupted (see {@link Thread#interrupted}) in which case it should attempt to stop the
   * creation as quickly as possible. It is acceptable for the creator to throw back any of the
   * following exceptions: {@link IOException}, {@link SolrServerException}, {@link
   * org.apache.solr.common.SolrException}, {@link InterruptedException}, or {@link
   * InterruptedIOException}. A retry will automatically be triggered if returning <code>null</code>
   * or any exceptions other than {@link InterruptedException} or {@link InterruptedIOException} or
   * thrown back.
   *
   * @param core the Solr core for which to create an adaptor
   * @param creator the creator to use for creating corresponding Solr clients
   * @throws IllegalArgumentException if <code>core</code>, <code>info</code>, and <code>creator
   *     </code> is <code>null</code>
   */
  public SolrClientAdapter(String core, Creator creator) {
    Validate.notNull(core, "invalid null core");
    Validate.notNull(creator, "invalid null creator");
    LOGGER.debug("Solr({}): Creating a Solr client adapter", core);
    this.core = core;
    this.creator = creator;
    this.state =
        new StatefulCloseable<>(
                core,
                "Adapter",
                "creation",
                SolrClientAdapter.EXECUTOR,
                SolrClientAdapter.RETRY_UNTIL_NOT_INTERRUPTED_AND_NO_ERROR_AND_NOT_NULL,
                this::create,
                this::notifyAvailability)
            .withInitLogLevel(Level.INFO)
            .withFailedLogLevel(Level.WARN);
    state.setUnavailable(new UnavailableSolrClient("initializing '" + core + "' core"));
  }

  private static ScheduledExecutorService createExecutor() throws NumberFormatException {
    return Executors.newScheduledThreadPool(
        NumberUtils.toInt(
            System.getProperty("org.codice.ddf.system.threadPoolSize"),
            SolrClientAdapter.THREAD_POOL_DEFAULT_SIZE),
        StandardThreadFactoryBuilder.newThreadFactory("SolrClientAdapter"));
  }

  @Override
  protected SolrClient getProxiedClient() {
    return state.getData();
  }

  @Override
  public final SolrClient getClient() {
    // returning this to make sure all calls from our SolrClient API will still be intercepted
    // when the client is retrieved and passed to a Solr request object
    // this should be temporary until we completely abstract out Solr with interfaces
    return this;
  }

  @Override
  public String getCore() {
    return core;
  }

  @Override
  public void close() throws IOException {
    try {
      state.close(SolrClientAdapter.CLOSED_CLIENT);
    } finally {
      listeners.clear();
      initializers.clear();
    }
  }

  @Override
  public boolean isAvailable() {
    return state.isAvailable();
  }

  @Override
  public boolean isAvailable(long timeout, TimeUnit unit) throws InterruptedException {
    return state.isAvailable(timeout, unit);
  }

  @Override
  public boolean isAvailable(Listener listener) {
    Validate.notNull(listener, "invalid null listener");
    if (state.isClosed()) {
      return false;
    }
    listeners.add(listener);
    final boolean available = isAvailable();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Solr({}): Adapter is starting a background task to notify a new listener that its availability is [{}]",
          core,
          SolrClientAdapter.availableToString(available));
    }
    SolrClientAdapter.EXECUTOR.submit(() -> notifyAvailability(listener, "is"));
    return available;
  }

  @Override
  public void whenAvailable(Initializer initializer) {
    Validate.notNull(initializer, "invalid null initializer");
    if (state.isClosed()) {
      return;
    }
    LOGGER.debug("Solr({}): Adapter is registering a new initializer [{}]", core, initializer);
    // add the initializer to the list first to make sure we don't miss
    // the available state changes. We shall notify the initializer ourselves if
    // we are available and it was not yet removed from the list by another thread
    initializers.add(initializer);
    if (isAvailable() && initializers.remove(initializer)) {
      SolrClientAdapter.EXECUTOR.submit(() -> notifyAvailability(initializer));
    }
  }

  @Override
  public String toString() {
    return "SolrClientAdapter(" + core + ", " + getProxiedClient() + ")";
  }

  private SolrClientProxy create() throws SolrServerException, IOException, InterruptedException {
    return new SolrClientStatefulProxy(
        SolrClientAdapter.EXECUTOR, this::notifyAvailability, creator.create(), core);
  }

  private void notifyAvailability(SolrClientProxy c) {
    // must be from the current client to notify our listeners and initializers
    if (c == getProxiedClient()) {
      notifyAvailability();
    }
  }

  private void notifyAvailability() {
    final boolean available = isAvailable();
    final String availableString = SolrClientAdapter.availableToString(available);

    if (state.isClosed()) {
      LOGGER.info("Solr client ({}) is closed", core);
    } else {
      LOGGER.info("Solr client ({}) is {}", core, availableString);
    }
    if (!listeners.isEmpty()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Solr({}): Adapter is starting background task(s) to notify {} listener(s) that its availability changed to [{}]",
            core,
            listeners.size(),
            availableString);
      }
      listeners.forEach(
          l -> SolrClientAdapter.EXECUTOR.submit(() -> notifyAvailability(l, "changed to")));
    }
    if (available && !initializers.isEmpty()) {
      Initializer i;

      LOGGER.debug(
          "Solr({}): Adapter is starting background task(s) to notify {} initializer(s)",
          core,
          initializers.size());
      while ((i = initializers.poll()) != null) {
        final Initializer initializer = i;

        SolrClientAdapter.EXECUTOR.submit(() -> notifyAvailability(initializer));
      }
    }
  }

  private void notifyAvailability(Listener listener, String how) {
    final boolean available = isAvailable();

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Solr({}): Adapter is notifying a listener [{}] that its availability {} [{}]",
          core,
          listener,
          how,
          SolrClientAdapter.availableToString(available));
    }
    listener.changed(this, available);
  }

  private void notifyAvailability(Initializer initializer) {
    LOGGER.debug("Solr({}): Adapter is notifying an initializer [{}]", core, initializer);
    initializer.initialized(this);
  }

  private static String availableToString(boolean available) {
    return available ? "available" : "not available";
  }

  /** Functional interface used to create Solr clients. */
  @FunctionalInterface
  protected interface Creator {
    /**
     * Called to attempt to create a new Solr client.
     *
     * @return the corresponding client or <code>null</code> if unable to create one
     * @throws IOException if an I/O exception occurred while attempting to create the Solr client
     * @throws SolrServerException if an Solr server exception occurred while attempting to create
     *     the Solr client
     * @throws SolrException if an Solr exception occurred while attempting to create the Solr
     *     client
     * @throws InterruptedException if interrupted while attempting to create the Solr client
     */
    public SolrClient create() throws SolrServerException, IOException, InterruptedException;
  }
}
