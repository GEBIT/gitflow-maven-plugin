/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.LinkedList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow branch config mojo. Used to configure branch specific properties
 *
 * @author Erwin Tratar
 * @since 1.4.0
 */
@Mojo(name = GitFlowBranchConfigMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowBranchConfigMojo extends AbstractGitFlowMojo {

    static final String GOAL = "branch-config";

    /**
     * Set the property name to specify. If not set in interactive mode you will be
     * asked.
     *
     * @since 1.4.0
     */
    @Parameter(property = "propertyName", readonly = true)
    private String propertyName;

    /**
     * Specify the property value to set. If not specified, the property is removed
     * (in interactive mode you will be asked).
     *
     * @since 1.4.0
     */
    @Parameter(property = "propertyValue", readonly = true)
    private String propertyValue;

    /**
     * Name of the branch to configure. If not specified the current branch is used.
     *
     * @since 1.4.0
     */
    @Parameter(property = "branchName", readonly = true)
    private String branchName;

    /**
     * Remove all properties for the branch. If specified, other parameters are
     * ignored.
     *
     * @since 2.1.0
     */
    @Parameter(property = "removeAllForBranch", readonly = true)
    private String removeAllForBranch;

    /**
     * Clean-up branch-config. Remove branch specific properties for not existing
     * branches. If specified, other parameters are ignored.
     *
     * @since 2.1.5
     */
    @Parameter(property = "flow.cleanup", defaultValue = "false", readonly = true)
    private boolean cleanup;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting branch config process");
        // set git flow configuration
        initGitFlowConfig();

        if (cleanup) {
            cleanupBranchConfig();
        } else if (removeAllForBranch != null && !removeAllForBranch.trim().isEmpty()) {
            if (gitBranchExists(removeAllForBranch) || gitRemoteBranchExists(removeAllForBranch)) {
                if (!getPrompter().promptConfirmation(
                        "The branch '" + removeAllForBranch
                                + "' exists. Are you sure you want to remove all properties " + "for existing branch?",
                        false,
                        new GitFlowFailureInfo(
                                "The branch '" + removeAllForBranch + "' exists. All properties for an "
                                        + "existing branch can't be removed in non-interactive mode.",
                                "Either remove branch locally and remotely first or run in interactive mode"))) {
                    throw new GitFlowFailureException("Branch config process aborted by user.", null);
                }
            }
            getMavenLog().info("Removing all properties for branch '" + removeAllForBranch + "'");
            gitRemoveAllBranchCentralConfigsForBranch(removeAllForBranch, null);
        } else {
            propertyName = getPrompter().promptRequiredParameterValue("Which property to modify?", "propertyName",
                    propertyName,
                    new GitFlowFailureInfo(
                            "Property 'propertyName' is required in non-interactive mode but was not set.",
                            "Specify a propertyName or run in interactive mode.",
                            "'mvn flow:branch-config -DpropertyName=XXX -B' to predefine property name",
                            "'mvn flow:branch-config' to run in interactive mode"));

            propertyValue = getPrompter().promptOptionalParameterValue("Set the value to (empty to delete)",
                    "propertyValue", propertyValue);

            // modify the branch config
            if (branchName == null) {
                branchName = gitCurrentBranch();
            }

            if (propertyValue != null) {
                getMavenLog().info("Setting branch property '" + propertyName + "' for branch '" + branchName + "' to '"
                        + propertyValue + "'");
                gitSetBranchCentralConfig(branchName, propertyName, propertyValue);
            } else {
                getMavenLog().info("Removing branch property '" + propertyName + "' for branch '" + branchName + "'");
                gitRemoveBranchCentralConfig(branchName, propertyName);
            }
        }
        getMavenLog().info("Branch config process finished");
    }

    private void cleanupBranchConfig() throws MojoFailureException, CommandLineException {
        getMavenLog().info("Clean-up branch configs");
        if (!fetchRemote) {
            throw new GitFlowFailureException("Clean-up of branch configs can be executed only if fetchRemote=true.",
                    null);
        }
        if (!pushRemote) {
            throw new GitFlowFailureException("Clean-up of branch configs can be executed only if pushRemote=true.",
                    null);
        }
        BranchCentralConfigChanges changes = new BranchCentralConfigChanges();
        gitFetchOnce();
        List<String> configuredBranches = getConfiguredBranches();
        List<String> existingBranches = gitAllBranches("");
        for (String configuredBranch : configuredBranches) {
            if (!existingBranches.contains(configuredBranch)) {
                if (changes.isEmpty()) {
                    getMavenLog().info("List of orphaned branch configs:");
                }
                getMavenLog().info(" - " + configuredBranch);
                changes.removeAllForBranch(configuredBranch);
            }
        }
        if (!changes.isEmpty()) {
            boolean confirmed = getPrompter().promptConfirmation(
                    "Do you realy want to remove all orphaned branch configs listed above?", true, true);
            if (confirmed) {
                gitApplyBranchCentralConfigChanges(changes, "clean-up orphaned branch configs");
                getMavenLog().info("All orphaned branch configs listed above were removed.");
            } else {
                throw new GitFlowFailureException("Clean-up of branch configs aborted by user.", null);
            }
        } else {
            getMavenLog().info("No orphaned branch configs found. Nothing to clean-up.");
        }
    }

    private List<String> getConfiguredBranches() throws MojoFailureException, CommandLineException {
        List<String> branches = new LinkedList<>();
        CentralBranchConfigCache configCache = getCentralBranchConfigCache();
        for (BranchType branchType : BranchType.values()) {
            branches.addAll(configCache.getBranches(branchType));
        }
        return branches;
    }
}
