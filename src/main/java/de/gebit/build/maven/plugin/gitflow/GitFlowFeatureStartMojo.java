/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Start implementing a new feature in a new feature branch.
 * <p>
 * Creates the new feature branch and updates the version in all
 * <code>pom.xml</code> files to a branch specific version (e.g.
 * <code>1.0.0-XYZ-1234-SNAPSHOT</code>). Feature branches can only be started
 * from the development branch, a maintenance branch or an epic branch.
 * <p>
 * If featureName not specified, you will be asked for a branch name (apply the
 * issue pattern). The changes will be committed in a single commit.
 * <p>
 * You may skip changing the version by specifying
 * <code>-DskipFeatureVersion=true</code>.
 * <p>
 * Use <code>-DjobBuild=true</code> to automatically create build jobs for the
 * feature branch.
 * <p>
 * Example:
 * <pre>
 * mvn flow:feature-start [-DfeatureName=XXXX] [-DjobBuild=true|false][-Dflow.installProject=true|false] [-D...]
 * </pre>
 *
 * @see GitFlowFeatureAbortMojo
 * @see GitFlowFeatureFinishMojo
 */
@Mojo(name = "feature-start", aggregator = true)
public class GitFlowFeatureStartMojo extends AbstractGitFlowFeatureMojo {

    /**
     * Whether to skip changing project version. Default is <code>false</code>
     * (the feature name will be appended to project version).
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipFeatureVersion", defaultValue = "false")
    private boolean skipFeatureVersion = false;

    /**
     * Additional maven commands/goals after the feature version has been
     * updated. Will be committed together with the version change. Can contain
     * an {@literal @}{version} placeholder which will be replaced with the new
     * version before execution. If empty the <code>commandsAfterVersion</code>
     * property is used.
     *
     * @since 1.3.2
     */
    @Parameter(property = "commandsAfterFeatureVersion", defaultValue = "")
    protected String commandsAfterFeatureVersion;

    /**
     * A natual language description of the <code>featureNamePattern</code>
     * which is used to print an error message. If not specified the pattern is
     * printed in the error message as is, which can be hard to understand.
     *
     * @since 1.3.1
     */
    @Parameter(property = "featureNamePatternDescription", required = false)
    protected String featureNamePatternDescription;

    /**
     * The feature name that will be used for feature branch.
     *
     * @since 1.5.14
     */
    @Parameter(property = "featureName", defaultValue = "${featureName}", required = false, readonly = true)
    protected String featureName;

    /**
     * Whether to configure automatic Jenkins job creation.
     *
     * @since 2.0.1
     */
    @Parameter(property = "jobBuild", required = false, readonly = true)
    protected boolean jobBuild = false;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature start process");
        initGitFlowConfig();

        checkCentralBranchConfig();
        checkUncommittedChanges();

        String featureBranchName;
        String featureIssue;
        String currentBranch = gitCurrentBranch();
        boolean continueOnCleanInstall = false;
        if (isFeatureBranch(currentBranch)) {
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null) {
                if ("featureStart.cleanInstall".equals(breakpoint)) {
                    continueOnCleanInstall = true;
                }
            }
        }
        if (!continueOnCleanInstall) {
            String baseBranch = currentBranch;
            if (!isMaintenanceBranch(baseBranch) && !isEpicBranch(baseBranch)) {
                baseBranch = gitFlowConfig.getDevelopmentBranch();
                if (!currentBranch.equals(baseBranch)) {
                    boolean confirmed = getPrompter()
                            .promptConfirmation(
                                    "Feature branch will be started not from current "
                                            + "branch but will be based off branch '" + baseBranch + "'. Continue?",
                                    true, true);
                    if (!confirmed) {
                        throw new GitFlowFailureException("Feature start process aborted by user.", null);
                    }
                }
            }
            getMavenLog().info("Base branch for new feature is '" + baseBranch + "'");

            String baseBranchStartPoint = selectBaseBranchStartPoint(baseBranch, "feature");

            featureName = getPrompter().promptRequiredParameterValue(
                    "What is a name of feature branch? " + gitFlowConfig.getFeatureBranchPrefix(), "featureName",
                    featureName, new StringValidator() {

                        @Override
                        public ValidationResult validate(String value) {
                            if (!validateFeatureName(value)) {
                                String invalidMessage;
                                if (featureNamePatternDescription != null) {
                                    invalidMessage = "The feature name '" + value + "' is invalid. "
                                            + featureNamePatternDescription;
                                } else {
                                    invalidMessage = "The feature name '" + value
                                            + "' is invalid. It does not match the required pattern: "
                                            + featureNamePattern;
                                }
                                return new ValidationResult(invalidMessage);
                            } else {
                                return ValidationResult.VALID;
                            }
                        }
                    },
                    new GitFlowFailureInfo(
                            "Property 'featureName' is required in non-interactive mode but was not set.",
                            "Specify a featureName or run in interactive mode.",
                            "'mvn flow:feature-start -DfeatureName=XXX -B'", "'mvn flow:feature-start'"));

            featureName = StringUtils.deleteWhitespace(featureName);
            getLog().info("New feature name: " + featureName);

            featureBranchName = gitFlowConfig.getFeatureBranchPrefix() + featureName;
            getLog().info("New feature branch name: " + featureBranchName);
            if (gitBranchExists(featureBranchName)) {
                throw new GitFlowFailureException("Feature branch '" + featureBranchName + "' already exists.",
                        "Either checkout the existing feature branch or start a new feature with another name.",
                        "'git checkout " + featureBranchName + "' to checkout the feature branch",
                        "'mvn flow:feature-start' to run again and specify another feature name");
            }
            if (gitRemoteBranchExists(featureBranchName)) {
                throw new GitFlowFailureException(
                        "Remote feature branch '" + featureBranchName + "' already exists on the remote '"
                                + gitFlowConfig.getOrigin() + "'.",
                        "Either checkout the existing feature branch or start a new feature with another name.",
                        "'git checkout " + featureBranchName + "' to checkout the feature branch",
                        "'mvn flow:feature-start' to run again and specify another feature name");
            }

            featureIssue = extractIssueNumberFromFeatureName(featureName);
            getLog().info("Feature issue number: " + featureIssue);
            String featureStartMessage = substituteWithIssueNumber(commitMessages.getFeatureStartMessage(),
                    featureIssue);

            getMavenLog().info("Creating feature branch '" + featureBranchName + "'");
            gitCreateAndCheckout(featureBranchName, baseBranchStartPoint);

            String currentVersion = getCurrentProjectVersion();
            String baseVersion = currentVersion;
            String versionChangeCommit = null;
            if (!skipFeatureVersion && !tychoBuild) {
                String version = currentVersion;
                getLog().info("Base project version: " + version);
                if (isEpicBranch(baseBranch)) {
                    version = removeEpicIssueFromVersion(version, baseBranch);
                    getLog().info("Removed epic issue number from project version: " + version);
                }
                baseVersion = version;
                version = insertSuffixInVersion(version, featureIssue);
                getLog().info("Added feature issue number to project version: " + version);
                if (!currentVersion.equals(version)) {
                    getMavenLog().info("Setting version '" + version + "' for project on feature branch...");
                    mvnSetVersions(version, GitFlowAction.FEATURE_START, "On feature branch: ", featureBranchName);
                    gitCommit(featureStartMessage);
                    versionChangeCommit = getCurrentCommit();
                } else {
                    getMavenLog().info(
                            "Project version for feature is same as base project version. Version update not needed");
                }
            }

            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(featureBranchName, BranchConfigKeys.BRANCH_TYPE, BranchType.FEATURE.getType());
            branchConfigChanges.set(featureBranchName, BranchConfigKeys.BASE_BRANCH, baseBranch);
            branchConfigChanges.set(featureBranchName, BranchConfigKeys.ISSUE_NUMBER, featureIssue);
            branchConfigChanges.set(featureBranchName, BranchConfigKeys.BASE_VERSION, baseVersion);
            branchConfigChanges.set(featureBranchName, BranchConfigKeys.START_COMMIT_MESSAGE, featureStartMessage);
            if (versionChangeCommit != null) {
                branchConfigChanges.set(featureBranchName, BranchConfigKeys.VERSION_CHANGE_COMMIT, versionChangeCommit);
            }
            gitApplyBranchCentralConfigChanges(branchConfigChanges, "feature '" + featureName + "' started");
        } else {
            getMavenLog().info("Restart after failed feature project installation detected");
            featureBranchName = currentBranch;
            featureIssue = gitGetBranchCentralConfig(featureBranchName, BranchConfigKeys.ISSUE_NUMBER);
        }

        if (installProject) {
            getMavenLog().info("Installing the feature project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog()
                        .info("Feature start process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(featureBranchName, "breakpoint", "featureStart.cleanInstall");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        "Failed to install the project on feature branch after feature start."
                                + (reason != null ? "\nReason: " + reason : ""),
                        "Please solve the problems on project, add and commit your changes and run "
                                + "'mvn flow:feature-start' again in order to continue.\n"
                                + "Do NOT push the feature branch!\nAlternatively you can use property "
                                + "'-Dflow.installProject=false' while running "
                                + "'mvn flow:feature-start' to skip the project installation.",
                        "'git add' and 'git commit' to commit your changes",
                        "'mvn flow:feature-start' to continue feature start process after problem solving",
                        "or 'mvn flow:feature-start -Dflow.installProject=false' to continue by skipping the project "
                                + "installation");
            }
        }
        gitRemoveBranchLocalConfig(featureBranchName, "breakpoint");

        if (pushRemote) {
            getMavenLog().info("Pushing feature branch '" + featureBranchName + "' to remote repository");
            gitPush(featureBranchName, false, false, true);
        }

        if (jobBuild) {
            getMavenLog().info("Configuring automatic Jenkins job creation");
            try {
                gitSetBranchCentralConfig(featureBranchName, "JOB_BUILD", "true");
            } catch (Exception exc) {
                getLog().error("Central branch config for automatical Jenkins job creation couldn't be stored.");
            }
        }

        getMavenLog().info("Feature for issue '" + featureIssue + "' started on branch '" + featureBranchName + "'");
        getMavenLog().info("Feature start process finished");
    }

    private String removeEpicIssueFromVersion(String version, String epicBranch)
            throws MojoFailureException, CommandLineException {
        String epicIssueNumber = getEpicIssueNumber(epicBranch);
        if (version.contains("-" + epicIssueNumber)) {
            return version.replaceFirst("-" + epicIssueNumber, "");
        }
        return version;
    }

    /**
     * If {@link #commandsAfterFeatureVersion} is set use it to replace the
     * {@link AbstractGitFlowMojo#commandsAfterVersion}.
     */
    @Override
    protected List<String> getCommandsAfterVersion(CommandContext commandPolicy) throws MojoFailureException {
        if (StringUtils.isEmpty(commandsAfterFeatureVersion)) {
            return super.getCommandsAfterVersion(commandPolicy);
        }
        List<String> result = new ArrayList<String>();
        result.add(commandsAfterFeatureVersion);
        result.addAll(getAdditionalVersionCommands(commandPolicy));
        return result;
    }

    /**
     * Check whether the given feature name matches the required pattern, if
     * any.
     */
    protected boolean validateFeatureName(String aFeatureName) {
        if (featureNamePattern == null) {
            return true;
        }
        return aFeatureName != null && aFeatureName.matches(featureNamePattern);
    }

    /**
     * Extracts the feature issue number from feature name using feature name
     * pattern. E.g. extracts issue number "GBLD-42" from feature name
     * "GBLD-42-someDescription" if default feature name pattern is used.
     * Returns feature name if issue number can't be extracted.
     *
     * @param aFeatureName
     *            the feature name
     * @return the extracted feature issue number or feature name if issue
     *         number can't be extracted
     */
    private String extractIssueNumberFromFeatureName(String aFeatureName) {
        String issueNumber = extractIssueNumberFromName(aFeatureName, featureNamePattern,
                "Feature branch conforms to <featureNamePattern>, but ther is no matching group to extract the issue "
                        + "number.",
                "Feature branch does not conform to <featureNamePattern> specified, cannot extract issue number.");
        return issueNumber != null ? issueNumber : aFeatureName;
    }
}
