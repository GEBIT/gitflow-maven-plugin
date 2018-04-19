//
// BranchType.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Enumeration of special branch types.
 *
 * @author Volodymyr Medvid
 */
public enum BranchType {
    FEATURE("feature"), EPIC("epic"), MAINTENANCE("maintenance"), RELEASE("release"), INTEGRATED("integrated");

    private String type;

    private BranchType(String aType) {
        type = aType;
    }

    /**
     * @return string representation of branch type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns branch type by string representation of the type or
     * <code>null</code> if type not found.
     *
     * @param type
     *            the string representation of branch type
     * @return branch type or <code>null</code> if type not found
     */
    public static BranchType getByType(String type) {
        if (type != null && type.length() > 0) {
            for (BranchType branchType : values()) {
                if (type.equalsIgnoreCase(branchType.getType())) {
                    return branchType;
                }
            }
        }
        return null;
    }
}
