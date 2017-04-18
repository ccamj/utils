/*
 * Copyright (c) 2016, 2017, Stein Eldar Johnsen
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package net.morimekta.util.concurrent;

import net.morimekta.util.Strings;
import net.morimekta.util.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Helper to handle a process with possible input and getting output.
 *
 * <pre>
 * ProcessExecutor clone = ProcessExecutor("git", "clone", repo);
 * Future&lt;Integer&gt; i = executorService.submit(clone);
 *
 * // do something else.
 *
 * int exitCode = i.get();
 * </pre>
 *
 * The two output strings will be available as the program runs, so
 * calling <code>ex.getOutput()</code> or <code>ex.getError()</code>
 * while the process executor runs is entirely safe. You will just
 * get the output snapshot at that time. <i><b>NOTE: </b>It is not
 * possible to get a simultaneous snapshot of stdout and stderr.</i>
 *
 * Also note that all program output is cached in byte arrays, so running
 * programs with exceedingly large outputs can cause OOM errors.
 */
public class ProcessExecutor implements Callable<Integer> {
    private final String[]        cmd;
    private final Runtime         runtime;
    private final ExecutorService executor;
    private final ByteArrayOutputStream out;
    private final ByteArrayOutputStream err;

    private IOException           inException;
    private IOException           outException;
    private IOException           errException;

    private InputStream           in;
    private long                  deadlineMs;

    public ProcessExecutor(String... cmd) throws IOException {
        this(cmd,
             Runtime.getRuntime(),
             Executors.newFixedThreadPool(3));
    }

    protected ProcessExecutor(String[] cmd, Runtime runtime, ExecutorService executor) {
        this.cmd = cmd;
        this.runtime = runtime;
        this.executor = executor;

        this.out = new ByteArrayOutputStream();
        this.err = new ByteArrayOutputStream();
        this.in = null;
        this.deadlineMs = TimeUnit.SECONDS.toMillis(1);
    }

    /**
     * @return The programs stdout content.
     */
    public String getOutput() {
        synchronized (out) {
            return new String(out.toByteArray(), UTF_8);
        }
    }

    /**
     * @return The programs stderr content.
     */
    public String getError() {
        synchronized (err) {
            return new String(err.toByteArray(), UTF_8);
        }
    }

    /**
     * Set input stream to write to the process as program input.
     *
     * @param in The program input.
     */
    public void setInput(InputStream in) {
        this.in = in;
    }

    /**
     * Set the program deadline. If not finished in this time interval,
     * the run fails with an IOException.
     *
     * @param deadlineMs The new deadline in milliseconds. 0 means to wait
     *                   forever. Default is 1 second.
     */
    public void setDeadlineMs(long deadlineMs) {
        this.deadlineMs = deadlineMs;
    }

    @Override
    public Integer call() {
        try {
            out.reset();
            err.reset();

            inException = null;
            outException = null;
            errException = null;

            Process process = runtime.exec(cmd);

            // T avoid the process running out of IO buffer, we need to handle
            // the read and writing of both std-in and std-out/-err in separate
            // threads, while we at the same time we wait (to handle the
            // execution deadline).
            executor.submit(() -> {
                try {
                    byte[] buffer = new byte[4 * 1024];
                    int b;
                    while ((b = process.getInputStream().read(buffer)) > 0) {
                        synchronized (out) {
                            out.write(buffer, 0, b);
                        }
                    }
                } catch (IOException e) {
                    outException = e;
                }
            });
            executor.submit(() -> {
                try {
                    byte[] buffer = new byte[4 * 1024];
                    int b;
                    while ((b = process.getErrorStream().read(buffer)) > 0) {
                        synchronized (err) {
                            err.write(buffer, 0, b);
                        }
                    }
                } catch (IOException e) {
                    errException = e;
                }
            });

            if (in != null) {
                executor.submit(() -> {
                    try {
                        IOUtils.copy(in, process.getOutputStream());
                        // Do not allow the std-in to have lingering bytes.
                        process.getOutputStream().flush();
                    } catch (IOException e) {
                        inException = e;
                    } finally {
                        try {
                            process.getOutputStream().close();
                        } catch (IOException e) {
                            if (inException != null) {
                                inException.addSuppressed(e);
                            }
                        }
                        in = null;
                    }
                });
            } else {
                // Always close the program's input stream to force it to stop reading.
                // NOTE: This is not identical to how interactive apps work, but avoids
                // the test to halt because of problems reading from std input stream.
                //
                // TODO(morimekta): Figure out the correct behavior + deadline.
                process.getOutputStream().close();
            }

            if (deadlineMs > 0) {
                if (!process.waitFor(deadlineMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();

                    StringBuilder bld   = new StringBuilder();
                    boolean       first = true;
                    for (String c : cmd) {
                        if (first) {
                            first = false;
                        } else {
                            bld.append(" ");
                        }

                        String esc = Strings.escape(c);

                        // Quote where escaping is needed, OR the argument
                        // contains a literal space.
                        if (!c.equals(esc) || c.contains(" ")) {
                            bld.append('\"')
                               .append(esc)
                               .append('\"');
                        } else {
                            bld.append(c);
                        }
                    }

                    throw new IOException("Process took too long: " + bld.toString());
                }
            } else {
                process.waitFor();
            }

            if (inException != null) {
                throw new IOException(inException.getMessage(), inException);
            } else if (outException != null) {
                throw new IOException(outException.getMessage(), outException);
            } else if (errException != null) {
                throw new IOException(errException.getMessage(), errException);
            }

            return process.exitValue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
