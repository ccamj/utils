package net.morimekta.console.terminal;

import com.google.common.annotations.VisibleForTesting;
import net.morimekta.console.chr.Char;
import net.morimekta.console.chr.Color;
import net.morimekta.console.chr.Control;
import net.morimekta.console.chr.Unicode;
import net.morimekta.util.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.IntSupplier;

import static java.lang.Math.max;

/**
 * Show progress on a single task in how many percent (with spinner and
 * progress-bar). Spinner type is configurable. This is the single-thread
 * progress where everything is handled in the same thread as calls
 * {@link #accept(long)}. This class is <i>not</i> thread safe.
 */
public class Progress implements ProgressTask {
    /**
     * Which spinner to show. Some may require extended unicode font to
     * be used in the console without just showing '?'.
     */
    public enum Spinner {
        /**
         * Simple ASCII spinner using '|', '/', '-', '\'. This variant will
         * work in any terminal.
         */
        ASCII(new Unicode('#'),
              new Unicode('-'),
              new Unicode('v'),
              new Unicode[] {
                      new Unicode('|'),
                      new Unicode('/'),
                      new Unicode('-'),
                      new Unicode('\\')
              }),

        /**
         * Using a block char that bounces up and down to show progress.
         * Not exactly <i>spinning</i>, but does the job. Using unicode
         * chars 0x2581 -&gt; 0x2588;
         * <p>
         * '▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'
         */
        BLOCKS(new Unicode('▓'),
               new Unicode('⋅'),
               new Unicode('✓'),
               new Unicode[] {
                       new Unicode('▁'),  // 1/8 block
                       new Unicode('▂'),  // 2/8 block
                       new Unicode('▃'),  // ...
                       new Unicode('▄'),  //
                       new Unicode('▅'),  //
                       new Unicode('▆'),  // ...
                       new Unicode('▇'),  // 7/8 block
                       new Unicode('█'),  // 8/8 (full) block
                       new Unicode('▇'),  // 7/8 block
                       new Unicode('▆'),  // ...
                       new Unicode('▅'),  //
                       new Unicode('▄'),  //
                       new Unicode('▂'),  // ...
                       new Unicode('▁'),  // 2/8 block
               }),

        /**
         * A spinning arrow. Using chars in range 0x2b60 -&gt; 0x2b69:
         * <p>
         * '⭢', '⭨', '⭣', '⭩', '⭠', '⭦', '⭡', '⭧'
         */
        ARROWS(new Unicode('⬛'),
               new Unicode('⋅'),
               new Unicode('✓'),
               new Unicode[] {
                       new Unicode('⭢'),
                       new Unicode('⭨'),
                       new Unicode('⭣'),
                       new Unicode('⭩'),
                       new Unicode('⭠'),
                       new Unicode('⭦'),
                       new Unicode('⭡'),
                       new Unicode('⭧'),
               }),

        /**
         * Use Unicode clock symbols, 0x1f550 -&gt; 0x1f55b:
         * <p>
         * '🕐', '🕑', '🕒', '🕓', '🕔', '🕕', '🕖', '🕗', '🕘', '🕙', '🕚', '🕛'
         */
        CLOCK(new Unicode('⬛'),
              new Unicode('⋅'),
              new Unicode('✓'),
              new Unicode[] {
                      new Unicode(0x1f550),  // 1 o'clock
                      new Unicode(0x1f551),  // ...
                      new Unicode(0x1f552),
                      new Unicode(0x1f553),
                      new Unicode(0x1f554),
                      new Unicode(0x1f555),
                      new Unicode(0x1f556),
                      new Unicode(0x1f557),
                      new Unicode(0x1f558),
                      new Unicode(0x1f559),
                      new Unicode(0x1f55a),  // ...
                      new Unicode(0x1f55b)   // 12 o'clock
              }),

        ;

        Char   done;
        Char   remain;
        Char   complete;
        Char[] spinner;

        Spinner(Char done,
                Char remain,
                Char complete,
                Char[] spinner) {
            this.done = done;
            this.spinner = spinner;
            this.remain = remain;
            this.complete = complete;
        }
    }

    private final Terminal    terminal;
    private final Spinner     spinner;
    private final long        total;
    private final long        start;
    private final Clock       clock;
    private final String      title;
    private final LinePrinter updater;
    private final IntSupplier terminalWidthSupplier;

    private int spinner_pos;
    private double fraction;
    private int last_pct;
    private int last_pts;
    private long last_update;
    private long spinner_update;
    private long expected_done_ts;

    /**
     * Create a progress bar using the given terminal.
     *
     * @param terminal The terminal to use.
     * @param spinner The spinner to use.
     * @param title The title of the progress.
     * @param total The total progress value.
     */
    public Progress(@Nonnull Terminal terminal,
                    @Nonnull Spinner spinner,
                    @Nonnull String title,
                    long total) {
        this(terminal,
             null,
             () -> terminal.getTTY().getTerminalSize().cols,
             Clock.systemUTC(),
             spinner,
             title,
             total);
    }

    /**
     * Create a progress bar using the line printer and width supplier.
     *
     * @param updater The line printer used to update visible progress.
     * @param widthSupplier The width supplier to get terminal width from.
     * @param spinner The spinner to use.
     * @param title The title of the progress.
     * @param total The total progress value.
     */
    public Progress(@Nonnull LinePrinter updater,
                    @Nonnull IntSupplier widthSupplier,
                    @Nonnull Spinner spinner,
                    @Nonnull String title,
                    long total) {
        this(null, updater, widthSupplier, Clock.systemUTC(), spinner, title, total);
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
        if (current > total) current = total;
        int pts_w = terminalWidthSupplier.getAsInt() - 23 - title.length();

        fraction = ((double) current) / ((double) total);
        int pct = (int) (fraction * 100);
        int pts = (int) (fraction * pts_w);

        if (current < total) {
            if (now - last_update < 73 && pct == last_pct && pts == last_pts) {
                return;
            }

            int remaining_pts = pts_w - pts;

            long     duration_ms = now - start;
            Duration remaining   = null;
            // Progress has actually gone forward, recalculate total time.
            if (duration_ms > 3000) {
                long remaining_ms;
                if (expected_done_ts == 0L || pct > last_pct) {
                    long assumed_total = (long) (((double) duration_ms) / fraction);
                    remaining_ms = max(0L, assumed_total - duration_ms);
                    expected_done_ts = now + remaining_ms;
                } else {
                    remaining_ms = max(0L, expected_done_ts - now);
                }
                remaining = Duration.of(remaining_ms, ChronoUnit.MILLIS);
            }

            if (now >= (spinner_update + 100)) {
                spinner_pos = (spinner_pos + 1) % spinner.spinner.length;
                spinner_update = now;
            }

            if (pts < pts_w) {
                updater.formatln("%s: [%s%s%s%s%s] %3d%% %s%s%s%s",
                                 title,

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
                updater.formatln("%s: [%s%s%s] 100%% %s%s%s%s",
                                 title,

                                 Color.GREEN,
                                 Strings.times(spinner.done.toString(), pts),
                                 Color.CLEAR,

                                 new Color(Color.YELLOW, Color.BOLD),
                                 spinner.spinner[spinner_pos],
                                 Color.CLEAR,
                                 remaining == null ? "" : " + " + format(remaining));
            }
            last_pct = pct;
            last_pts = pts;
            last_update = now;
        } else {
            updater.formatln("%s: [%s%s%s] 100%% %s%s%s @ %s",
                             title,

                             Color.GREEN,
                             Strings.times(spinner.done.toString(), pts_w),
                             Color.CLEAR,

                             new Color(Color.GREEN, Color.BOLD),
                             spinner.complete,
                             Color.CLEAR,
                             format(Duration.of(now - start, ChronoUnit.MILLIS)));
            last_update = Long.MAX_VALUE;
            last_pct = 100;
        }
    }

    @Override
    public void close() {
        long now = clock.millis();
        if (now >= last_update) {
            int pts_w = terminalWidthSupplier.getAsInt() - 23 - title.length();
            int pts = max(0, (int) (fraction * pts_w));
            int remaining_pts = max(0, pts_w - pts);

            updater.formatln("%s: [%s%s%s%s%s] %3d%% %sAborted%s",
                             title,

                             Color.GREEN,
                             Strings.times(spinner.done.toString(), pts),
                             Color.YELLOW,
                             Strings.times(spinner.remain.toString(), remaining_pts),
                             Color.CLEAR,

                             last_pct,

                             new Color(Color.RED, Color.BOLD),
                             Color.CLEAR);

            last_update = Long.MAX_VALUE;
        }
    }

    @Override
    public boolean isDone() {
        return last_update > clock.millis();
    }

    /**
     * Create a progress updater. Note that <b>either</b> terminal or the
     * updater param must be set.
     *
     * @param terminal The terminal to print to.
     * @param updater The updater to write to.
     * @param widthSupplier The width supplier to get terminal width from.
     * @param clock The clock to use for timing.
     * @param spinner The spinner type.
     * @param title What progresses.
     * @param total The total value to be 'progressed'.
     */
    @VisibleForTesting
    protected Progress(@Nullable Terminal terminal,
                       @Nullable LinePrinter updater,
                       @Nonnull IntSupplier widthSupplier,
                       @Nonnull Clock clock,
                       @Nonnull Spinner spinner,
                       @Nonnull String title,
                       long total) {
        this.terminal = terminal;
        this.terminalWidthSupplier = widthSupplier;
        this.updater = updater != null ? updater : this::println;
        this.spinner = spinner;
        this.title = title;
        this.total = total;
        this.start = clock.millis();
        this.clock = clock;
        this.last_pct = -1;
        this.last_pts = -1;
        this.spinner_pos = 0;
        this.spinner_update = start;

        if (terminal != null) {
            terminal.finish();
        }
        accept(0);
    }

    private void println(String line) {
        terminal.print("\r" + Control.CURSOR_ERASE + line);
    }

    static String format(Duration duration) {
        long h = duration.toHours();
        long m = duration.minusHours(h).toMinutes();
        if (h > 0) {
            return String.format("%2d:%02d H", h, m);
        }
        long s = duration.minusHours(h).minusMinutes(m).getSeconds();
        if (m > 0) {
            return String.format("%2d:%02d min", m, s);
        }
        long ms = duration.minusHours(h).minusMinutes(m).minusSeconds(s).toMillis();
            return String.format("%2d.%1d  s", s, ms / 100);
    }
}
