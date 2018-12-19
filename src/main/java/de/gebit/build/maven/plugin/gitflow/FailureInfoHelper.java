//
// FailureInfoHelper.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Helper class for creating gitflow failure messages.
 *
 * @author Volodja Medvid
 * @since 2.1.5
 */
public class FailureInfoHelper {

    /**
     * Creates gitflow failure info for failed project installation (mvn clean
     * install).
     *
     * @param goal
     *            The maven goal where project installation failed.
     * @param branch
     *            The branch where project installation was executed.
     * @param process
     *            The process where project installation failed (short goal
     *            description).
     * @param reason
     *            The short reason of the failure or <code>null</code>.
     * @return The gitflow failure info for failed project installation.
     */
    public static GitFlowFailureInfo installProjectFailure(String goal, String branch, String process, String reason) {
        return new GitFlowFailureInfo(
                "Failed to install the project on branch '" + branch + "' after " + process + "."
                        + (reason != null ? "\nReason: " + reason : ""),
                "Please solve the problems on project, add and commit your changes and run 'mvn flow:" + goal
                        + "' again in order to continue.\nDo NOT push the branch!\n"
                        + "Alternatively you can use property '-Dflow.installProject=false' while running "
                        + "'mvn flow:" + goal + "' to skip the project installation.",
                "'git add' and 'git commit' to commit your changes",
                "'mvn flow:" + goal + "' to continue " + process + " process after problem solving", "or 'mvn flow:"
                        + goal + " -Dflow.installProject=false' to continue by skipping the project installation");
    }

}
