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
package org.apache.ace.client.repository.helper.configuration.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.configuration.ConfigurationHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Activator class for the Configuration ArtifactHelper.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary<String, String> props = new Hashtable<>();
        props.put(ArtifactObject.KEY_MIMETYPE, ConfigurationHelper.MIMETYPE);

        ConfigurationHelperImpl helperImpl = new ConfigurationHelperImpl();
        manager.add(createComponent()
            .setInterface(new String[] { ArtifactHelper.class.getName(), ArtifactRecognizer.class.getName() }, props)
            .setImplementation(helperImpl)
            .add(createServiceDependency()
                .setService(ConnectionFactory.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false))
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do
    }
}
