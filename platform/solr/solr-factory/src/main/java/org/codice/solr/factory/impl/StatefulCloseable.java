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

import com.google.common.io.Closeables;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.codice.solr.factory.impl.StatefulCloseable.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to track the state of an object while referencing a closeable piece of data.
 * It is design to initialize or re-initialize that piece of data in the background whenever its
 * availability is changed to not available. Any close request will be propagated to the referenced
 * closeable piece of data.
 *
 * <p>The availability state can be changed via one of {@link #setAvailable(Data)}, {@link
 * #setUnavailable(Data)}, or {@link #setUnavailableIfAvailable(Data)} methods. It can be closed
 * when no longer required via the {@link #close(Data)} method.
 *
 * @param <T> The type of closeable data referenced
 */
public class StatefulCloseable<T extends Data> {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatefulCloseable.class);

  private static final long ERROR_MIN_FREQUENCY = TimeUnit.MINUTES.toMillis(1L);

  private final Object lock = new Object();

  private final String core;

  private final String type;

  private final String initInfo;

  private final Runnable listener;

  private final ScheduledExecutorService executor;

  private final RetryPolicy retryPolicy;

  private final Callable<T> initializer;

  private volatile Level initLevel = Level.NONE;

  private volatile Level failedLevel = Level.NONE;

  // writes and reads are always protected by synchronization
  @Nullable private Future<T> future = null;

  // writes are always protected by synchronization, reads are not
  private State state = State.UNAVAILABLE;

  // writes are always protected by synchronization, reads are not
  private volatile T data;

  private final AtomicLong lastError = new AtomicLong();

  /**
   * Constructs a {@link StatefulCloseable} object.
   *
   * @param core the Solr core for which this state is going to be used
   * @param type type information for the owner of this object (used when logging only)
   * @param initInfo information identifying the initialization operation (used when logging only)
   * @param executor the executor to use for scheduling initialization attempts
   * @param retryPolicy the retry policy to use for when scheduling initialization attempts
   * @param initializer the initializer to call to perform the initialization
   * @param listener a listener to callback whenever the availability of this state has changed
   */
  public StatefulCloseable(
      String core,
      String type,
      String initInfo,
      ScheduledExecutorService executor,
      RetryPolicy retryPolicy,
      Callable<T> initializer,
      Runnable listener) {
    this.core = core;
    this.type = type;
    this.initInfo = initInfo;
    this.executor = executor;
    this.retryPolicy = retryPolicy;
    this.initializer = initializer;
    this.listener = listener;
  }

  /**
   * Sets a log level at which to log initialization successes in addition to being logged at debug
   * level. By default only the debug logs is generated.
   *
   * @param newInitLevel a log level at which to log a successful initialization
   * @return this for chaining
   */
  public StatefulCloseable<T> withInitLogLevel(Level newInitLevel) {
    this.initLevel = newInitLevel;
    return this;
  }

  /**
   * Sets a log level at which to log initialization failures in addition to being logged at debug
   * level. By default only the debug logs is generated.
   *
   * <p><i>Note:</i> For failures, logs generated while retrying to initialize will actually be
   * throttled to one every minute at the specified level to avoid overwhelming the administrator
   * with repeated logs.
   *
   * @param newFailedLevel a log level at which to log failures to initialize
   * @return this for chaining
   */
  public StatefulCloseable<T> withFailedLogLevel(Level newFailedLevel) {
    this.failedLevel = newFailedLevel;
    return this;
  }

  /**
   * Gets the currently referenced data from this state.
   *
   * @return the currently referenced data from this state
   */
  public T getData() {
    return data;
  }

  /**
   * Gets the current state.
   *
   * @return the current state
   */
  public State getState() {
    return state;
  }

  /**
   * Checks whether this state was closed.
   *
   * @return <code>true</code> if the state was closed; <code>false</code> otherwise
   */
  public boolean isClosed() {
    return state == State.CLOSED;
  }

  /**
   * Checks whether this state and the associated data are currently both reporting as available.
   *
   * @return <code>true</code> if the state and the associated data are currently both reported as
   *     available; <code>false</code> otherwise
   */
  public boolean isAvailable() {
    if (isClosed()) {
      return false;
    }
    final boolean available = ((state == State.AVAILABLE) && data.isAvailable());

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Solr({}): {}'s availability is [{}]",
          core,
          type,
          StatefulCloseable.availableToString(available));
    }
    return available;
  }

  /**
   * Waits if necessary for this state and the associated data to both become available.
   *
   * <p><i>Note:</i> Nothing will happen and the method will return right away if the state was
   * already closed
   *
   * @return <code>true</code> if this state and the associated data are currently both reported as
   *     or both become available before the specified timeout expires; <code>false</code> otherwise
   */
  public boolean isAvailable(long timeout, TimeUnit unit) throws InterruptedException {
    Validate.notNull(unit, "invalid null time unit");
    if (isAvailable()) { // quick check to avoid synchronization
      return true;
    }
    final long end =
        TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) + unit.toNanos(timeout);

    synchronized (lock) {
      if (state == State.CLOSED) {
        return false;
      }
      while (true) {
        final boolean available = isAvailable();

        if (available) {
          return true;
        }
        final long t = end - TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());

        if (t <= 0L) { // we timed out
          return available;
        }
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "Solr({}): {} is waiting {} to become available",
              core,
              type,
              DurationFormatUtils.formatDurationHMS(TimeUnit.NANOSECONDS.toMillis(t)));
        }
        TimeUnit.NANOSECONDS.timedWait(lock, t);
      }
    }
  }

  /**
   * Changes this state to closed while updating the referenced data.
   *
   * <p><i>Note:</i> Nothing will happen if this state is already reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean close(T newData) throws IOException {
    if (isClosed()) { // quick check to avoid synchronization
      return true;
    }
    final T previousData;
    Future<T> futureToCancel;

    synchronized (lock) {
      if (isClosed()) { // already closed so bail
        return true;
      }
      futureToCancel = future;
      previousData = this.data;
      LOGGER.debug("Solr({}): {} is closing", core, type);
      this.state = State.CLOSED;
      this.data = newData;
      this.future = null;
    }
    finalizeStateChange(true, futureToCancel, previousData, false);
    return false;
  }

  /**
   * Changes the state to available while updating the referenced data.
   *
   * <p><i>Note:</i> The specified data will replace the current one even if the state is already
   * reported as available. Nothing will happen if this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setAvailable(T newData) {
    if (isClosed()) { // quick check to avoid synchronization
      return true;
    }
    final boolean notifyAvailability;
    final T previousData;
    Future<T> futureToCancel;

    synchronized (lock) {
      if (isClosed()) { // already closed so bail
        return true;
      }
      futureToCancel = future;
      previousData = this.data;
      notifyAvailability = newData.isAvailable();
      if (notifyAvailability) {
        LOGGER.debug("Solr({}): {} is going available", core, type);
        lock.notifyAll(); // wakeup those waiting for isAvailable(timeout)
      } else {
        LOGGER.debug("Solr({}): {} is going unavailable", core, type);
      }
      this.data = newData;
      this.future = null;
    }
    try {
      finalizeStateChange(notifyAvailability, futureToCancel, previousData, true);
    } catch (IOException e) { // will never happen, exeptions are swallowed above
    }
    return false;
  }

  /**
   * Changes the state to unavailable while updating the referenced data.
   *
   * <p><i>Note:</i> This method differs from {@link #setUnavailableIfAvailable(Data)} in that it
   * will always update the referenced data whether the previous state was available or not. Nothing
   * will happen if this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setUnavailable(T newData) {
    return setUnavailable(newData, false);
  }

  /**
   * Changes the state to unavailable while updating the referenced data only if the current state
   * is available.
   *
   * <p><i>Note:</i> This methods differs from {@link #setUnavailable(Data)} in that it won't update
   * the referenced data if the current state was not reported as available. Nothing will happen if
   * this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setUnavailableIfAvailable(T newData) {
    return setUnavailable(newData, true);
  }

  /**
   * Changes the state to unavailable while updating the referenced data if needed or if requested.
   *
   * <p><i>Note:</i> Nothing will happen if this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @param onlyIfAvailable <code>true</code> to only update the referenced data if the state is
   *     changed from available to unavailable; <code>false</code> to change it even if the state
   *     was already unavailable
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  private boolean setUnavailable(T newData, boolean onlyIfAvailable) {
    if (isClosed()) { // quick check to avoid synchronization
      return true;
    }
    final boolean notifyAvailability;
    final Future<T> futureToCancel;
    final T previousDataToClose;

    synchronized (lock) {
      if (isClosed()) { // already closed so bail
        return true;
      }
      if (onlyIfAvailable && !isAvailable()) {
        return false;
      }
      futureToCancel = future;
      previousDataToClose = this.data;
      // notify only if we were available
      notifyAvailability = previousDataToClose.isAvailable();
      if (notifyAvailability) {
        LOGGER.debug("Solr({}): {} is going unavailable", core, type);
      }
      this.data = newData;
      LOGGER.debug("Solr({}): {} is starting a background task to create a client", core, type);
      this.future =
          Failsafe.<T>with(retryPolicy)
              .with(executor)
              .onRetry(this::retrying)
              .onAbort(this::aborted)
              .onFailure(this::reinitialize)
              .onSuccess(this::initialized)
              .get(initializer);
    }
    try {
      finalizeStateChange(notifyAvailability, futureToCancel, previousDataToClose, true);
    } catch (IOException e) { // will never happen, exeptions are swallowed above
    }
    return false;
  }

  private void finalizeStateChange(
      boolean notifyAvailability,
      @Nullable Future<T> futureToCancel,
      @Nullable T previousDataToClose,
      boolean swallowIOExceptions)
      throws IOException {
    if (notifyAvailability) {
      listener.run();
    }
    if ((futureToCancel != null) && !futureToCancel.isDone()) {
      LOGGER.debug("Solr({}): {} is cancelling its previous background task", core, type);
      futureToCancel.cancel(true);
    }
    Closeables.close(previousDataToClose, swallowIOExceptions);
  }

  @SuppressWarnings("unused" /* used as a method reference */)
  private void retrying(T returnedData, Throwable t, ExecutionContext ctx) {
    if (lastErrorWasNotRecent()) {
      failedLevel.log("Solr client ({}) {} failed; retrying again", core, initInfo);
    }
    LOGGER.debug(
        "Solr({}): {} client {} failed attempt #{}; retrying again",
        core,
        type,
        initInfo,
        ctx.getExecutions(),
        t);
  }

  private void aborted(Throwable t) {
    lastError.set(0L); // reset it
    failedLevel.log("Solr client ({}) {} aborted", core, initInfo);
    LOGGER.debug("Solr({}): {}'s client {} attempt was interrupted", core, type, initInfo, t);
  }

  private void reinitialize(Throwable t) {
    if (t instanceof CancellationException) { // don't restart if it was cancelled
      return;
    }
    LOGGER.debug(
        "Solr({}): {} client {} failed all attempts; re-initializing", core, type, initInfo, t);
    setUnavailable(data);
  }

  private void initialized(T returnedData) throws IOException {
    lastError.set(0L); // reset it
    initLevel.log("Solr client ({}) {} was successful", core, initInfo);
    LOGGER.debug("Solr({}): {} client {} was successful [{}]", core, type, initInfo, returnedData);
    setAvailable(returnedData);
  }

  private boolean lastErrorWasNotRecent() {
    final long now = System.currentTimeMillis();

    return (lastError.accumulateAndGet(now, StatefulCloseable::updateIfNotRecent) == now);
  }

  private static long updateIfNotRecent(long previous, long now) {
    return ((now - previous) >= StatefulCloseable.ERROR_MIN_FREQUENCY) ? now : previous;
  }

  private static String availableToString(boolean available) {
    return available ? "available" : "not available";
  }

  /** Interface used for any data that can be associated with a {@link StatefulCloseable} object. */
  public interface Data extends Closeable {
    /**
     * Checks if the associated data is considered available or not.
     *
     * @return <code>true</code> if the data is considered available; <code>false</code> otherwise
     */
    public boolean isAvailable();
  }

  /** Enumeration representing the various state for a {@link StatefulCloseable} object. */
  public enum State {
    CLOSED,
    AVAILABLE,
    UNAVAILABLE
  }

  /** Enumeration used to customize logging options for a {@link StatefulCloseable} object. */
  public enum Level {
    NONE {
      @Override
      public void log(String format, Object arg1, Object arg2) { // do nothing
      }
    },
    DEBUG {
      @Override
      public void log(String format, Object arg1, Object arg2) {
        LOGGER.debug(format, arg1, arg2);
      }
    },
    INFO {
      @Override
      public void log(String format, Object arg1, Object arg2) {
        LOGGER.info(format, arg1, arg2);
      }
    },
    WARN {
      @Override
      public void log(String format, Object arg1, Object arg2) {
        LOGGER.warn(format, arg1, arg2);
      }
    };

    public abstract void log(String format, Object arg1, Object arg2);
  }
}
