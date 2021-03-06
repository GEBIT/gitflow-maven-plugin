/*
 * Copyright 2014-2015 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.ExtendedPrompter.SelectOption;

/**
 * Start a maintenance branch.
 * <p>
 * Start a maintenance branch off of a release tag or some other point. First,
 * prompts a tag or commit to base the branch upen, then creates the maintenance
 * branch, sets a specific version for maintenance and finally pushes the
 * maintenance branch to remote.
 * <p>
 * Example:
 * <pre>
 * mvn flow:maintenance-start [-DmaintenanceVersion=X.Y] [-DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT]
 * </pre>
 *
 * @author Erwin Tratar
 * @since 1.5.3
 */
@Mojo(name = GitFlowMaintenanceStartMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowMaintenanceStartMojo extends AbstractGitFlowMojo {

    static final String GOAL = "maintenance-start";

    /**
     * The release version to create the maintenance branch from.
     *
     * @since 1.3.0
     */
    @Parameter(property = "releaseVersion", readonly = true)
    protected String releaseVersion;

    /**
     * The version used for the branch itself.
     *
     * @since 1.5.3
     */
    @Parameter(property = "maintenanceVersion", readonly = true)
    protected String maintenanceVersion;

    /**
     * The first version to set on the branch.
     *
     * @since 1.5.3
     */
    @Parameter(property = "firstMaintenanceVersion", readonly = true)
    protected String firstMaintenanceVersion;

    /**
     * Filter to query for release branches to limit the results. The value is a
     * shell glob pattern if not starting with a ^ and as a regular expression
     * otherwise.
     *
     * @since 1.3.0
     * @since 1.5.9
     */
    @Parameter(property = "flow.releaseBranchFilter")
    protected String releaseBranchFilter;

    /**
     * Number of release versions to offer. If not specified the selection is
     * unlimited. The order is from highest to lowest.
     *
     * @since 1.5.9
     */
    @Parameter(property = "flow.releaseVersionLimit")
    protected Integer releaseVersionLimit;

    /**
     * The tag which the maintenance branch shold be created from.
     *
     * @since 2.2.0
     */
    @Parameter(property = "baseTag", readonly = true)
    protected String baseTag;

    /**
     * The commit which the maintenance branch should be created from.
     *
     * @since 2.2.0
     */
    @Parameter(property = "baseCommit", readonly = true)
    protected String baseCommit;
    
    /**
     * Whether to call Maven install goal after maintenance start. By default
     * the value of <code>installProject</code> parameter
     * (<code>flow.installProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.installProjectOnMaintenanceStart")
    private Boolean installProjectOnMaintenanceStart;
    
    /**
     * Maven goals (separated by space) to be used after maintenance start. By
     * default the value of <code>installProjectGoals</code> parameter
     * (<code>flow.installProjectGoals</code> property) is used.
     *
     * @since 2.3.1
     */
    @Parameter(property = "flow.installProjectGoalsOnMaintenanceStart")
    private String installProjectGoalsOnMaintenanceStart;

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting maintenance start process");
        checkCentralBranchConfig();
        checkUncommittedChanges();

        String currentBranchOrCommit = gitCurrentBranchOrCommit();
        String baseName = null;
        if (StringUtils.isNotEmpty(baseCommit)) {
            baseName = baseCommit;
            if (!gitCommitExists(baseName)) {
                throw new GitFlowFailureException(
                        "Commit '" + baseName + "' defined in 'baseCommit' property doesn't exist.",
                        "Please define an existing base commit in order to proceed.");
            }
            getLog().info("Commit [" + baseName + "] specified by property 'baseCommit' to start maintenance.");
        } else if (StringUtils.isNotEmpty(baseTag)) {
            baseName = baseTag;
            if (!gitTagExists(baseName)) {
                throw new GitFlowFailureException("Tag '" + baseName + "' defined in 'baseTag' property doesn't exist.",
                        "Please define an existing base tag in order to proceed.");
            }
            getLog().info("Tag [" + baseName + "] specified by property 'baseTag' to start maintenance.");
        } else if (releaseVersion == null) {
            if (settings.isInteractiveMode()) {
                List<String> releaseTags = getFilteredReleaseTags();
                SelectOption selectedOption = getPrompter().promptToSelectFromOrderedListAndOptions("Release:",
                        "Choose release to create the maintenance branch from or enter a custom tag or release name",
                        releaseTags, Arrays.asList(new SelectOption("0", currentBranchOrCommit, "<current commit>")),
                        Arrays.asList(new SelectOption("T", null, "<prompt for explicit tag name>")), null);

                if ("T".equals(selectedOption.getKey())) {
                    releaseVersion = getPrompter().promptValue("Enter explicit tag name");
                    baseName = getTagForReleaseVersion(releaseVersion,
                            new GitFlowFailureInfo("Tag '" + releaseVersion + "' doesn't exist.",
                                    "Run 'mvn flow:maintenance-start' again and enter name of an existing tag or select"
                                            + " another option from the list."));
                    getLog().info("Tag [" + baseName + "] for release version [" + releaseVersion
                            + "] entered by user to start maintenance.");
                } else if ("0".equals(selectedOption.getKey())) {
                    baseName = currentBranchOrCommit;
                    String currentCommit = getCurrentCommit();
                    getLog().info("Current commit [" + currentCommit
                            + (currentCommit.equals(currentBranchOrCommit) ? "" : " (" + currentBranchOrCommit + ")")
                            + "] selected by user to start maintenance.");
                } else {
                    baseName = selectedOption.getValue();
                    getLog().info("Tag [" + baseName + "] selected by user to start maintenance.");
                }
            } else {
                baseName = currentBranchOrCommit;
                String currentCommit = getCurrentCommit();
                getLog().info("Using current commit [" + currentCommit
                        + (currentCommit.equals(currentBranchOrCommit) ? "" : " (" + currentBranchOrCommit + ")")
                        + "] to start maintenance in batch mode.");
            }
        } else {
            baseName = getTagForReleaseVersion(releaseVersion, new GitFlowFailureInfo(
                    "Tag '" + releaseVersion + "' doesn't exist.",
                    "Run 'mvn flow:maintenance-start' again with existing tag name in 'releaseVersion' "
                            + "parameter or run in interactive mode to select another option from the list.",
                    "'mvn flow:maintenance-start -DreleaseVersion=XXX -B' to run with another tag name",
                    "'mvn flow:maintenance-start' to run in interactive mode and to select another option from the list"));
            getLog().info("Tag [" + baseName + "] selected for releaseVersion [" + releaseVersion
                    + "] passed via property to start maintenance.");
        }

        if (!baseName.equals(currentBranchOrCommit)) {
            getMavenLog().info("Switching to base commit '" + baseName + "'");
            gitCheckout(baseName);
        }

        String currentVersion = getCurrentProjectVersion();

        // get default maintenance version
        if (maintenanceVersion == null && firstMaintenanceVersion == null) {
            getLog().info("Properties 'maintenanceVersion' and 'firstMaintenanceVersion' not provided. "
                    + "Trying to calculate them from project version.");
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                getLog().info("Version info: " + versionInfo);
                final DefaultVersionInfo branchVersionInfo = new DefaultVersionInfo(
                        versionInfo.getDigits().subList(0, versionInfo.getDigits().size() - 1),
                        versionInfo.getAnnotation(), versionInfo.getAnnotationRevision(),
                        versionInfo.getBuildSpecifier() != null ? "-" + versionInfo.getBuildSpecifier() : null, null,
                        null, null);
                getLog().info("Branch Version info: " + branchVersionInfo);
                maintenanceVersion = branchVersionInfo.getReleaseVersionString();
                firstMaintenanceVersion = versionInfo.getNextVersion().getSnapshotVersionString();
                getLog().info("Project version: " + currentVersion);
                getLog().info("Calculated maintenanceVersion: " + maintenanceVersion);
                getLog().info("Calculated firstMaintenanceVersion: " + firstMaintenanceVersion);
                if (!settings.isInteractiveMode()) {
                    getLog().info("Using calculated maintenanceVersion and firstMaintenanceVersion for maintenance "
                            + "branch in batch mode.");

                }
            } catch (VersionParseException e) {
                if (!baseName.equals(currentBranchOrCommit)) {
                    getMavenLog().info("Switching back to origin branch/commit '" + currentBranchOrCommit + "'");
                    gitCheckout(currentBranchOrCommit);
                }
                throw new GitFlowFailureException(e,
                        "Failed to calculate maintenance versions. The project version '" + currentVersion
                                + "' can't be parsed.",
                        "Check the version of the project or run 'mvn flow:maintenance-start' with specified "
                                + "parameters 'maintenanceVersion' and 'firstMaintenanceVersion'.",
                        "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT'"
                                + " to predefine default version used for the branch name and default first project "
                                + "version in maintenance branch");
            }
        } else if (maintenanceVersion == null || firstMaintenanceVersion == null) {
            if (!baseName.equals(currentBranchOrCommit)) {
                getMavenLog().info("Switching back to origin branch/commit '" + currentBranchOrCommit + "'");
                gitCheckout(currentBranchOrCommit);
            }
            throw new GitFlowFailureException(
                    "Either both parameters 'maintenanceVersion' and 'firstMaintenanceVersion' must be specified or "
                            + "none.",
                    "Run 'mvn flow:maintenance-start' either with both parameters 'maintenanceVersion' and "
                            + "'firstMaintenanceVersion' or none of them.",
                    "'mvn flow:maintenance-start -DmaintenanceVersion=X.Y -DfirstMaintenanceVersion=X.Y.Z-SNAPSHOT' to "
                            + "predefine default version used for the branch name and default first project version in "
                            + "maintenance branch",
                    "'mvn flow:maintenance-start' to calculate default version used for the branch name and default "
                            + "first project version in maintenance branch automatically based on actual project "
                            + "version");
        }

        String branchVersion = getPrompter().promptRequiredParameterValue("What is the maintenance version?",
                "maintenanceVersion", null, maintenanceVersion);
        String branchFirstVersion = getPrompter().promptRequiredParameterValue(
                "What is the first version on the maintenance branch?", "firstMaintenanceVersion", null,
                firstMaintenanceVersion);

        getMavenLog().info("Using maintenanceVersion: " + branchVersion);
        getMavenLog().info("Using firstMaintenanceVersion: " + branchFirstVersion);

        String maintenanceBranch = gitFlowConfig.getMaintenanceBranchPrefix() + branchVersion;
        if (gitBranchExists(maintenanceBranch)) {
            if (!baseName.equals(currentBranchOrCommit)) {
                getMavenLog().info("Switching back to origin branch/commit '" + currentBranchOrCommit + "'");
                gitCheckout(currentBranchOrCommit);
            }
            throw new GitFlowFailureException(
                    "Maintenance branch '" + maintenanceBranch + "' already exists. Cannot start maintenance.",
                    "Either checkout the existing maintenance branch or start a new maintenance with another "
                            + "maintenance version.",
                    "'git checkout " + maintenanceBranch + "' to checkout the maintenance branch",
                    "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        }
        if (gitRemoteBranchExists(maintenanceBranch)) {
            if (!baseName.equals(currentBranchOrCommit)) {
                getMavenLog().info("Switching back to origin branch/commit '" + currentBranchOrCommit + "'");
                gitCheckout(currentBranchOrCommit);
            }
            throw new GitFlowFailureException(
                    "Remote maintenance branch '" + maintenanceBranch + "' already exists on the remote '"
                            + gitFlowConfig.getOrigin() + "'. Cannot start maintenance.",
                    "Either checkout the existing maintenance branch or start a new maintenance with another "
                            + "maintenance version.",
                    "'git checkout " + maintenanceBranch + "' to checkout the maintenance branch",
                    "'mvn flow:maintenance-start' to run again and specify another maintenance version");
        }

        getMavenLog().info("Creating maintenance branch '" + maintenanceBranch + "' based on '" + baseName + "'");
        // git checkout -b maintenance/... master
        gitCreateAndCheckout(maintenanceBranch, baseName);

        if (!versionlessMode.needsVersionChangeCommit() || !currentVersion.equals(branchFirstVersion)) {
            getMavenLog()
                    .info("Setting first version '" + branchFirstVersion + "' for project on maintenance branch...");
            mvnSetVersions(branchFirstVersion, GitFlowAction.MAINTENANCE_START, "On maintenance branch: ",
                    maintenanceBranch, commitMessages.getMaintenanceStartMessage());
        }

        if (pushRemote) {
            getMavenLog().info("Pushing maintenance branch '" + maintenanceBranch + "' to remote repository");
            gitPush(maintenanceBranch, false, false, true);
        }

        if (isInstallProject()) {
            getMavenLog().info("Installing the maintenance project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Maintenance start process finished with failed project installation");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        "Failed to install the project on maintenance branch after maintenance start."
                                + (reason != null ? "\nReason: " + reason : ""),
                        "Maintenance branch was created successfully. No further steps with gitflow are required.");
            }
        }
        getMavenLog().info("Maintenance start process finished");
    }

    private String getTagForReleaseVersion(String aReleaseVersion, GitFlowFailureInfo tagNotExistsError)
            throws GitFlowFailureException, MojoFailureException, CommandLineException {
        if (gitTagExists(aReleaseVersion)) {
            return aReleaseVersion;
        } else {
            String tagWithPrefix = gitFlowConfig.getVersionTagPrefix() + aReleaseVersion;
            if (gitTagExists(tagWithPrefix)) {
                return tagWithPrefix;
            } else {
                throw new GitFlowFailureException(tagNotExistsError);
            }
        }
    }

    private List<String> getFilteredReleaseTags() throws MojoFailureException, CommandLineException {
        boolean regexMode = releaseBranchFilter != null && releaseBranchFilter.startsWith("^");
        List<String> releaseTags = gitListReleaseTags(gitFlowConfig.getVersionTagPrefix(),
                regexMode ? null : releaseBranchFilter);

        // post process selection
        if (regexMode) {
            Pattern versionFilter = Pattern
                    .compile("^\\Q" + gitFlowConfig.getVersionTagPrefix() + "\\E" + releaseBranchFilter.substring(1));
            for (Iterator<String> it = releaseTags.iterator(); it.hasNext();) {
                if (!versionFilter.matcher(it.next()).matches()) {
                    it.remove();
                }
            }
        }
        if (releaseVersionLimit != null && releaseTags.size() > releaseVersionLimit) {
            releaseTags = releaseTags.subList(0, releaseVersionLimit);
        }
        return releaseTags;
    }
    
    @Override
    protected Boolean getIndividualInstallProjectConfig() {
        return installProjectOnMaintenanceStart;
    }
    
    @Override
    protected String getIndividualInstallProjectGoals() {
        return installProjectGoalsOnMaintenanceStart;
    }
}
