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
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.ExtendedPrompter.SelectOption;

/**
 * The git flow maintenance branch start mojo.
 *
 * @author Erwin Tratar
 * @since 1.5.3
 */
@Mojo(name = "maintenance-start", aggregator = true)
public class GitFlowMaintenanceStartMojo extends AbstractGitFlowMojo {

    /**
     * The release version to create the maintenance branch from.
     *
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${releaseVersion}", required = false)
    protected String releaseVersion;

    /**
     * The version used for the branch itself.
     *
     * @since 1.5.3
     */
    @Parameter(defaultValue = "${maintenanceVersion}", required = false)
    protected String maintenanceVersion;

    /**
     * The first version to set on the branch.
     *
     * @since 1.5.3
     */
    @Parameter(defaultValue = "${firstMaintenanceVersion}", required = false)
    protected String firstMaintenanceVersion;

    /**
     * Filter to query for release branches to limit the results. The value is a
     * shell glob pattern if not starting with a ^ and as a regular expression
     * otherwise.
     *
     * @since 1.3.0
     * @since 1.5.9
     */
    @Parameter(defaultValue = "${releaseBranchFilter}", required = false)
    protected String releaseBranchFilter;

    /**
     * Number of release versions to offer. If not specified the selection is
     * unlimited. The order is from highest to lowest.
     *
     * @since 1.5.9
     */
    @Parameter(defaultValue = "${releaseVersionLimit}", required = false)
    protected Integer releaseVersionLimit;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting maintenance start process");
        // set git flow configuration
        initGitFlowConfig();

        checkCentralBranchConfig();
        checkUncommittedChanges();

        String currentBranchOrCommit = gitCurrentBranchOrCommit();
        String baseName = null;
        if (releaseVersion == null) {
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
            getMavenLog().info("Switching to base release tag '" + baseName + "'");
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

        if (!currentVersion.equals(branchFirstVersion)) {
            getMavenLog()
                    .info("Setting first version '" + branchFirstVersion + "' for project on maintenance branch...");
            mvnSetVersions(branchFirstVersion, "On maintenance branch: ", maintenanceBranch);
            gitCommit(commitMessages.getMaintenanceStartMessage());
        }

        if (pushRemote) {
            getMavenLog().info("Pushing maintenance branch '" + maintenanceBranch + "' to remote repository");
            gitPush(maintenanceBranch, false, false, true);
        }

        if (installProject) {
            getMavenLog().info("Cleaning and installing maintenance project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog()
                        .info("Maintenance start process finished with failed execution of clean and install project");
                throw new GitFlowFailureException(e,
                        "Failed to execute 'mvn clean install' on the project on maintenance branch after maintenance "
                                + "start.",
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
}
