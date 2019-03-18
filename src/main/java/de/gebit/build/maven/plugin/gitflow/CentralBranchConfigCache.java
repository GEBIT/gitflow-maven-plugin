//
// CentralBranchConfigCache.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Cache for central branch config.
 *
 * @author Volodymyr Medvid
 */
public class CentralBranchConfigCache {

    private Map<String, Properties> branchProperties = new HashMap<>();
    private Map<BranchType, List<String>> branches = new HashMap<>();

    /**
     * Create an instance of central branch config with passed
     *
     * @param initialBranchProperties
     *            the inital branch properties
     */
    public CentralBranchConfigCache(Map<String, Properties> initialBranchProperties) {
        refresh(initialBranchProperties);
    }

    /**
     * Remove old and cache new branch properties.
     *
     * @param newBranchProperties
     *            new branch properties to be cached
     */
    public void refresh(Map<String, Properties> newBranchProperties) {
        branchProperties.clear();
        branches.clear();
        if (newBranchProperties != null) {
            for (Entry<String, Properties> branchEntries : newBranchProperties.entrySet()) {
                String branchName = branchEntries.getKey();
                Properties properties = branchEntries.getValue();
                branchProperties.put(branchName, properties);
                String type = properties.getProperty(BranchConfigKeys.BRANCH_TYPE);
                BranchType branchType = BranchType.getByType(type);
                if (branchType != null) {
                    List<String> typeBranches = branches.get(branchType);
                    if (typeBranches == null) {
                        typeBranches = new LinkedList<>();
                        branches.put(branchType, typeBranches);
                    }
                    typeBranches.add(branchName);
                }
            }
        }
    }

    /**
     * Get properties for passed branch.
     *
     * @param branchName
     *            the name of the branch
     * @return properties for passed branch or empty properties if branch not found
     */
    public Properties getProperties(String branchName) {
        Properties properties = branchProperties.get(branchName);
        return properties != null ? properties : new Properties();
    }

    /**
     * Get branches of passed type.
     *
     * @param branchType
     *            the type of branches to be returned
     * @return list of branches or empty list if no branches for passed type found
     */
    public List<String> getBranches(BranchType branchType) {
        List<String> typeBranches = branches.get(branchType);
        return typeBranches != null ? typeBranches : new LinkedList<>();
    }

    /**
     * Get branches with passed base branch.
     *
     * @param baseBranch
     *            the base branch of branches to be returned
     * @return list of branches or empty list if no branches for passed base branch
     *         found
     */
    public List<String> getBranchesWithBaseBranch(String baseBranch) {
        List<String> branchesWithBaseBranch = new LinkedList<>();
        for (Entry<String, Properties> branchEntry : branchProperties.entrySet()) {
            if (baseBranch.equals(branchEntry.getValue().getProperty(BranchConfigKeys.BASE_BRANCH))) {
                branchesWithBaseBranch.add(branchEntry.getKey());
            }
        }
        return branchesWithBaseBranch;
    }
}
