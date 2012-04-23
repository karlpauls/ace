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

package org.apache.ace.connectionfactory.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;

import org.apache.ace.connectionfactory.impl.UrlCredentials.AuthType;

/**
 * Provides a helper class for obtaining access credentials from a dictionary.
 */
final class UrlCredentialsFactory {

    public static final class MissingValueException extends RuntimeException {
        private final String m_property;

        /**
         * @param property the name of the missing property;
         * @param message the message explaining the missing property. 
         */
        public MissingValueException(String property) {
            this(property, "No value for " + property + " given!");
        }

        /**
         * @param property the name of the missing property;
         * @param message the message explaining the missing property. 
         */
        public MissingValueException(String property, String message) {
            super(message);
            m_property = property;
        }
        
        /**
         * Returns the name of the missing property.
         * @return the property name, never <code>null</code>.
         */
        public String getProperty() {
            return m_property;
        }
    }

    public static final String KEY_AUTH_BASE_URL = "authentication.baseURL";
    public static final String KEY_AUTH_TYPE = "authentication.type";
    public static final String KEY_AUTH_USER_NAME = "authentication.user.name";
    public static final String KEY_AUTH_USER_PASSWORD = "authentication.user.password";
    
    /**
     * Not used.
     */
    private UrlCredentialsFactory() {
        // Nop
    }

    /**
     * @param props the properties to take the access credentials from.
     * @throws MissingValueException in case the given properties is missing values.
     */
    public static UrlCredentials getCredentials(Dictionary props) throws MissingValueException {
        return getCredentials(props, "");
    }
    
    /**
     * @param props the properties to take the access credentials from;
     * @param prefix the prefix to use to lookup the correct values in the given dictionary.
     * @throws MissingValueException in case the given properties is missing values.
     */
    public static UrlCredentials getCredentials(Dictionary props, String prefix) throws MissingValueException {
        if (props == null) {
            throw new IllegalArgumentException("Properties cannot be null!");
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null!");
        }

        AuthType type;
        URL baseURL; 
        Object[] creds;
        
        String baseUrlValue = getStringProperty(props, prefix.concat(KEY_AUTH_BASE_URL));
        if (baseUrlValue == null) {
            throw new MissingValueException(KEY_AUTH_BASE_URL);
        }
        
        try {
            baseURL = new URL(baseUrlValue);
        }
        catch (MalformedURLException e) {
            throw new MissingValueException(KEY_AUTH_BASE_URL, "Invalid base URL!");
        }

        String authType = getStringProperty(props, prefix.concat(KEY_AUTH_TYPE), "none");
        try {
            type = AuthType.valueOf(authType.toUpperCase());
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Unsupported authentication type: " + authType);
        }
        
        if (AuthType.NONE.equals(type)) {
            creds = new Object[0];
        } else if (AuthType.BASIC.equals(type)) {
            String userName = getStringProperty(props, prefix.concat(KEY_AUTH_USER_NAME));
            if (userName == null) {
                throw new MissingValueException(prefix.concat(KEY_AUTH_USER_NAME));
            }

            String password = getStringProperty(props, prefix.concat(KEY_AUTH_USER_PASSWORD));
            if (password == null) {
                throw new MissingValueException(prefix.concat(KEY_AUTH_USER_PASSWORD));
            }

            creds = new Object[] { userName, password };
        } else {
            throw new IllegalArgumentException("Invalid/unhandled authentication type: " + authType);
        }

        return new UrlCredentials(type, baseURL, creds);
    }

    private static String getStringProperty(Dictionary dict, String key) {
        Object value = dict.get(key);
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        return null;
    }
    
    private static String getStringProperty(Dictionary dict, String key, String defaultValue) {
        String value = getStringProperty(dict, key);
        return (value == null) ? defaultValue : value;
    }
}
