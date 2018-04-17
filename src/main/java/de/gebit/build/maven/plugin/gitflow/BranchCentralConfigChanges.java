//
// BranchCentralConfigChanges.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * {@link BranchCentralConfigChanges} collects set/remove changes on branch
 * central config. It can be used to apply multiple changes with one access.
 *
 * @author Volodymyr Medvid
 */
public class BranchCentralConfigChanges {

    private Map<String, Properties> setProperties = new HashMap<>();

    private Map<String, List<String>> removedProperties = new HashMap<>();

    /**
     * Set the central branch config value for passed branch to be applied
     * later.
     *
     * @param branchName
     *            the name of the branch
     * @param configName
     *            the config name
     * @param value
     *            the value to be set
     */
    public void set(String branchName, String configName, String value) {
        Properties branchPorperties = setProperties.get(branchName);
        if (branchPorperties == null) {
            branchPorperties = new Properties();
            setProperties.put(branchName, branchPorperties);
        }
        branchPorperties.setProperty(configName, value);
        List<String> removedBranchProperties = removedProperties.get(branchName);
        if (removedBranchProperties != null) {
            removedBranchProperties.remove(configName);
        }
    }

    /**
     * Remove the central branch config for passed branch to be applied later.
     *
     * @param branchName
     *            the name of the branch
     * @param configName
     *            the config name
     */
    public void remove(String branchName, String configName) {
        List<String> removedBranchProperties = removedProperties.get(branchName);
        if (removedBranchProperties == null) {
            removedBranchProperties = new LinkedList<>();
        }
        if (!removedBranchProperties.contains(configName)) {
            removedBranchProperties.add(configName);
        }
        Properties branchPorperties = setProperties.get(branchName);
        if (branchPorperties != null) {
            branchPorperties.remove(configName);
        }
    }

    public Map<String, List<Change>> getAllChanges() {
        Map<String, List<Change>> allChanges = new HashMap<>();
        for (Entry<String, Properties> setPropertiesEntry : setProperties.entrySet()) {
            String branchName = setPropertiesEntry.getKey();
            Properties properties = setPropertiesEntry.getValue();
            if (!properties.isEmpty()) {
                List<Change> changes = new LinkedList<>();
                allChanges.put(branchName, changes);
                Enumeration<?> keys = properties.propertyNames();
                while (keys.hasMoreElements()) {
                    String key = (String) keys.nextElement();
                    String value = properties.getProperty(key);
                    changes.add(new Change(key, value));
                }
            }
        }
        for (Entry<String, List<String>> removedPropertiesEntry : removedProperties.entrySet()) {
            String branchName = removedPropertiesEntry.getKey();
            List<String> keys = removedPropertiesEntry.getValue();
            if (!keys.isEmpty()) {
                List<Change> changes = new LinkedList<>();
                allChanges.put(branchName, changes);
                for (String key : keys) {
                    changes.add(new Change(key));
                }
            }
        }
        return allChanges;
    }

    public enum ChangeType {
        SET, REMOVE;
    }

    public class Change {
        private ChangeType type;
        private String configName;
        private String value;

        public Change(String aConfigName, String aValue) {
            type = ChangeType.SET;
            configName = aConfigName;
            value = aValue;
        }

        public Change(String aConfigName) {
            type = ChangeType.REMOVE;
            configName = aConfigName;
        }

        public ChangeType getType() {
            return type;
        }

        public String getConfigName() {
            return configName;
        }

        public String getValue() {
            return value;
        }

    }
}
