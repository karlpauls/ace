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

package org.apache.ace.agent.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ServiceLoader;

import org.apache.ace.agent.AgentControl;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * A simple launcher, that launches the embedded Felix together with a management agent. Additional bundles may be
 * installed by putting {@link BundleProvider} services on the classpath.
 */
public class Launcher {

    public static void main(String[] args) throws Exception {

        Options options = new Options();
        options.addOption("a", "agent", true, "agentid (default handler)");
        options.addOption("s", "serverurl", true, "serverurl (default handler");
        options.addOption("v", "verbose", false, "verbose logging");
        options.addOption("h", "help", false, "print this message");

        CommandLineParser parser = new BasicParser();
        CommandLine command = parser.parse(options, args);

        if (command.hasOption("h")) {
            printHelp(options);
            return;
        }

        String[] arguments = command.getArgs();
        if (arguments.length > 1) {
            printHelp(options);
        }

        Map<String, String> configuration = new Hashtable<String, String>();

        // first map all default properties
        Properties defaultProperties = loadDefaultProperties();
        for (Object key : defaultProperties.keySet()) {
            configuration.put((String) key, defaultProperties.getProperty((String) key));
        }

        // overwrite with user properties
        if (arguments.length == 1) {
            Properties userProperties = loadUserProperties(arguments[0]);
            if (userProperties != null) {
                for (Object key : userProperties.keySet()) {
                    configuration.put((String) key, userProperties.getProperty((String) key));
                }
            }
        }

        if (command.hasOption("v")) {
            configuration.put("verbose", "true");
        }

        // // overwrite with user args
        // if (command.hasOption("a")) {
        // configuration.put(CONFIG_IDENTIFICATION_KEY, command.getOptionValue("a"));
        // }
        // if (command.hasOption("s")) {
        // configuration.put(CONFIG_SERVERURL_KEY, command.getOptionValue("s"));
        // }
        // if (command.hasOption("v")) {
        // configuration.put(CONFIG_LOGLEVEL_KEY, "DEBUG");
        // }
        //
        // // set defaults
        // if (!configuration.containsKey(CONFIG_LOGLEVEL_KEY)) {
        // configuration.put(CONFIG_LOGLEVEL_KEY, "INFO");
        // }
        //
        // // basic checks
        // if (!configuration.containsKey(CONFIG_IDENTIFICATION_KEY)) {
        // System.err.println("No agent specified");
        // System.exit(1);
        // }

        new Launcher(configuration).run();
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(120, "java -jar org.apache.ace.agent.launcher [options] [configurationfile]",
            "\nApache ACE AgentLauncher\n\n", options,
            "\n\nConfiguration file options\n\nTODO", false);
    }

    private static Properties loadDefaultProperties() throws IOException {
        Properties properties = new Properties();
        ClassLoader classloader = Launcher.class.getClassLoader();
        InputStream inStream = classloader.getResourceAsStream("org/apache/ace/agent/launcher/launcher-defaults.properties");
        try {

            properties.load(inStream);
            return properties;
        }
        finally {
            inStream.close();
        }
    }

    private static Properties loadUserProperties(String configFileArgument) throws IOException {
        File configFile = new File(configFileArgument);
        if (!configFile.exists() || !configFile.isFile() || !configFile.canRead()) {
            System.err.println("Can not acces configuration file : " + configFileArgument);
            return null;
        }
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(configFile));
        }
        catch (IOException e) {
            System.err.println("Can not load configuration file : " + configFileArgument);
            return null;
        }
        return properties;
    }

    private final Map<String, String> m_configuration;
    private final boolean m_verbose;

    public Launcher(Map<String, String> configuration) {
        m_configuration = configuration;
        m_verbose = (m_configuration.get("verbose") != null) && Boolean.parseBoolean(m_configuration.get("verbose"));
    }

    /**
     * Main execution logic of the launcher; Start a framework, install bundles and pass configuration to the
     * {@link AgentFactory}.
     * 
     * @throws Exception on failure
     */
    public void run() throws Exception {

        try {
            FrameworkFactory frameworkFactory = loadFrameworkFactory();
            Map<String, String> frameworkProperties = createFrameworkProperties();
            if (m_verbose)
                System.out.println("Launching OSGI framework\n factory\t: " + frameworkFactory.getClass().getName() + "\n properties\t: " + frameworkProperties);

            Framework framework = frameworkFactory.newFramework(frameworkProperties);
            BundleContext context = null;
            framework.init();
            context = framework.getBundleContext();

            BundleProvider[] bundleFactories = loadBundleProviders();
            if (bundleFactories != null) {
                for (BundleProvider bundleFactory : bundleFactories) {
                    if (m_verbose)
                        System.out.println(" bundle\t: " + bundleFactory.getLocation());
                    InputStream inputStream = bundleFactory.getInputStream();
                    try {
                        Bundle bundle = context.installBundle(bundleFactory.getLocation(), inputStream);
                        bundle.start();
                    }
                    finally {
                        inputStream.close();
                    }
                }
            }

            File[] bundleFiles = loadBundleFiles();
            if (bundleFiles != null) {
                for (File bundleFile : bundleFiles) {
                    if (m_verbose)
                        System.out.println(" bundle\t: " + bundleFile.getAbsolutePath());
                    InputStream inputStream = new FileInputStream(bundleFile);
                    try {
                        Bundle bundle = context.installBundle(bundleFile.getAbsolutePath(), inputStream);
                        bundle.start();
                    }
                    finally {
                        inputStream.close();
                    }
                }
            }

            framework.start();

            if (m_verbose)
                System.out.println("Startup complete..");
            framework.waitForStop(0);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /**
     * Load {@link FrameworkFactory} through the {@link ServiceLoader}.
     * 
     * @return the first factory
     * @throws Exception on failure
     */
    private FrameworkFactory loadFrameworkFactory() throws Exception {
        ServiceLoader<FrameworkFactory> frameworkFactoryLoader = ServiceLoader.load(FrameworkFactory.class);
        Iterator<FrameworkFactory> frameworkFactoryIterator = frameworkFactoryLoader.iterator();
        if (!frameworkFactoryIterator.hasNext()) {
            throw new IllegalStateException("Unable to load any FrameworkFactory");
        }
        return frameworkFactoryIterator.next();
    }

    /**
     * Load bundle file specified through configuration.
     * 
     * @return list of files
     * @throws Exception on failure
     */
    private File[] loadBundleFiles() throws Exception {
        List<File> bundleFiles = new ArrayList<File>();
        String bundleFileConfig = m_configuration.get("system.bundles");
        if (bundleFileConfig != null && !bundleFileConfig.trim().equals("")) {
            String[] bundleFileNames = bundleFileConfig.trim().split(",");
            int i = 0;
            for (String bundleFileName : bundleFileNames) {
                File bundleFile = new File(bundleFileName);
                if (!bundleFile.exists() || !bundleFile.isFile() || !bundleFile.canRead()) {
                    throw new IllegalArgumentException("Can not access configured file: " + bundleFile.getAbsolutePath());
                }
                bundleFiles.add(bundleFile);
            }
        }
        return bundleFiles.toArray(new File[bundleFiles.size()]);
    }

    /**
     * Load {@link BundleProvider}s through the {@link ServiceLoader}.
     * 
     * @return list of providers
     * @throws Exception on failure
     */
    private BundleProvider[] loadBundleProviders() throws Exception {
        ServiceLoader<BundleProvider> bundleFactoryLoader = ServiceLoader.load(BundleProvider.class);
        Iterator<BundleProvider> bundleFactoryIterator = bundleFactoryLoader.iterator();
        List<BundleProvider> bundelFactoryList = new ArrayList<BundleProvider>();
        while (bundleFactoryIterator.hasNext()) {
            bundelFactoryList.add(bundleFactoryIterator.next());
        }
        return bundelFactoryList.toArray(new BundleProvider[bundelFactoryList.size()]);
    }

    /**
     * Build the framework launch properties.
     * 
     * @return the launch properties
     * @throws Exception on failure
     */
    private Map<String, String> createFrameworkProperties() throws Exception {
        Map<String, String> frameworkProperties = new HashMap<String, String>();
        for (Entry<String, String> entry : m_configuration.entrySet()) {
            if (entry.getKey().startsWith("framework.")) {
                String frameworkKey = entry.getKey().replaceFirst("framework.", "");
                String frameworkValue = m_configuration.get(entry.getValue());
                frameworkProperties.put(frameworkKey, frameworkValue);
            }
        }
        getAgentApiPackageSpec();
        String extraPackage = frameworkProperties.get(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);
        if (extraPackage == null || extraPackage.equals("")) {
            frameworkProperties.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, getAgentApiPackageSpec());
        }
        else {
            frameworkProperties.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, extraPackage + "," + getAgentApiPackageSpec());

        }
        return frameworkProperties;
    }

    /**
     * Determines the export clause for the agent API package.
     * 
     * @return the export clause
     * @throws Exception on failure
     */
    private String getAgentApiPackageSpec() throws IOException {

        String apiPackage = AgentControl.class.getPackage().getName();
        String apiVersion = Version.emptyVersion.toString();
        InputStream packageInfoStream = null;
        BufferedReader packageInfoReader = null;
        try {
            packageInfoStream = getClass().getClassLoader().getResourceAsStream(apiPackage.replaceAll("\\.", "/") + "/packageinfo");
            packageInfoReader = new BufferedReader(new InputStreamReader(packageInfoStream));
            String packageInfoLine = null;
            while ((packageInfoLine = packageInfoReader.readLine()) != null) {
                if (packageInfoLine.trim().startsWith("version ")) {
                    apiVersion = packageInfoLine.trim().replaceFirst("version ", "");
                    break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            if (packageInfoReader != null) {
                packageInfoReader.close();
            }
            else {
                if (packageInfoStream != null) {
                    packageInfoStream.close();
                }
            }
        }
        return apiPackage + ";version=" + apiVersion;
    }
}