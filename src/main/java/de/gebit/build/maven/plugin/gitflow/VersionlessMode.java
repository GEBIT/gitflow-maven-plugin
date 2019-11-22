//
// VersionlessMode.java
//
// Copyright (C) 2019
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Mode of operation for versionless POMs.
 */
public enum VersionlessMode {
    /**
     * Non-versionless mode (default)
     */
    NONE(true),

    /**
     * Versionless mode where the version is derived from a file (.mvn/pom.version)
     */
    FILE(true),

    /**
     * Versionless mode where the version is kept in the branch-config.
     */
    CONFIG(false),

    /**
     * Versionless mode where the version is derived from the latest git tag.
     */
    TAGS(false);

    private boolean needsVersionChangeCommit;

    @SuppressWarnings("hiding")
    private VersionlessMode(boolean needsVersionChangeCommit) {
        this.needsVersionChangeCommit = needsVersionChangeCommit;
    }

    public boolean needsVersionChangeCommit() {
        return needsVersionChangeCommit;
    }
}
