/*
 * Copyright (c) 2016, Stein Eldar Johnsen
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
package net.morimekta.console.args;

import net.morimekta.console.util.STTY;

import java.util.Comparator;

/**
 * Options for configuring the argument parser.
 */
public class ArgumentOptions {
    private final STTY tty;

    private boolean defaultsShown = true;
    private boolean subCommandsShown = false;
    private String subCommandsString = "Available Commands:";
    private int usageWidth = 80;
    private Comparator<BaseOption> optionComparator = null;

    public ArgumentOptions(STTY tty) {
        this.tty = tty;
    }

    public static ArgumentOptions defaults() {
        return new ArgumentOptions(new STTY());
    }

    public static ArgumentOptions defaults(STTY tty) {
        return new ArgumentOptions(tty);
    }

    /**
     * Set whether the default values should be printed in usage.
     * @param show If the default values should be printed.
     * @return The Argument options.
     */
    public ArgumentOptions withDefaultsShown(boolean show) {
        this.defaultsShown = show;
        return this;
    }

    /**
     * @return True if the default values should be printed in usage.
     */
    public boolean isDefaultsShown() {
        return defaultsShown;
    }

    /**
     * Set the number of columns to be used for usage printing.
     * @param usageWidth The number of columns.
     * @return The Argument options.
     */
    public ArgumentOptions withUsageWidth(int usageWidth) {
        this.usageWidth = usageWidth;
        return this;
    }

    /**
     * Set the maximum usage width. The width is set as wide as possible
     * based on the terminal column count, but maximum the maxWidth.
     *
     * @param maxWidth The maximum width.
     * @return The Argument options.
     */
    public ArgumentOptions withMaxUsageWidth(int maxWidth) {
        if (tty.isInteractive()) {
            this.usageWidth = Math.min(maxWidth, tty.getTerminalSize().cols);
        } else {
            this.usageWidth = maxWidth;
        }
        return this;
    }

    /**
     * @return Number of columns to be used for usage printing.
     */
    public int getUsageWidth() {
        return usageWidth;
    }

    public ArgumentOptions withOptionComparator(Comparator<BaseOption> comparator) {
        this.optionComparator = comparator;
        return this;
    }

    public Comparator<BaseOption> getOptionComparator() {
        return optionComparator;
    }

    public boolean isSubCommandsShown() {
        return subCommandsShown;
    }

    public ArgumentOptions withSubCommandsShown(boolean subCommandsShown) {
        this.subCommandsShown = subCommandsShown;
        return this;
    }

    public String getSubCommandsString() {
        return subCommandsString;
    }

    public ArgumentOptions withSubCommandsString(String subCommandsString) {
        this.subCommandsString = subCommandsString;
        return this;
    }
}
