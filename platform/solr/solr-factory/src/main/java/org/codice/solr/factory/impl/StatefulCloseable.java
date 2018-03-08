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

import com.google.common.annotations.VisibleForTesting;
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
 * This class is used to track the state and initialization of an object while referencing a
 * closeable piece of data. It is design to initialize or re-initialize that piece of data in the
 * background whenever its availability is changed to not available. Any close request will be
 * propagated to the referenced closeable piece of data.
 *
 * <p>The availability state can be changed via one of {@link #setInitialized(Data)}, {@link
 * #setInitializing(Data)}, or {@link #setInitializingIfInitialized(Data)} methods. It can be closed
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

  private volatile Level initLevel;

  private volatile Level failedLevel;

  // writes and reads are always protected by synchronization
  @Nullable private Future<T> future = null;

  // writes are always protected by synchronization, reads are not
  private State state = State.INITIALIZING;

  // writes are always protected by synchronization, reads are not
  private volatile T data;

  private final AtomicLong lastError = new AtomicLong();

  /**
   * Starts the creation process for a {@link StatefulCloseable} to be used for managing the
   * specified core state for a given type of owner.
   *
   * @param core the Solr core for which this state is going to be used
   * @param type type information for the owner of this object
   * @return a temporary builder object to help chain the creation of the state object
   * @throws IllegalArgumentException if <code>core</code> or <code>type</code> is <code>null</code>
   */
  public static Builder retryingFor(String core, String type) {
    Validate.notNull(core, "invalid null core");
    Validate.notNull(type, "invalid null owner type");
    return new Builder(core, type);
  }

  StatefulCloseable(Builder5 builder, T initialData) {
    this.core = builder.builder.builder.builder.builder.core;
    this.type = builder.builder.builder.builder.builder.type;
    this.initInfo = builder.builder.builder.builder.initInfo;
    this.listener = builder.listener;
    this.executor = builder.builder.executor;
    this.retryPolicy = builder.builder.builder.retryPolicy;
    this.initializer = builder.builder.builder.builder.initializer;
    this.data = initialData;
    this.initLevel = builder.initLevel;
    this.failedLevel = builder.failedLevel;
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
   * Checks whether this state was closed.
   *
   * @return <code>true</code> if the state was closed; <code>false</code> otherwise
   */
  public boolean isClosed() {
    return state == State.CLOSED;
  }

  /**
   * Checks whether this state is initialized and the referenced data is available.
   *
   * @return <code>true</code> if this state is initialized and the referenced data is available;
   *     <code>false</code> otherwise
   */
  public boolean isAvailable() {
    if (isClosed()) {
      LOGGER.debug("Solr({}): {}'s current availability is [{}]", core, type, state);
      return false;
    }
    final boolean dataAvailability = data.isAvailable();
    final boolean available = (state == State.INITIALIZED) && dataAvailability;

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Solr({}): {}'s current availability is [{} & {} = {}]",
          core,
          type,
          state,
          StatefulCloseable.availableToString(dataAvailability),
          StatefulCloseable.availableToString(available));
    }
    return available;
  }

  /**
   * Waits if necessary for this state to be initialized and for the referenced data to become
   * available.
   *
   * <p><i>Note:</i> Nothing will happen and the method will return right away if the state was
   * already closed.
   *
   * @return <code>true</code> if this state is initialized and the referenced data is available or
   *     become initialized and available before the specified timeout expires; <code>false</code>
   *     otherwise
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
   * Changes the state to initialized while updating the referenced data.
   *
   * <p><i>Note:</i> The specified data will replace the current one even if the state was already
   * initialized and the currently referenced data was available. Nothing will happen if this state
   * is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setInitialized(T newData) {
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
      if (isAvailable()) {
        // notify only if we were available and we will now no longer be
        notifyAvailability = !newData.isAvailable();
        if (notifyAvailability) {
          LOGGER.debug("Solr({}): {} is going unavailable", core, type);
        }
      } else {
        // notify only if we were not available and we will now be
        notifyAvailability = newData.isAvailable();
        if (notifyAvailability) {
          LOGGER.debug("Solr({}): {} is going available", core, type);
          lock.notifyAll(); // wakeup those waiting for isAvailable(timeout)
        }
        this.state = State.INITIALIZED;
      }
      this.data = newData;
      this.future = null;
    }
    try {
      finalizeStateChange(notifyAvailability, futureToCancel, previousData, true);
    } catch (IOException e) { // will never happen, exceptions are swallowed above
    }
    return false;
  }

  /**
   * Changes the state to initializing while updating the referenced data.
   *
   * <p><i>Note:</i> This method differs from {@link #setInitializingIfInitialized(Data)} in that it
   * will always update the referenced data whether it was initialized already or not. Nothing will
   * happen if this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setInitializing(T newData) {
    return setInitializing(newData, false);
  }

  /**
   * Changes the state to initializing while updating the referenced data only if this state is
   * currently initialized.
   *
   * <p><i>Note:</i> This methods differs from {@link #setInitializing(Data)} in that it won't
   * update the referenced data if the current state was not initialized. Nothing will happen if
   * this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  public boolean setInitializingIfInitialized(T newData) {
    return setInitializing(newData, true);
  }

  /**
   * Gets the current state.
   *
   * @return the current state
   */
  @VisibleForTesting
  State getState() {
    return state;
  }

  /**
   * Changes the state to initializing while updating the referenced data if needed or if requested.
   *
   * <p><i>Note:</i> Nothing will happen if this state is reported as closed.
   *
   * @param newData the new referenced data from this state
   * @param onlyIfInitialized <code>true</code> to only update the referenced data if the state is
   *     currently initialized; <code>false</code> to change it even if the state was already
   *     initializing
   * @return <code>true</code> if already closed; <code>false</code> otherwise
   */
  private boolean setInitializing(T newData, boolean onlyIfInitialized) {
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
      if (onlyIfInitialized && (state == State.INITIALIZING)) {
        return false;
      }
      futureToCancel = future;
      previousDataToClose = data;
      // notify only if we were available
      notifyAvailability = isAvailable();
      if (notifyAvailability) {
        LOGGER.debug("Solr({}): {} is going unavailable", core, type);
      }
      this.state = State.INITIALIZING;
      this.data = newData;
      LOGGER.debug(
          "Solr({}): {} is starting a background task for client {}", core, type, initInfo);
      this.future =
          Failsafe.<T>with(retryPolicy)
              .with(executor)
              .onRetry(this::logFailure)
              .onAbort(this::logInterruption)
              .onFailure(this::logAndReinitializeIfNotCancelled)
              .onSuccess(this::logAndSetInitialized)
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
    if (previousDataToClose != data) { // don't close if we still reference the same data
      Closeables.close(previousDataToClose, swallowIOExceptions);
    }
  }

  private void logFailure(T returnedData, Throwable t, ExecutionContext ctx) {
    if (lastErrorWasNotRecent()) {
      failedLevel.log("Solr client ({}) {} failed; retrying again", core, initInfo);
    }
    LOGGER.debug(
        "Solr({}): {} failed attempt #{} for client {}; retrying again: {}",
        core,
        type,
        ctx.getExecutions(),
        initInfo,
        returnedData,
        t);
  }

  private void logInterruption(Throwable t) {
    lastError.set(0L); // reset it
    failedLevel.log("Solr client ({}) {} aborted", core, initInfo);
    LOGGER.debug("Solr({}): {}'s client {} attempts were interrupted", core, type, initInfo, t);
  }

  private void logAndReinitializeIfNotCancelled(Throwable t) {
    if (t instanceof CancellationException) { // don't restart if it was cancelled
      return;
    }
    LOGGER.debug(
        "Solr({}): {} failed all attempts for client {}; re-initializing", core, type, initInfo, t);
    setInitializing(data);
  }

  private void logAndSetInitialized(T returnedData, ExecutionContext ctx) {
    lastError.set(0L); // reset it
    initLevel.log("Solr client ({}) {} was successful", core, initInfo);
    LOGGER.debug(
        "Solr({}): {} client {} was successful after {} attempts: {}",
        core,
        type,
        initInfo,
        ctx.getExecutions(),
        returnedData);
    setInitialized(returnedData);
  }

  private boolean lastErrorWasNotRecent() {
    final long now = System.currentTimeMillis();

    return (lastError.accumulateAndGet(now, StatefulCloseable::updateIfNotRecent) == now);
  }

  private static long updateIfNotRecent(long previous, long now) {
    return ((now - previous) >= StatefulCloseable.ERROR_MIN_FREQUENCY) ? now : previous;
  }

  private static String availableToString(boolean available) {
    return available ? "AVAILABLE" : "NOT AVAILABLE";
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
    INITIALIZED,
    INITIALIZING
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

  /** This class is used when creating a {@link StatefulCloseable} object. */
  public static class Builder {
    private final String core;
    private final String type;

    private Builder(String core, String type) {
      this.core = core;
      this.type = type;
    }

    /**
     * Continues the creation process for a {@link StatefulCloseable} with the specified
     * initialization information and code.
     *
     * @param <T> The type of closeable data referenced
     * @param initInfo information identifying the initialization operation
     * @param initializer the initializer to call to perform the initialization
     * @return a temporary builder object to help chain the creation of the state object
     * @throws IllegalArgumentException if <code>initInfo</code> or <code>initializer</code> is
     *     <code>null</code>
     */
    public <T extends Data> Builder2 to(String initInfo, Callable<T> initializer) {
      Validate.notNull(initInfo, "invalid null initialization information");
      Validate.notNull(initializer, "invalid null initializer");
      return new Builder2<>(this, initInfo, initializer);
    }
  }

  /** This class is used when creating a {@link StatefulCloseable} object. */
  public static class Builder2<T extends Data> {
    private final Builder builder;
    private final String initInfo;
    private final Callable<T> initializer;

    private Builder2(Builder builder, String initInfo, Callable<T> initializer) {
      this.builder = builder;
      this.initInfo = initInfo;
      this.initializer = initializer;
    }

    /**
     * Continues the creation process for a {@link StatefulCloseable} with the specified retry
     * policy while attempt to initialize the referenced data.
     *
     * @param retryPolicy the retry policy to use for when scheduling initialization attempts
     * @return a temporary builder object to help chain the creation of the state object
     * @throws IllegalArgumentException if <code>retryPolicy</code> is <code>null</code>
     */
    public Builder3 until(RetryPolicy retryPolicy) {
      Validate.notNull(retryPolicy, "invalid null retry policy");
      return new Builder3<>(this, retryPolicy);
    }
  }

  /** This class is used when creating a {@link StatefulCloseable} object. */
  public static class Builder3<T extends Data> {
    private final Builder2 builder;
    private final RetryPolicy retryPolicy;

    private Builder3(Builder2 builder, RetryPolicy retryPolicy) {
      this.builder = builder;
      this.retryPolicy = retryPolicy;
    }

    /**
     * Continues the creation process for a {@link StatefulCloseable} with the specified executor
     * service to use when performing the initialization.
     *
     * @param executor the executor to use for scheduling initialization attempts
     * @return a temporary builder object to help chain the creation of the state object
     * @throws IllegalArgumentException if <code>executor</code> is <code>null</code>
     */
    public Builder4<T> using(ScheduledExecutorService executor) {
      Validate.notNull(executor, "invalid null executor");
      return new Builder4<>(this, executor);
    }
  }

  /** This class is used when creating a {@link StatefulCloseable} object. */
  public static class Builder4<T extends Data> {
    private final Builder3<T> builder;
    private final ScheduledExecutorService executor;

    private Builder4(Builder3 builder, ScheduledExecutorService executor) {
      this.builder = builder;
      this.executor = executor;
    }

    /**
     * Continues the creation process for a {@link StatefulCloseable} with the specified listener to
     * be called back whenever a changes in availability occurs.
     *
     * @param listener a listener to callback whenever the availability as reported by the state
     *     object has changed
     * @return a temporary builder object to help chain the creation of the state object
     * @throws IllegalArgumentException if <code>listener</code> is <code>null</code>
     */
    public Builder5<T> whenAvailabilityChanges(Runnable listener) {
      Validate.notNull(listener, "invalid null listener");
      return new Builder5<>(this, listener);
    }
  }

  /** This class is used when creating a {@link StatefulCloseable} object. */
  public static class Builder5<T extends Data> {
    private final Builder4<T> builder;
    private final Runnable listener;
    private Level initLevel = Level.NONE;
    private Level failedLevel = Level.NONE;

    private Builder5(Builder4 builder, Runnable listener) {
      this.builder = builder;
      this.listener = listener;
    }

    /**
     * Sets a log level at which to log initialization successes in addition to being logged at
     * debug level. By default only the debug logs is generated.
     *
     * @param newInitLevel a log level at which to log a successful initialization
     * @return a temporary builder object to help chain the creation of the state object
     */
    public Builder5<T> withInitLogLevel(Level newInitLevel) {
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
     * @return a temporary builder object to help chain the creation of the state object
     */
    public Builder5<T> withFailedLogLevel(Level newFailedLevel) {
      this.failedLevel = newFailedLevel;
      return this;
    }

    /**
     * Continues the creation process for a {@link StatefulCloseable} with the specified data to be
     * initialized for the state object while kick starting the initialization process.
     *
     * @param initialData the initial referenced data
     * @return a newly created {@link StatefulCloseable} object
     * @throws IllegalArgumentException if <code>initialData</code> is <code>null</code>
     */
    public <T extends Data> StatefulCloseable<T> initializingWith(T initialData) {
      Validate.notNull(initialData, "invalid null initial data");
      final StatefulCloseable<T> state = new StatefulCloseable<>(this, initialData);

      state.setInitializing(initialData, false); // to trigger the initialization retries
      return state;
    }
  }
}
