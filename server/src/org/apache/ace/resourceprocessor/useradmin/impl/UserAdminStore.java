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
package org.apache.ace.resourceprocessor.useradmin.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ace.resourceprocessor.useradmin.UserAdminConfigurator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class UserAdminStore extends ResourceStore implements UserAdminConfigurator {

    private UserAdmin m_userAdmin;
    private Object m_userAdminLock = new Object();
    private volatile LogService m_log;

    private List<ProcessRole> m_toInstall = new ArrayList<ProcessRole>();
    private List<ProcessRole> m_toRemove = new ArrayList<ProcessRole>();
    private Object m_installListLock = new Object();
    private boolean m_clear;

    UserAdminStore(BundleContext context) {
        super(context);
    }

    @Override
    public void install(String resourceName) throws IOException {
        InputStream input = getResource(resourceName);
        install(input);
    }

    public void install(Document doc) {
        installRoles(doc);
        updateUserAdmin();
    }

    public void install(InputStream input) throws IOException {
        install(getDocument(input));
    }

    @Override
    public void uninstall(String resourceName) throws IOException {
        InputStream input = getResource(resourceName);
        uninstall(getDocument(input));
    }

    public void uninstall(Document doc) {
        removeRoles(doc);
        updateUserAdmin();
    }

    public void uninstall(InputStream input) throws IOException {
        uninstall(getDocument(input));
    }

    public void setUsers(Document doc) {
        m_toInstall.clear();
        m_toRemove.clear();
        installRoles(doc);
        m_clear = true;
        updateUserAdmin();
    }

    public void setUsers(InputStream input) throws IOException {
        setUsers(getDocument(input));
    }

    @Override
    public void validate(InputStream resource) throws Exception {
        Document doc = getDocument(resource);
        getRoles(doc);
    }

    /**
     * Installs the users and groups found in a document.
     */
    private void installRoles(Document doc) {
        synchronized (m_installListLock) {
            m_toInstall.addAll(getRoles(doc));
        }
    }

    /**
     * Removes the users and groups found in a document.
     */
    private void removeRoles(Document doc) {
        synchronized (m_installListLock) {
            // do this backwards from the order in the file
            List<ProcessRole> roles = getRoles(doc);
            ListIterator<ProcessRole> i = roles.listIterator(roles.size());
            for (ProcessRole role = i.previous(); i.hasPrevious(); i.previous()) {
                m_toRemove.add(role);
            }
        }
    }

    /**
     * Updates the currently present UserAdmin with the data in m_toInstall and m_toRemove.
     */
    @SuppressWarnings("unchecked")
    private void updateUserAdmin() {
        synchronized(m_installListLock) {
            synchronized (m_userAdminLock) {
                if (m_userAdmin == null) {
                    return;
                }

                List<String> updated = new ArrayList<String>();

                // install or update all roles we have to update
                while (!m_toInstall.isEmpty()) {
                    ProcessRole role = m_toInstall.remove(0);
                    updateRole(role);
                    updated.add(role.getName());
                }

                // remove all roles that have not been updated if this install
                // is a full install
                if (m_clear) {
                    Role[] roles = null;
                    try {
                        roles = m_userAdmin.getRoles(null);
                    }
                    catch (InvalidSyntaxException e) {
                        // Will not happen, since we pass in a null filter.
                    }
                    for (Role r : roles) {
                        if (!updated.contains(r.getName())) {
                            m_userAdmin.removeRole(r.getName());
                        }
                    }
                }

                // if this is not a full install, remove any roles that should be
                // removed
                if (!m_clear) {
                    while (!m_toRemove.isEmpty()) {
                        ProcessRole role = m_toRemove.remove(m_toRemove.size());
                        m_userAdmin.removeRole(role.getName());
                    }
                }

                m_clear = false;
            }
        }
    }

    /**
     * Updates a role with new parameter, but reuses the UserAdmin's role object
     * for this (if available).
     */
    @SuppressWarnings("unchecked")
    private void updateRole(ProcessRole role) {
        Role r = m_userAdmin.getRole(role.getName());
        if (r == null) {
            r = m_userAdmin.createRole(role.getName(), role.getType());
        }
        clearDictionary(r.getProperties());
        for (Entry<String, Object> entry : role.getProperties().entrySet()) {
            r.getProperties().put(entry.getKey(), entry.getValue());
        }
        clearDictionary(((User) r).getCredentials());
        if (role.getType() == Role.USER) {
            for (Entry<String, Object> entry : role.getCredentials().entrySet()) {
                ((User) r).getCredentials().put(entry.getKey(), entry.getValue());
            }
        }
        for (Group g : memberOf(r)) {
            g.removeMember(r);
        }
        for (String groupName : role.getMemberOf()) {
            Group g = (Group) m_userAdmin.getRole(groupName);
            if (g == null) {
                m_log.log(LogService.LOG_WARNING, "Cannot add user " + role.getName() + " to group " + groupName + ", because the group does not exist.");
                continue;
            }
            g.addMember(r);
        }
    }

    private void clearDictionary(Dictionary dict) {
        Enumeration i = dict.keys();
        while (i.hasMoreElements()) {
            dict.remove(i.nextElement());
        }
    }

    /**
     * Helper that finds all groups this role is a member of.
     */
    private Group[] memberOf(Role r) {
        List<Group> result = new ArrayList<Group>();
        Role[] roles = null;
        try {
            roles = m_userAdmin.getRoles(null);
        }
        catch (InvalidSyntaxException e) {
            // Will not happen, since we pass in a null filter.
        }
        if (roles == null) {
            return new Group[0];
        }
        for (Role group : roles) {
            if (group instanceof Group) {
                Role[] members = ((Group) group).getMembers();
                if (members != null) {
                    if (contains(r, members)) {
                        result.add((Group) group);
                    }
                }
            }
        }

        return result.toArray(new Group[result.size()]);
    }

    /**
     * Helper method that checks the presence of an object in an array. Returns
     * <code>true</code> if <code>t</code> is in <code>ts</code>, <code>false</code> otherwise.
     */
    private <T> boolean contains(T t, T[] ts) {
        for (T current : ts) {
            if (current.equals(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by the dependency manager when a user admin becomes available.
     */
    public void userAdminAdded(UserAdmin admin) {
        synchronized (m_userAdminLock) {
            if (m_userAdmin != null) {
                throw new IllegalStateException("UserAdminStore is intended to work with a single user admin.");
            }
            m_userAdmin = admin;
            updateUserAdmin();
        }
    }

    /**
     * Called by the dependency manager when a user admin goes away.
     */
    public void userAdminRemoved(UserAdmin admin) {
        synchronized (m_userAdminLock) {
            if (m_userAdmin != admin) {
                throw new IllegalStateException("UserAdminStore is intended to work with a single user admin.");
            }
            m_userAdmin = null;
        }
    }

    /**
     * Gets the DOM document contained in a stream.
     */
    private Document getDocument(InputStream input) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            return builder.parse(input);
        }
        catch (ParserConfigurationException e) {
            throw new IOException("Error instantiation XML parser:" + e.getMessage());
        }
        catch (SAXException e) {
            throw new IOException("Error parsing user data:" + e.getMessage());
        }
    }

    /**
     * Gets all roles that are present in a document.
     * @param doc The document to use.
     * @return A list of ProcessRoles.
     */
    private List<ProcessRole> getRoles(Document doc) {
        List<ProcessRole> result = new ArrayList<ProcessRole>();
        for (Node node = doc.getFirstChild().getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!node.getNodeName().equals("#text")) {
                result.add(getRole(node));
            }
        }
        return result;
    }

    /**
     * Helper method that takes a single XML node containing a 'user' or 'group', and return a
     * ProcessRole for it.
     */
    private ProcessRole getRole(Node node) {
        ProcessRole result = new ProcessRole(node.getAttributes().getNamedItem("name").getTextContent(), (node.getNodeName().equals("group") ? Role.GROUP : Role.USER));

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals("properties")) {
                for (Node property = child.getFirstChild(); property != null; property = property.getNextSibling()) {
                    if (!property.getNodeName().equals("#text")) {
                        String type = null;
                        Node typeNode = property.getAttributes().getNamedItem("type");
                        if (typeNode != null) {
                            type = typeNode.getTextContent();
                        }
                        result.getProperties().put(property.getNodeName(), "byte[]".equals(type) ? property.getTextContent().getBytes() : property.getTextContent());
                    }
                }
            }
            else if (child.getNodeName().equals("credentials")) {
                for (Node credential = child.getFirstChild(); credential != null; credential = credential.getNextSibling()) {
                    if (!credential.getNodeName().equals("#text")) {
                        String type = null;
                        Node typeNode = credential.getAttributes().getNamedItem("type");
                        if (typeNode != null) {
                            type = typeNode.getTextContent();
                        }
                        result.getCredentials().put(credential.getNodeName(), "byte[]".equals(type) ? credential.getTextContent().getBytes() : credential.getTextContent());
                    }

                }
            }
            else if (child.getNodeName().equals("memberof")) {
                if (!child.getNodeName().equals("#text")) {
                    result.getMemberOf().add(child.getTextContent());
                }
            }
        }

        return result;
    }

    /**
     * Value object for relaying user information between XML-processing methods and
     * UserAdmin users.
     * This indirection is necessary because we want to separate the parsing of the
     * XML, and the actual installation.
     */
    private class ProcessRole {
        private final int m_type;
        private final String m_name;
        private final Map<String, Object> m_properties = new HashMap<String, Object>();
        private final Map<String, Object> m_credentials = new HashMap<String, Object>();
        private final List<String> m_memberOf = new ArrayList<String>();

        ProcessRole(String name, int type) {
            m_name = name;
            m_type = type;
        }

        public int getType() {
            return m_type;
        }

        public String getName() {
            return m_name;
        }

        public Map<String, Object> getProperties() {
            return m_properties;
        }

        public Map<String, Object> getCredentials() {
            return m_credentials;
        }

        public List<String> getMemberOf() {
            return m_memberOf;
        }
    }

}
