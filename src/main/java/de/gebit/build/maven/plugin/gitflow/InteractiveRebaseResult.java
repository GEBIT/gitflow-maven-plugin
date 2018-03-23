//
// InteractiveRebaseResult.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Result of an interactive rebase that contains the status and git output
 * message.
 *
 * @author VMedvid
 */
public class InteractiveRebaseResult {

    /**
     * The result of an successful interactive rebase.
     */
    public static final InteractiveRebaseResult SUCCESS = new InteractiveRebaseResult(InteractiveRebaseStatus.SUCCESS,
            null);

    private InteractiveRebaseStatus status;
    private String gitMessage;

    /**
     * Creates an instance of the result.
     *
     * @param aStatus
     *            the status of the interactive rebase result
     * @param aGitMessage
     *            the git output message
     */
    public InteractiveRebaseResult(InteractiveRebaseStatus aStatus, String aGitMessage) {
        status = aStatus;
        gitMessage = aGitMessage;
    }

    /**
     * @return the status of the interactive rebase result
     */
    public InteractiveRebaseStatus getStatus() {
        return status;
    }

    /**
     * @return the git output message
     */
    public String getGitMessage() {
        return gitMessage;
    }

}
