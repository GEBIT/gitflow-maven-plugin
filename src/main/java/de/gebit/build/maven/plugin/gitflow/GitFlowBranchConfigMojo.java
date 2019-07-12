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
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Configure branch specific properties that can be used internally in gitflow
 * or externally, e.g. by Jenkins.
 * <p>
 * Set/remove a property for current or specified branch:
 * 
 * <pre>
 * mvn flow:branch-config [-DbranchName=XYZ] [-DpropertyName=JOB_BUILD] [-DpropertyValue=true]
 * </pre>
 * </p>
 * <p>
 * Show branch specific properties for current or specified branch (since
 * 2.1.9):
 * 
 * <pre>
 * mvn flow:branch-config -Dflow.show=true [-DbranchName=XYZ] [-DpropertyName=JOB_BUILD]
 * </pre>
 * </p>
 * <p>
 * Remove all properties for specified branch (since 2.1.0):
 * 
 * <pre>
 * mvn flow:branch-config -DremoveAllForBranch=XYZ
 * </pre>
 * </p>
 * <p>
 * Clean-up: remove branch specific properties for not existing feature, epic
 * and release branches (since 2.1.5):
 * 
 * <pre>
 * mvn flow:branch-config -Dflow.cleanup=true
 * </pre>
 * </p>
 *
 * @author Erwin Tratar
 * @since 1.4.0
 */
@Mojo(name = GitFlowBranchConfigMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowBranchConfigMojo extends AbstractGitFlowMojo {

    static final String GOAL = "branch-config";

    /**
     * Set the property name to specify. If not set in interactive mode you will
     * be asked.
     *
     * @since 1.4.0
     */
    @Parameter(property = "propertyName", readonly = true)
    private String propertyName;

    /**
     * Specify the property value to set. If not specified, the property is
     * removed (in interactive mode you will be asked).
     *
     * @since 1.4.0
     */
    @Parameter(property = "propertyValue", readonly = true)
    private String propertyValue;

    /**
     * Name of the branch to configure. If not specified the current branch is
     * used.
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
     * Clean-up branch-config. Remove branch specific properties for not
     * existing feature, epic and release branches. If specified, other
     * parameters are ignored.
     *
     * @since 2.1.5
     */
    @Parameter(property = "flow.cleanup", defaultValue = "false", readonly = true)
    private boolean cleanup;

    /**
     * Show branch specific properties for current branch or for branch defined
     * in <code>branchName</code> property.
     *
     * @since 2.1.9
     */
    @Parameter(property = "flow.show", defaultValue = "false", readonly = true)
    private boolean show;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting branch config process");
        // set git flow configuration
        initGitFlowConfig();

        if (cleanup) {
            cleanupBranchConfig();
        } else if (show) {
            if (branchName == null) {
                branchName = gitCurrentBranch();
            }
            CentralBranchConfigCache cache = getCentralBranchConfigCache();
            Properties properties = cache.getProperties(branchName);
            if (properties == null || properties.isEmpty()) {
                getMavenLog().info("No branch properties found for branch '" + branchName + "'");
            } else if (StringUtils.isNotEmpty(propertyName)) {
                String value = properties.getProperty(propertyName);
                if (value == null) {
                    getMavenLog().info(
                            "No value set for branch property '" + propertyName + "' for branch '" + branchName + "'");
                } else {
                    getMavenLog().info("Branch property '" + propertyName + "' for branch '" + branchName
                            + "' has value '" + value + "'");
                }
            } else {
                StringBuilder msg = new StringBuilder();
                msg.append("Branch '");
                msg.append(branchName);
                msg.append("' has following branch properties:");
                for (Entry<Object, Object> propEntry : properties.entrySet()) {
                    msg.append("\n  ");
                    msg.append(propEntry.getKey());
                    msg.append("=");
                    msg.append(propEntry.getValue());
                }
                getMavenLog().info(msg.toString());
            }
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

            if (StringUtils.isNotEmpty(propertyValue)) {
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
