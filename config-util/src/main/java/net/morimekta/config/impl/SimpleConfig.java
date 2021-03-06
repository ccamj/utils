/*
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
package net.morimekta.config.impl;

import net.morimekta.config.Config;
import net.morimekta.config.ConfigBuilder;
import net.morimekta.config.util.ConfigUtil;

import java.util.TreeMap;

/**
 * Mutable configuration object backed by a tree map.
 */
public class SimpleConfig extends TreeMap<String,Object> implements ConfigBuilder<SimpleConfig> {
    /**
     * Create an empty config instance.
     */
    public SimpleConfig() {}

    /**
     * Create an empty config instance with parent and base.
     *
     * @param base The base config (or super-config).
     */
    public SimpleConfig(Config base) {
        for (String key : base.keySet()) {
            put(key, base.get(key));
        }
    }

    @Override
    public Object get(String key) {
        return super.get(key);
    }

    @Override
    public Object remove(String key) {
        return remove((Object) key);
    }

    @Override
    public boolean containsKey(String key) {
        return super.containsKey(key);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof Config)) {
            return false;
        }
        return ConfigUtil.equals(this, (Config) o);
    }

    @Override
    public int hashCode() {
        return ConfigUtil.hashCode(this);
    }

    @Override
    public String toString() {
        return ConfigUtil.toString(this);
    }
}
