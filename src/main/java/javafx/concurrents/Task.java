/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package javafx.concurrents;

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrents.EventHelper;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.event.EventType;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import static javafx.concurrent.WorkerStateEvent.WORKER_STATE_CANCELLED;
import static javafx.concurrent.WorkerStateEvent.WORKER_STATE_FAILED;
import static javafx.concurrent.WorkerStateEvent.WORKER_STATE_RUNNING;
import static javafx.concurrent.WorkerStateEvent.WORKER_STATE_SCHEDULED;
import static javafx.concurrent.WorkerStateEvent.WORKER_STATE_SUCCEEDED;

public abstract class Task<V> extends FutureTask<V> implements Worker<V>, EventTarget {
    /**
     * Used to send workDone updates in a thread-safe manner from the subclass
     * to the FX application thread and workDone related properties. AtomicReference
     * is used so as to coalesce updates such that we don't flood the event queue.
     */
    protected AtomicReference<ProgressUpdate> progressUpdate = new AtomicReference<>();

    /**
     * Used to send message updates in a thread-safe manner from the subclass
     * to the FX application thread. AtomicReference is used so as to coalesce
     * updates such that we don't flood the event queue.
     */
    protected AtomicReference<String> messageUpdate = new AtomicReference<>();

    /**
     * Used to send title updates in a thread-safe manner from the subclass
     * to the FX application thread. AtomicReference is used so as to coalesce
     * updates such that we don't flood the event queue.
     */
    protected AtomicReference<String> titleUpdate = new AtomicReference<>();

    /**
     * Used to send value updates in a thread-safe manner from the subclass
     * to the FX application thread. AtomicReference is used so as to coalesce
     * updates such that we don't flood the event queue.
     */
    protected AtomicReference<V> valueUpdate = new AtomicReference<>();

    /**
     * This is used so we have a thread-safe way to ask whether the task was
     * started in the checkThread() method.
     */
    protected volatile boolean started = false;

    /**
     * Creates a new Task.
     */
    public Task() {
        this(new TaskCallable<V>());
    }

    /**
     * This bit of construction trickery is necessary because otherwise there is
     * no way for the main constructor to both create the callable and maintain
     * a reference to it, which is necessary because an anonymous callable construction
     * cannot reference the implicit "this". We leverage an internal Callable
     * so that all the pre-built semantics around cancel and so forth are
     * handled correctly.
     *
     * @param callableAdapter non-null implementation of the
     *                        TaskCallable adapter
     */
    protected Task(final TaskCallable<V> callableAdapter) {
        super(callableAdapter);
        callableAdapter.task = this;
    }

    /**
     * Invoked when the Task is executed, the call method must be overridden and
     * implemented by subclasses. The call method actually performs the
     * background thread logic. Only the updateProgress, updateMessage, updateValue and
     * updateTitle methods of Task may be called from code within this method.
     * Any other interaction with the Task from the background thread will result
     * in runtime exceptions.
     *
     * @return The result of the background work, if any.
     * @throws Exception an unhandled exception which occurred during the
     *                   background operation
     */
    protected abstract V call() throws Exception;

    protected ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", State.READY);

    protected final void setState(State value) { // package access for the Service
        checkThread();
        final State s = getState();
        if (s != State.CANCELLED) {
            this.state.set(value);
            // Make sure the running flag is set
            setRunning(value == State.SCHEDULED || value == State.RUNNING);

            // Invoke the event handlers, and then call the protected methods.
            switch (state.get()) {
                case CANCELLED:
                    fireEvent(new WorkerStateEvent(this, WORKER_STATE_CANCELLED));
                    cancelled();
                    break;
                case FAILED:
                    fireEvent(new WorkerStateEvent(this, WORKER_STATE_FAILED));
                    failed();
                    break;
                case READY:
                    // This even can never meaningfully occur, because the
                    // Task begins life as ready and can never go back to it!
                    break;
                case RUNNING:
                    fireEvent(new WorkerStateEvent(this, WORKER_STATE_RUNNING));
                    running();
                    break;
                case SCHEDULED:
                    fireEvent(new WorkerStateEvent(this, WORKER_STATE_SCHEDULED));
                    scheduled();
                    break;
                case SUCCEEDED:
                    fireEvent(new WorkerStateEvent(this, WORKER_STATE_SUCCEEDED));
                    succeeded();
                    break;
                default:
                    throw new AssertionError("Should be unreachable");
            }
        }
    }

    @Override
    public final State getState() {
        checkThread();
        return state.get();
    }

    @Override
    public final ReadOnlyObjectProperty<State> stateProperty() {
        checkThread();
        return state;
    }

    /**
     * The onSchedule event handler is called whenever the Task state
     * transitions to the SCHEDULED state.
     *
     * @return the onScheduled event handler property
     * @since JavaFX 2.1
     */
    public final ObjectProperty<EventHandler<WorkerStateEvent>> onScheduledProperty() {
        checkThread();
        return getEventHelper().onScheduledProperty();
    }

    /**
     * The onSchedule event handler is called whenever the Task state
     * transitions to the SCHEDULED state.
     *
     * @return the onScheduled event handler, if any
     * @since JavaFX 2.1
     */
    public final EventHandler<WorkerStateEvent> getOnScheduled() {
        checkThread();
        return eventHelper == null ? null : eventHelper.getOnScheduled();
    }

    /**
     * The onSchedule event handler is called whenever the Task state
     * transitions to the SCHEDULED state.
     *
     * @param value the event handler, can be null to clear it
     * @since JavaFX 2.1
     */
    public final void setOnScheduled(EventHandler<WorkerStateEvent> value) {
        checkThread();
        getEventHelper().setOnScheduled(value);
    }

    /**
     * A protected convenience method for subclasses, called whenever the
     * state of the Task has transitioned to the SCHEDULED state.
     * This method is invoked on the FX Application Thread after the Task has been fully transitioned to
     * the new state.
     *
     * @since JavaFX 2.1
     */
    protected void scheduled() {
    }

    /**
     * The onRunning event handler is called whenever the Task state
     * transitions to the RUNNING state.
     *
     * @return the onRunning event handler property
     * @since JavaFX 2.1
     */
    public final ObjectProperty<EventHandler<WorkerStateEvent>> onRunningProperty() {
        checkThread();
        return getEventHelper().onRunningProperty();
    }

    /**
     * The onRunning event handler is called whenever the Task state
     * transitions to the RUNNING state.
     *
     * @return the onRunning event handler, if any
     * @since JavaFX 2.1
     */
    public final EventHandler<WorkerStateEvent> getOnRunning() {
        checkThread();
        return eventHelper == null ? null : eventHelper.getOnRunning();
    }

    /**
     * The onRunning event handler is called whenever the Task state
     * transitions to the RUNNING state.
     *
     * @param value the event handler, can be null to clear it
     * @since JavaFX 2.1
     */
    public final void setOnRunning(EventHandler<WorkerStateEvent> value) {
        checkThread();
        getEventHelper().setOnRunning(value);
    }

    /**
     * A protected convenience method for subclasses, called whenever the
     * state of the Task has transitioned to the RUNNING state.
     * This method is invoked on the FX Application Thread after the Task has been fully transitioned to
     * the new state.
     *
     * @since JavaFX 2.1
     */
    protected void running() {
    }

    /**
     * The onSucceeded event handler is called whenever the Task state
     * transitions to the SUCCEEDED state.
     *
     * @return the onSucceeded event handler property
     * @since JavaFX 2.1
     */
    public final ObjectProperty<EventHandler<WorkerStateEvent>> onSucceededProperty() {
        checkThread();
        return getEventHelper().onSucceededProperty();
    }

    /**
     * The onSucceeded event handler is called whenever the Task state
     * transitions to the SUCCEEDED state.
     *
     * @return the onSucceeded event handler, if any
     * @since JavaFX 2.1
     */
    public final EventHandler<WorkerStateEvent> getOnSucceeded() {
        checkThread();
        return eventHelper == null ? null : eventHelper.getOnSucceeded();
    }

    /**
     * The onSucceeded event handler is called whenever the Task state
     * transitions to the SUCCEEDED state.
     *
     * @param value the event handler, can be null to clear it
     * @since JavaFX 2.1
     */
    public final void setOnSucceeded(EventHandler<WorkerStateEvent> value) {
        checkThread();
        getEventHelper().setOnSucceeded(value);
    }

    /**
     * A protected convenience method for subclasses, called whenever the
     * state of the Task has transitioned to the SUCCEEDED state.
     * This method is invoked on the FX Application Thread after the Task has been fully transitioned to
     * the new state.
     *
     * @since JavaFX 2.1
     */
    protected void succeeded() {
    }

    /**
     * The onCancelled event handler is called whenever the Task state
     * transitions to the CANCELLED state.
     *
     * @return the onCancelled event handler property
     * @since JavaFX 2.1
     */
    public final ObjectProperty<EventHandler<WorkerStateEvent>> onCancelledProperty() {
        checkThread();
        return getEventHelper().onCancelledProperty();
    }

    /**
     * The onCancelled event handler is called whenever the Task state
     * transitions to the CANCELLED state.
     *
     * @return the onCancelled event handler, if any
     * @since JavaFX 2.1
     */
    public final EventHandler<WorkerStateEvent> getOnCancelled() {
        checkThread();
        return eventHelper == null ? null : eventHelper.getOnCancelled();
    }

    /**
     * The onCancelled event handler is called whenever the Task state
     * transitions to the CANCELLED state.
     *
     * @param value the event handler, can be null to clear it
     * @since JavaFX 2.1
     */
    public final void setOnCancelled(EventHandler<WorkerStateEvent> value) {
        checkThread();
        getEventHelper().setOnCancelled(value);
    }

    /**
     * A protected convenience method for subclasses, called whenever the
     * state of the Task has transitioned to the CANCELLED state.
     * This method is invoked on the FX Application Thread after the Task has been fully transitioned to
     * the new state.
     *
     * @since JavaFX 2.1
     */
    protected void cancelled() {
    }

    /**
     * The onFailed event handler is called whenever the Task state
     * transitions to the FAILED state.
     *
     * @return the onFailed event handler property
     * @since JavaFX 2.1
     */
    public final ObjectProperty<EventHandler<WorkerStateEvent>> onFailedProperty() {
        checkThread();
        return getEventHelper().onFailedProperty();
    }

    /**
     * The onFailed event handler is called whenever the Task state
     * transitions to the FAILED state.
     *
     * @return the onFailed event handler, if any
     * @since JavaFX 2.1
     */
    public final EventHandler<WorkerStateEvent> getOnFailed() {
        checkThread();
        return eventHelper == null ? null : eventHelper.getOnFailed();
    }

    /**
     * The onFailed event handler is called whenever the Task state
     * transitions to the FAILED state.
     *
     * @param value the event handler, can be null to clear it
     * @since JavaFX 2.1
     */
    public final void setOnFailed(EventHandler<WorkerStateEvent> value) {
        checkThread();
        getEventHelper().setOnFailed(value);
    }

    /**
     * A protected convenience method for subclasses, called whenever the
     * state of the Task has transitioned to the FAILED state.
     * This method is invoked on the FX Application Thread after the Task has been fully transitioned to
     * the new state.
     *
     * @since JavaFX 2.1
     */
    protected void failed() {
    }

    protected final ObjectProperty<V> value = new SimpleObjectProperty<>(this, "value");

    protected void setValue(V v) {
        checkThread();
        value.set(v);
    }

    @Override
    public final V getValue() {
        checkThread();
        return value.get();
    }

    @Override
    public final ReadOnlyObjectProperty<V> valueProperty() {
        checkThread();
        return value;
    }

    protected final ObjectProperty<Throwable> exception = new SimpleObjectProperty<>(this, "exception");

    protected void _setException(Throwable value) {
        checkThread();
        exception.set(value);
    }

    @Override
    public final Throwable getException() {
        checkThread();
        return exception.get();
    }

    @Override
    public final ReadOnlyObjectProperty<Throwable> exceptionProperty() {
        checkThread();
        return exception;
    }

    protected final DoubleProperty workDone = new SimpleDoubleProperty(this, "workDone", -1);

    protected void setWorkDone(double value) {
        checkThread();
        workDone.set(value);
    }

    @Override
    public final double getWorkDone() {
        checkThread();
        return workDone.get();
    }

    @Override
    public final ReadOnlyDoubleProperty workDoneProperty() {
        checkThread();
        return workDone;
    }

    protected final DoubleProperty totalWork = new SimpleDoubleProperty(this, "totalWork", -1);

    protected void setTotalWork(double value) {
        checkThread();
        totalWork.set(value);
    }

    @Override
    public final double getTotalWork() {
        checkThread();
        return totalWork.get();
    }

    @Override
    public final ReadOnlyDoubleProperty totalWorkProperty() {
        checkThread();
        return totalWork;
    }

    protected final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", -1);

    protected void setProgress(double value) {
        checkThread();
        progress.set(value);
    }

    @Override
    public final double getProgress() {
        checkThread();
        return progress.get();
    }

    @Override
    public final ReadOnlyDoubleProperty progressProperty() {
        checkThread();
        return progress;
    }

    protected final BooleanProperty running = new SimpleBooleanProperty(this, "running", false);

    protected void setRunning(boolean value) {
        checkThread();
        running.set(value);
    }

    @Override
    public final boolean isRunning() {
        checkThread();
        return running.get();
    }

    @Override
    public final ReadOnlyBooleanProperty runningProperty() {
        checkThread();
        return running;
    }

    protected final StringProperty message = new SimpleStringProperty(this, "message", "");

    @Override
    public final String getMessage() {
        checkThread();
        return message.get();
    }

    @Override
    public final ReadOnlyStringProperty messageProperty() {
        checkThread();
        return message;
    }

    protected final StringProperty title = new SimpleStringProperty(this, "title", "");

    @Override
    public final String getTitle() {
        checkThread();
        return title.get();
    }

    @Override
    public final ReadOnlyStringProperty titleProperty() {
        checkThread();
        return title;
    }

    @Override
    public final boolean cancel() {
        return cancel(true);
    }

    // Need to assert the modifyThread permission so an app can cancel
    // a task that it created (the default executor for the service runs in
    // its own thread group)
    // Note that this is needed when running as an applet or a web start app.
    protected static final Permission modifyThreadPerm = new RuntimePermission("modifyThread");

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // Delegate to the super implementation to actually attempt to cancel this thing
        // Assert the modifyThread permission
        boolean flag = AccessController.doPrivileged(
                (PrivilegedAction<Boolean>) () -> super.cancel(mayInterruptIfRunning),
                null,
                modifyThreadPerm);

        // If cancel succeeded (according to the semantics of the Future cancel method),
        // then we need to make sure the State flag is set appropriately
        if (flag) {
            // If this method was called on the FX application thread, then we can
            // just update the state directly and this will make sure that after
            // the cancel method was called, the state will be set correctly
            // (otherwise it would be indeterminate). However if the cancel method was
            // called off the FX app thread, then we must use runLater, and the
            // state flag will not be readable immediately after this call. However,
            // that would be the case anyway since these properties are not thread-safe.
            if (isFxApplicationThread()) {
                setState(State.CANCELLED);
            } else {
                runLater(() -> setState(State.CANCELLED));
            }
        }
        // return the flag
        return flag;
    }

    /**
     * Updates the <code>workDone</code>, <code>totalWork</code>,
     * and <code>progress</code> properties. Calls to updateProgress
     * are coalesced and run later on the FX application thread, and calls
     * to updateProgress, even from the FX Application thread, may not
     * necessarily result in immediate updates to these properties, and
     * intermediate workDone values may be coalesced to save on event
     * notifications. <code>max</code> becomes the new value for
     * <code>totalWork</code>.
     * <p>
     * <em>This method is safe to be called from any thread.</em>
     * </p>
     *
     * @param workDone A value from Long.MIN_VALUE up to max. If the value is greater
     *                 than max, then it will be clamped at max.
     *                 If the value passed is negative then the resulting percent
     *                 done will be -1 (thus, indeterminate).
     * @param max      A value from Long.MIN_VALUE to Long.MAX_VALUE.
     * @see #updateProgress(double, double)
     */
    protected void updateProgress(long workDone, long max) {
        updateProgress((double) workDone, (double) max);
    }

    /**
     * Updates the <code>workDone</code>, <code>totalWork</code>,
     * and <code>progress</code> properties. Calls to updateProgress
     * are coalesced and run later on the FX application thread, and calls
     * to updateProgress, even from the FX Application thread, may not
     * necessarily result in immediate updates to these properties, and
     * intermediate workDone values may be coalesced to save on event
     * notifications. <code>max</code> becomes the new value for
     * <code>totalWork</code>.
     * <p>
     * <em>This method is safe to be called from any thread.</em>
     * </p>
     *
     * @param workDone A value from Double.MIN_VALUE up to max. If the value is greater
     *                 than max, then it will be clamped at max.
     *                 If the value passed is negative, or Infinity, or NaN,
     *                 then the resulting percentDone will be -1 (thus, indeterminate).
     * @param max      A value from Double.MIN_VALUE to Double.MAX_VALUE. Infinity and NaN are treated as -1.
     * @since JavaFX 2.2
     */
    protected void updateProgress(double workDone, double max) {
        // Adjust Infinity / NaN to be -1 for both workDone and max.
        if (Double.isInfinite(workDone) || Double.isNaN(workDone)) {
            workDone = -1;
        }

        if (Double.isInfinite(max) || Double.isNaN(max)) {
            max = -1;
        }

        if (workDone < 0) {
            workDone = -1;
        }

        if (max < 0) {
            max = -1;
        }

        // Clamp the workDone if necessary so as not to exceed max
        if (workDone > max) {
            workDone = max;
        }

        if (isFxApplicationThread()) {
            _updateProgress(workDone, max);
        } else if (progressUpdate.getAndSet(new ProgressUpdate(workDone, max)) == null) {
            runLater(() -> {
                final ProgressUpdate update = progressUpdate.getAndSet(null);
                _updateProgress(update.workDone, update.totalWork);
            });
        }
    }

    protected void _updateProgress(double workDone, double max) {
        setTotalWork(max);
        setWorkDone(workDone);
        if (workDone == -1) {
            setProgress(-1);
        } else {
            setProgress(workDone / max);
        }
    }

    /**
     * Updates the <code>message</code> property. Calls to updateMessage
     * are coalesced and run later on the FX application thread, so calls
     * to updateMessage, even from the FX Application thread, may not
     * necessarily result in immediate updates to this property, and
     * intermediate message values may be coalesced to save on event
     * notifications.
     * <p>
     * <em>This method is safe to be called from any thread.</em>
     * </p>
     *
     * @param message the new message
     */
    protected void updateMessage(String message) {
        if (isFxApplicationThread()) {
            this.message.set(message);
        } else {
            // As with the workDone, it might be that the background thread
            // will update this message quite frequently, and we need
            // to throttle the updates so as not to completely clobber
            // the event dispatching system.
            if (messageUpdate.getAndSet(message) == null) {
                runLater(new Runnable() {
                    @Override
                    public void run() {
                        final String message = messageUpdate.getAndSet(null);
                        Task.this.message.set(message);
                    }
                });
            }
        }
    }

    /**
     * Updates the <code>title</code> property. Calls to updateTitle
     * are coalesced and run later on the FX application thread, so calls
     * to updateTitle, even from the FX Application thread, may not
     * necessarily result in immediate updates to this property, and
     * intermediate title values may be coalesced to save on event
     * notifications.
     * <p>
     * <em>This method is safe to be called from any thread.</em>
     * </p>
     *
     * @param title the new title
     */
    protected void updateTitle(String title) {
        if (isFxApplicationThread()) {
            this.title.set(title);
        } else {
            // As with the workDone, it might be that the background thread
            // will update this title quite frequently, and we need
            // to throttle the updates so as not to completely clobber
            // the event dispatching system.
            if (titleUpdate.getAndSet(title) == null) {
                runLater(new Runnable() {
                    @Override
                    public void run() {
                        final String title = titleUpdate.getAndSet(null);
                        Task.this.title.set(title);
                    }
                });
            }
        }
    }

    /**
     * Updates the <code>value</code> property. Calls to updateValue
     * are coalesced and run later on the FX application thread, so calls
     * to updateValue, even from the FX Application thread, may not
     * necessarily result in immediate updates to this property, and
     * intermediate values may be coalesced to save on event
     * notifications.
     * <p>
     * <em>This method is safe to be called from any thread.</em>
     * </p>
     *
     * @param value the new value
     * @since JavaFX 8.0
     */
    protected void updateValue(V value) {
        if (isFxApplicationThread()) {
            this.value.set(value);
        } else {
            // As with the workDone, it might be that the background thread
            // will update this value quite frequently, and we need
            // to throttle the updates so as not to completely clobber
            // the event dispatching system.
            if (valueUpdate.getAndSet(value) == null) {
                runLater(() -> Task.this.value.set(valueUpdate.getAndSet(null)));
            }
        }
    }

    /*
     * IMPLEMENTATION
     */
    protected void checkThread() {
        if (started && !isFxApplicationThread()) {
            throw new IllegalStateException("Task must only be used from the FX Application Thread");
        }
    }

    // This method exists for the sake of testing, so I can subclass and override
    // this method in the test and not actually use Platform.runLater.
    void runLater(Runnable r) {
        Platform.runLater(r);
    }

    // This method exists for the sake of testing, so I can subclass and override
    // this method in the test and not actually use Platform.isFxApplicationThread.
    boolean isFxApplicationThread() {
        return Platform.isFxApplicationThread();
    }

    /***************************************************************************
     *                                                                         *
     *                         Event Dispatch                                  *
     *                                                                         *
     **************************************************************************/

    protected EventHelper eventHelper = null;

    protected EventHelper getEventHelper() {
        if (eventHelper == null) {
            eventHelper = new EventHelper(this);
        }
        return eventHelper;
    }

    /**
     * Registers an event handler to this task. Any event filters are first
     * processed, then the specified onFoo event handlers, and finally any
     * event handlers registered by this method. As with other events
     * in the scene graph, if an event is consumed, it will not continue
     * dispatching.
     *
     * @param <T>          the specific event class of the handler
     * @param eventType    the type of the events to receive by the handler
     * @param eventHandler the handler to register
     * @throws NullPointerException if the event type or handler is null
     * @since JavaFX 2.1
     */
    public final <T extends Event> void addEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        checkThread();
        getEventHelper().addEventHandler(eventType, eventHandler);
    }

    /**
     * Unregisters a previously registered event handler from this task. One
     * handler might have been registered for different event types, so the
     * caller needs to specify the particular event type from which to
     * unregister the handler.
     *
     * @param <T>          the specific event class of the handler
     * @param eventType    the event type from which to unregister
     * @param eventHandler the handler to unregister
     * @throws NullPointerException if the event type or handler is null
     * @since JavaFX 2.1
     */
    public final <T extends Event> void removeEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        checkThread();
        getEventHelper().removeEventHandler(eventType, eventHandler);
    }

    /**
     * Registers an event filter to this task. Registered event filters get
     * an event before any associated event handlers.
     *
     * @param <T>         the specific event class of the filter
     * @param eventType   the type of the events to receive by the filter
     * @param eventFilter the filter to register
     * @throws NullPointerException if the event type or filter is null
     * @since JavaFX 2.1
     */
    public final <T extends Event> void addEventFilter(
            final EventType<T> eventType,
            final EventHandler<? super T> eventFilter) {
        checkThread();
        getEventHelper().addEventFilter(eventType, eventFilter);
    }

    /**
     * Unregisters a previously registered event filter from this task. One
     * filter might have been registered for different event types, so the
     * caller needs to specify the particular event type from which to
     * unregister the filter.
     *
     * @param <T>         the specific event class of the filter
     * @param eventType   the event type from which to unregister
     * @param eventFilter the filter to unregister
     * @throws NullPointerException if the event type or filter is null
     * @since JavaFX 2.1
     */
    public final <T extends Event> void removeEventFilter(
            final EventType<T> eventType,
            final EventHandler<? super T> eventFilter) {
        checkThread();
        getEventHelper().removeEventFilter(eventType, eventFilter);
    }

    /**
     * Sets the handler to use for this event type. There can only be one such
     * handler specified at a time. This handler is guaranteed to be called
     * first. This is used for registering the user-defined onFoo event
     * handlers.
     *
     * @param <T>          the specific event class of the handler
     * @param eventType    the event type to associate with the given eventHandler
     * @param eventHandler the handler to register, or null to unregister
     * @throws NullPointerException if the event type is null
     * @since JavaFX 2.1
     */
    protected final <T extends Event> void setEventHandler(
            final EventType<T> eventType,
            final EventHandler<? super T> eventHandler) {
        checkThread();
        getEventHelper().setEventHandler(eventType, eventHandler);
    }

    /**
     * Fires the specified event. Any event filter encountered will
     * be notified and can consume the event. If not consumed by the filters,
     * the event handlers on this task are notified. If these don't consume the
     * event either, then all event handlers are called and can consume the
     * event.
     * <p>
     * This method must be called on the FX user thread.
     *
     * @param event the event to fire
     * @since JavaFX 2.1
     */
    public final void fireEvent(Event event) {
        checkThread();
        getEventHelper().fireEvent(event);
    }

    @Override
    public EventDispatchChain buildEventDispatchChain(EventDispatchChain tail) {
        checkThread();
        return getEventHelper().buildEventDispatchChain(tail);
    }

    /**
     * A struct like class that contains the last workDone update information.
     * What we do when updateProgress is called, is we create a new ProgressUpdate
     * object and store it. If it was null, then we fire off a new Runnable
     * using RunLater, which will eventually read the latest and set it to null
     * atomically. If it was not null, then we simply update it.
     */
    protected static final class ProgressUpdate {
        protected final double workDone;
        protected final double totalWork;

        protected ProgressUpdate(double p, double m) {
            this.workDone = p;
            this.totalWork = m;
        }
    }

    /**
     * TaskCallable actually implements the Callable contract as defined for
     * the FutureTask class, and is necessary so as to allow us to intercept
     * the call() operation to update state on the Task as appropriate.
     *
     * @param <V>
     */
    public static final class TaskCallable<V> implements Callable<V> {
        /**
         * The Task that is going to use this TaskCallable
         */
        public Task<V> task;

        /**
         * Create a TaskCallable. The concurrent and other fields MUST be set
         * immediately after creation.
         */
        public TaskCallable() {
        }

        /**
         * Invoked by the system when it is time to run the client code. This
         * implementation is where we modify the state and other properties
         * and from which we invoke the events.
         *
         * @return The result of the Task call method
         * @throws Exception any exception which occurred
         */
        @Override
        public V call() throws Exception {
            // If the Task is sent to an ExecutorService for execution, then we
            // will need to make sure that we transition first to the SCHEDULED
            // state before then transitioning to the RUNNING state. If the
            // Task was executed by a Service, then it will have already been
            // in the SCHEDULED state and setting it again here has no negative
            // effect. But we must ensure that SCHEDULED is visited before RUNNING
            // in all cases so that developer code can be consistent.
            task.started = true;
            task.runLater(() -> {
                task.setState(State.SCHEDULED);
                task.setState(State.RUNNING);
            });
            // Go ahead and delegate to the wrapped callable
            try {
                final V result = task.call();
                if (!task.isCancelled()) {
                    // If it was not cancelled, then we take the return
                    // value and set it as the result.
                    task.runLater(() -> {
                        // The result must be set first, so that when the
                        // SUCCEEDED flag is set, the value will be available
                        // The alternative is not the case, because you
                        // can assume if the result is set, it has
                        // succeeded.
                        task.updateValue(result);
                        task.setState(State.SUCCEEDED);
                    });
                    return result;
                } else {
                    // Since cancelled Future/FutureTask doesn't return any value,
                    // the returned value is going to be trashed, so we can jus return null
                    return null;
                }
            } catch (final Throwable th) {
                // Be sure to set the state after setting the cause of failure
                // so that developers handling the state change events have a
                // throwable to inspect when they get the FAILED state. Note
                // that the other way around is not important -- when a developer
                // observes the causeOfFailure is set to a non-null value, even
                // though the state has not yet been updated, he can infer that
                // it will be FAILED because it can be nothing other than FAILED
                // in that circumstance.
                task.runLater(() -> {
                    task._setException(th);
                    task.setState(State.FAILED);
                });
                // Some error occurred during the call (it might be
                // an exception (either runtime or checked), or it might
                // be an error. In any case, we capture the throwable,
                // record it as the causeOfFailure, and then rethrow. However
                // since the Callable interface requires that we throw an
                // Exception (not Throwable), we have to wrap the exception
                // if it is not already one.
                if (th instanceof Exception) {
                    th.printStackTrace();
                    throw (Exception) th;
                } else {
                    th.printStackTrace();
                    throw new Exception(th);
                }
            }
        }
    }
}
