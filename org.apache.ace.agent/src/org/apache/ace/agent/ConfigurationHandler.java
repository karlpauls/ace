/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.agent;

import java.util.Map;
import java.util.Set;

/**
 * Agent context delegate interface that is responsible for managing persisted configuration. External launchers may
 * override or set values within the {@link AgentConstants.CONFIG_KEY_NAMESPACE} through system properties when the
 * agent starts. If the launcher wants to retain existing persisted values, instead of overwriting them, it should
 * specify an additional property with the same name post-fixed with {@link AgentConstants.CONFIG_KEY_RETAIN} set to
 * <code>true</code>. <br/>
 * <br/>
 * Example: A launcher that wants to ensure the syncinterval is set to 3000 only when not configuration is already set
 * should specify the following two system properties:<br/>
 * <code>agent.controller.syncinterval=3000</code><br/>
 * <code>agent.controller.syncinterval.retain=true</code>
 */
public interface ConfigurationHandler {

    /**
     * Return an unmodifiable copy of the configuration keys.
     * 
     * @return The set of keys
     */
    Set<String> keySet();

    /**
     * Retrieve the configuration value associated with the key, or the specified default.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param defaultValue
     *            The default value, must not be <code>null</code>
     * @return The associated value if it exists, otherwise the default value
     */
    String get(String key, String defaultValue);

    /**
     * Store a single configuration value.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param value
     *            The value, must not be <code>null</code>
     * @deprecated use {@link #putAll(Map)} instead which allows the configuration to be updated without partial
     *             visibility issues.
     */
    @Deprecated
    void put(String key, String value);

    /**
     * Store a configuration value.
     * 
     * @param props
     *            the properties to put, cannot be <code>null</code>.
     */
    void putAll(Map<String, String> props);

    /**
     * Remove a configuration value.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @deprecated use {@link #putAll(Map)} instead which allows the configuration to be updated without partial
     *             visibility issues.
     */
    @Deprecated
    void remove(String key);

    /**
     * Retrieve the configuration value associated with the key, or the specified default.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param defaultValue
     *            The default value
     * @return The associated value if it exists, otherwise the default value
     */
    long getLong(String key, long defaultValue);

    /**
     * Store a configuration value.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param value
     *            The value
     * @deprecated use {@link #putAll(Map)} instead which allows the configuration to be updated without partial
     *             visibility issues.
     */
    @Deprecated
    void putLong(String key, long value);

    /**
     * Retrieve the configuration value associated with the key, or the specified default.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param defaultValue
     *            The default value
     * @return The associated value if it exists, otherwise the default value
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Store a configuration value.
     * 
     * @param key
     *            The key, must not be <code>null</code>
     * @param value
     *            The value
     * @deprecated use {@link #putAll(Map)} instead which allows the configuration to be updated without partial
     *             visibility issues.
     */
    @Deprecated
    void putBoolean(String key, boolean Value);
}
