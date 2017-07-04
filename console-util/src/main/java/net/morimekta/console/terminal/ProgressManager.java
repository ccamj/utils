package net.morimekta.console.terminal;

import com.google.common.annotations.VisibleForTesting;
import net.morimekta.console.chr.Color;
import net.morimekta.util.Strings;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.morimekta.console.terminal.Progress.format;

/**
 * Show progress on a number of tasks. The tasks can be dynamically created
 * and finished. E.g if a number of large files needs to be downloaded they
 * can be given a task each, and only a certain number of files will be downloaded
 * at the same time. Example:
 *
 * <pre>{@code
 * try (ProgressManager progress = new ProgressManager(term, Progress.Spinner.CLOCK)) {
 *     Future<String> first = progress.addTask("First Task", 10000, task -> {
 *         // All the work
 *         task.accept(10000);
 *         return "OK";
 *     });
 *     Future<String> second = progress.addTask("Second Task", 10000, task -> {
 *         // All the work
 *         task.accept(10000);
 *         return "OK";
 *     });
 *
 *     progress.waitAbortable();
 *
 *     term.println("First: " + first.get());
 *     term.println("Second: " + second.get());
 * } finally {
 *     term.println();
 * }
 * }</pre>
 */
public class ProgressManager implements AutoCloseable {
    @FunctionalInterface
    public interface ProgressHandler<T> {
        T handle(@Nonnull ProgressTask progress) throws Exception;
    }

    /**
     * Create a progress bar using the given terminal.
     *
     * @param terminal The terminal to use.
     * @param spinner  The spinner to use.
     */
    public ProgressManager(@Nonnull Terminal terminal,
                           @Nonnull Progress.Spinner spinner) {
        this(terminal,
             spinner,
             DEFAULT_MAX_TASKS);
    }

    /**
     * Create a progress bar using the given terminal.
     *
     * @param terminal The terminal to use.
     * @param spinner  The spinner to use.
     * @param max_tasks Maximum number fo concurrent inProgress.
     */
    public ProgressManager(@Nonnull Terminal terminal,
                           @Nonnull Progress.Spinner spinner,
                           int max_tasks) {
        this(terminal,
             spinner,
             max_tasks,
             Executors.newFixedThreadPool(max_tasks + 1),
             Clock.systemUTC());
    }

    /**
     * Create a progress updater. Note that <b>either</b> terminal or the
     * updater param must be set.
     *
     * @param terminal The terminal to print to.
     * @param spinner  The spinner type.
     * @param executor The executor to run updater task in.
     * @param clock    The clock to use for timing.
     */
    @VisibleForTesting
    ProgressManager(Terminal terminal,
                    Progress.Spinner spinner,
                    int maxTasks,
                    ExecutorService executor,
                    Clock clock) {
        this.terminal = terminal;
        this.executor = executor;
        this.clock = clock;
        this.spinner = spinner;

        this.maxTasks = maxTasks;
        this.startedTasks = new LinkedList<>();
        this.queuedTasks = new ConcurrentLinkedQueue<>();
        this.buffer = new LineBuffer(terminal);
        this.isWaiting = new AtomicBoolean(false);
        this.updater = executor.submit(this::doUpdate);
    }

    /**
     * Close the progress and all tasks associated with it.
     */
    @Override
    public void close() {
        if (updater.isCancelled()) return;

        // ... stop updater thread. Do not interrupt.
        updater.cancel(false);
        synchronized (startedTasks) {
            // And stop all the tasks, do interrupting.
            for (InternalTask task : startedTasks) {
                task.cancel(true);
            }
            for (InternalTask task : queuedTasks) {
                task.close();
            }
        }
        try {
            executor.shutdown();
            executor.awaitTermination(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignore) {
        }
    }

    /**
     * Wait for all scheduled tasks to finish allowing the user to abort all
     * tasks with &lt;ctrl&gt;-C.
     *
     * @throws IOException If interrupted by user.
     * @throws InterruptedException If interrupted by system or other threads.
     */
    public void waitAbortable() throws IOException, InterruptedException {
        try {
            isWaiting.set(true);
            terminal.waitAbortable(updater);
        } finally {
            close();
            Thread.sleep(1L);
            updateLines();
            terminal.finish();
        }
    }

    /**
     * Add a task to be done while showing progress. If there are too many tasks
     * ongoing, the task will be queued and done when the local thread pool has
     * available threads.
     *
     * @param title The progress title of the task.
     * @param total The total progress to complete.
     * @param handler The handler to do the task behind the progress being shown.
     * @param <T> The return type for the task.
     * @return The future returning the task result.
     */
    public <T> Future<T> addTask(String title,
                                 long total,
                                 ProgressHandler<T> handler) {
        InternalTask<T> task = new InternalTask<>(clock, title, total, handler);
        queuedTasks.add(task);
        startTasks();
        return task;
    }

    protected List<String> lines() {
        return buffer.lines();
    }

    // ------ private ------

    private static final int DEFAULT_MAX_TASKS = 5;

    private final Terminal                 terminal;
    private final ExecutorService          executor;
    private final Progress.Spinner         spinner;
    private final Clock                    clock;
    private final Future<?>                updater;
    private final LinkedList<InternalTask> startedTasks;
    private final Queue<InternalTask>      queuedTasks;
    private final LineBuffer               buffer;
    private final AtomicBoolean            isWaiting;
    private final int                      maxTasks;

    private void startTasks() {
        synchronized (startedTasks) {
            int toAdd = maxTasks;
            for (InternalTask task : startedTasks) {
                if (!task.isDone()) {
                    --toAdd;
                }
            }

            while (toAdd-- > 0 && !queuedTasks.isEmpty()) {
                InternalTask task = queuedTasks.poll();
                startedTasks.add(task);
                task.start(executor, this::startTasks);
            }
        }
    }

    private int getTerminalWidth() {
        return terminal.getTTY().getTerminalSize().cols;
    }

    private boolean isDone() {
        if (!queuedTasks.isEmpty()) return false;
        synchronized (startedTasks) {
            for (InternalTask task : startedTasks) {
                if (!task.isDone()) {
                    return false;
                }
            }
        }

        return true;
    }

    @VisibleForTesting
    protected void sleep(long ms) throws InterruptedException {
        Thread.sleep(10L);
    }

    private void doUpdate() {
        try {
            // Allow for time to add the first task(s).
            sleep(10L);

            while (!updater.isCancelled()) {
                updateLines();
                if (updater.isCancelled() ||
                    (isWaiting.get() && isDone())) {
                    break;
                }
                sleep(100L);
            }
        } catch (InterruptedException ignore) {
            synchronized (startedTasks) {
                // And stop all the tasks, do interrupting.
                for (InternalTask task : startedTasks) {
                    task.cancel(true);
                }
                for (InternalTask task : queuedTasks) {
                    task.cancel(false);
                }
            }
        }
    }

    private void updateLines() {
        List<String> updatedLines = new LinkedList<>();
        synchronized (startedTasks) {
            for (InternalTask task : startedTasks) {
                updatedLines.add(renderTask(task));
            }
            if (queuedTasks.size() > 0) {
                updatedLines.add(" -- And " + queuedTasks.size() + " more...");
            }
        }
        if (updatedLines.size() > 0) {
            while (buffer.count() < updatedLines.size()) {
                buffer.add("");
            }

            buffer.update(0, updatedLines);
        }
    }

    private String renderTask(@Nonnull InternalTask<?> task) {
        long  now   = clock.millis();

        synchronized (task) {
            int pts_w = getTerminalWidth() - 23 - task.title.length();
            int pct   = (int) (task.fraction * 100);
            int pts   = (int) (task.fraction * pts_w);

            int remaining_pts = pts_w - pts;

            if (task.failed.get()) {
                return String.format("%s: [%s%s%s%s%s] %3d%% %sFailed%s",
                                     task.title,

                                     Color.GREEN,
                                     Strings.times(spinner.done.toString(), pts),
                                     Color.YELLOW,
                                     Strings.times(spinner.remain.toString(), remaining_pts),
                                     Color.CLEAR,

                                     pct,

                                     new Color(Color.RED, Color.BOLD),
                                     Color.CLEAR);
            } else if (task.cancelled.get()) {
                return String.format("%s: [%s%s%s%s%s] %3d%% %sCancelled%s",
                                     task.title,

                                     Color.GREEN,
                                     Strings.times(spinner.done.toString(), pts),
                                     Color.YELLOW,
                                     Strings.times(spinner.remain.toString(), remaining_pts),
                                     Color.CLEAR,

                                     pct,

                                     new Color(Color.RED, Color.BOLD),
                                     Color.CLEAR);
            } else if (task.fraction < 1.0) {
                Duration remaining = null;
                // Progress has actually gone forward, recalculate total time.
                if (task.expected_done_ts > 0) {
                    long remaining_ms = max(0L, task.expected_done_ts - now);
                    remaining = Duration.of(remaining_ms, ChronoUnit.MILLIS);
                }

                int spinner_pos = task.spinner_pos % spinner.spinner.length;

                return String.format("%s: [%s%s%s%s%s] %3d%% %s%s%s%s",
                                     task.title,

                                     Color.GREEN,
                                     Strings.times(spinner.done.toString(), pts),
                                     Color.YELLOW,
                                     Strings.times(spinner.remain.toString(), remaining_pts),
                                     Color.CLEAR,

                                     pct,

                                     new Color(Color.YELLOW, Color.BOLD),
                                     spinner.spinner[spinner_pos],
                                     Color.CLEAR,
                                     remaining == null ? "" : " + " + format(remaining));
            } else {
                return String.format("%s: [%s%s%s] 100%% %s%s%s @ %s",
                                     task.title,

                                     Color.GREEN,
                                     Strings.times(spinner.done.toString(), pts_w),
                                     Color.CLEAR,

                                     new Color(Color.GREEN, Color.BOLD),
                                     spinner.complete,
                                     Color.CLEAR,
                                     format(Duration.of(task.updated_ts - task.started_ts, ChronoUnit.MILLIS)));
            }
        }
    }

    static class InternalTask<T> implements Future<T>, ProgressTask {
        final long                       total;
        final long                       created_ts;
        final Clock                      clock;
        final String                     title;
        final AtomicReference<Future<T>> future;
        final ProgressHandler<T>         handler;
        final AtomicBoolean              cancelled;
        final AtomicBoolean              failed;

        volatile int     spinner_pos;
        volatile long    spinner_update_ts;
        volatile long    started_ts;
        volatile long    updated_ts;
        volatile long    expected_done_ts;
        volatile double  fraction;

        /**
         * Create a progress updater. Note that <b>either</b> terminal or the
         * updater param must be set.
         *
         * @param clock The clock to use for timing.
         * @param title What progresses.
         * @param total The total value to be 'progressed'.
         */
        InternalTask(Clock clock,
                     String title,
                     long total,
                     ProgressHandler<T> handler) {
            this.title = title;
            this.total = total;
            this.clock = clock;
            this.handler = handler;

            this.future = new AtomicReference<>();

            this.spinner_pos = 0;
            this.spinner_update_ts = 0L;
            this.created_ts = clock.millis();
            this.started_ts = 0;
            this.fraction = 0.0;
            this.cancelled = new AtomicBoolean(false);
            this.failed = new AtomicBoolean(false);
        }

        void start(ExecutorService executor,
                   Runnable onFinished) {
            synchronized (this) {
                if (cancelled.get()) {
                    throw new IllegalStateException("Starting cancelled task");
                }
                if (started_ts > 0) {
                    throw new IllegalStateException("Already Started");
                }
                started_ts = clock.millis();
                spinner_update_ts = started_ts;
            }
            synchronized (future) {
                future.set(executor.submit(() -> {
                    try {
                        T result = handler.handle(InternalTask.this);
                        accept(total);
                        return result;
                    } catch (InterruptedException e) {
                        synchronized (this) {
                            stopInternal(true, false);
                        }
                        throw e;
                    } catch (Exception e) {
                        synchronized (this) {
                            stopInternal(false, true);
                        }
                        throw e;
                    } finally {
                        onFinished.run();
                    }
                }));
                future.notifyAll();
            }
        }

        @Override
        public boolean cancel(boolean interruptable) {
            synchronized (this) {
                if (isDone()) {
                    return false;
                }
                stopInternal(true, false);
            }

            boolean ret = false;
            synchronized (future) {
                Future<T> f = future.get();
                if (f != null) {
                    ret = f.cancel(interruptable);
                }
                future.notifyAll();
            }
            return ret;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            if (cancelled.get() || failed.get()) return true;
            Future<T> f = future.get();
            return f != null && f.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                synchronized (future) {
                    if (!cancelled.get() && future.get() == null) {
                        future.wait();
                    }
                }
                if (future.get() != null) {
                    return future.get().get();
                }
                throw new CancellationException();
            } catch (CancellationException e) {
                CancellationException ce = new CancellationException("Cancelled");
                ce.initCause(e);
                throw ce;
            }
        }

        @Override
        public T get(long l, @Nonnull TimeUnit timeUnit) throws
                                                         InterruptedException,
                                                         ExecutionException,
                                                         TimeoutException {
            try {
                long start    = clock.millis();
                long deadline = timeUnit.toMillis(l);
                synchronized (future) {
                    if (!cancelled.get() && future.get() == null) {
                        future.wait(deadline);
                    }
                }
                if (future.get() != null) {
                    long now = clock.millis();
                    deadline = deadline - (now - start);
                    return future.get().get(deadline, TimeUnit.MILLISECONDS);
                }
            } catch (CancellationException e) {
                CancellationException ce = new CancellationException("Cancelled");
                ce.initCause(e);
                throw ce;
            }
            throw new CancellationException("Cancelled");
        }

        /**
         * Update the progress to reflect the current progress value.
         *
         * @param current The new current progress value.
         */
        @Override
        public void accept(long current) {
            if (isDone()) return;

            long now = clock.millis();
            current = min(current, total);

            synchronized (this) {
                if (now >= (spinner_update_ts + 100)) {
                    spinner_pos = spinner_pos + 1;
                    spinner_update_ts = now;
                }

                if (current < total) {
                    fraction = ((double) current) / ((double) total);

                    long duration_ms = now - created_ts;
                    if (duration_ms > 3000) {
                        // Progress has actually gone forward, recalculate total time only if
                        // we have 3 second of progress.
                        if (expected_done_ts == 0L || updated_ts < (now - 2000L)) {
                            // Update total / expected time once per 2 seconds.
                            long assumed_total = (long) (((double) duration_ms) / fraction);
                            long remaining_ms  = max(0L, assumed_total - duration_ms);
                            expected_done_ts = now + remaining_ms;
                        }
                    }
                } else {
                    fraction = 1.0;
                    expected_done_ts = now;
                }
                updated_ts = now;
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                if (!isDone()) {
                    stopInternal(true, false);
                }
            }
        }

        private void stopInternal(boolean cancelled, boolean failed) {
            long now = clock.millis();
            this.cancelled.set(cancelled);
            this.failed.set(failed);
            this.updated_ts = now;
            this.spinner_update_ts = now;
            this.expected_done_ts = 0L;
        }
    }
}