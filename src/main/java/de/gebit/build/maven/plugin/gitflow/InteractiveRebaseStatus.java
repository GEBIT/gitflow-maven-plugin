//
// InteractiveRebaseStatus.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Status of an interactive rebase result.
 *
 * @author VMedvid
 */
public enum InteractiveRebaseStatus {

    /**
     * The interactive rebase completed successfully.
     */
    SUCCESS,

    /**
     * The interactive rebase paused (e.g. because the 'edit' command was used).
     */
    PAUSED,

    /**
     * The interactive rebase caused a conflict.
     */
    CONFLICT;

}
