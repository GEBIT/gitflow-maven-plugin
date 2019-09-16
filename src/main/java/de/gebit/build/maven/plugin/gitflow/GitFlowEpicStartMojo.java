//
// GitFlowEpicStartMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Start implementing a new Epic.
 * <p>
 * Creates the new epic branch to aggregate multiple features and updates the
 * version in all <code>pom.xml</code> files to a branch specific version (e.g.
 * <code>1.0.0-XYZ-1234-SNAPSHOT</code>). If <code>epicName</code> is not specified, you will be
 * asked for a branch name (apply the issue pattern). The version changes will be
 * committed in a single commit. Epic branches can only be started from the master or a maintenance branch.
 * <p>
 * Make sure your local development is not behind
 * the remote, before executing.
 * <p>
 * Use <code>-DjobBuild=true</code> to automatically create build jobs for the epic branch.
 * <p>
 * Example:
 * <pre>
 * mvn flow:epic-start [-DepicName=XXXX] [-DjobBuild=true|false] [-Dflow.installProject=true|false] [-D...]
 * </pre>
 *
 * @author Volodymyr Medvid
 * @see GitFlowEpicAbortMojo
 * @see GitFlowEpicUpdateMojo
 * @see GitFlowEpicFinishMojo
 * @since 2.0.0
 */
@Mojo(name = GitFlowEpicStartMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowEpicStartMojo extends AbstractGitFlowEpicMojo {

    static final String GOAL = "epic-start";

    /**
     * A natual language description of the <code>epicNamePattern</code> which
     * is used to print an error message. If not specified the pattern is
     * printed in the error message as is, which can be hard to understand.
     */
    @Parameter(property = "flow.epicNamePatternDescription")
    protected String epicNamePatternDescription;

    /**
     * The epic name that will be used for epic branch.
     */
    @Parameter(property = "epicName", readonly = true)
    protected String epicName;

    /**
     * Whether to configure automatical Jenkins job creation.
     *
     * @since 2.0.1
     */
    @Parameter(property = "jobBuild", readonly = true)
    protected boolean jobBuild = false;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting epic start process");
        checkCentralBranchConfig();
        checkUncommittedChanges();

        String epicBranchName;
        String epicIssue;
        String currentBranch = gitCurrentBranch();
        boolean continueOnCleanInstall = false;
        if (isEpicBranch(currentBranch)) {
            String breakpoint = gitGetBranchLocalConfig(currentBranch, "breakpoint");
            if (breakpoint != null) {
                if ("epicStart.cleanInstall".equals(breakpoint)) {
                    continueOnCleanInstall = true;
                }
            }
        }

        if (!continueOnCleanInstall) {
            String baseBranch = currentBranch;
            if (!isMaintenanceBranch(baseBranch)) {
                baseBranch = gitFlowConfig.getDevelopmentBranch();
                if (!currentBranch.equals(baseBranch)) {
                    boolean confirmed = getPrompter().promptConfirmation("Epic branch will be started not from current "
                            + "branch but will be based off branch '" + baseBranch + "'. Continue?", true, true);
                    if (!confirmed) {
                        throw new GitFlowFailureException("Epic start process aborted by user.", null);
                    }
                }
            }
            getMavenLog().info("Base branch for new epic is '" + baseBranch + "'");

            String baseBranchStartPoint = selectBaseBranchStartPoint(baseBranch, "epic");

            epicName = getPrompter().promptRequiredParameterValue(
                    "What is a name of epic branch? " + gitFlowConfig.getEpicBranchPrefix(), "epicName", epicName,
                    new StringValidator() {

                        @Override
                        public ValidationResult validate(String value) {
                            if (!validateEpicName(value)) {
                                String invalidMessage;
                                if (StringUtils.isNotEmpty(epicNamePatternDescription)) {
                                    invalidMessage = "The epic name '" + value + "' is invalid. "
                                            + epicNamePatternDescription;
                                } else {
                                    invalidMessage = "The epic name '" + value
                                            + "' is invalid. It does not match the required pattern: "
                                            + epicNamePattern;
                                }
                                return new ValidationResult(invalidMessage);
                            } else {
                                return ValidationResult.VALID;
                            }
                        }
                    },
                    new GitFlowFailureInfo("Property 'epicName' is required in non-interactive mode but was not set.",
                            "Specify a epicName or run in interactive mode.", "'mvn flow:epic-start -DepicName=XXX -B'",
                            "'mvn flow:epic-start'"));

            epicName = StringUtils.deleteWhitespace(epicName);
            getLog().info("New epic name: " + epicName);

            epicBranchName = gitFlowConfig.getEpicBranchPrefix() + epicName;
            getLog().info("New epic branch name: " + epicBranchName);
            if (gitBranchExists(epicBranchName)) {
                throw new GitFlowFailureException("Epic branch '" + epicBranchName + "' already exists.",
                        "Either checkout the existing epic branch or start a new epic with another name.",
                        "'git checkout " + epicBranchName + "' to checkout the epic branch",
                        "'mvn flow:epic-start' to run again and specify another epic name");
            }
            if (gitRemoteBranchExists(epicBranchName)) {
                throw new GitFlowFailureException(
                        "Remote epic branch '" + epicBranchName + "' already exists on the remote '"
                                + gitFlowConfig.getOrigin() + "'.",
                        "Either checkout the existing epic branch or start a new epic with another name.",
                        "'git checkout " + epicBranchName + "' to checkout the epic branch",
                        "'mvn flow:epic-start' to run again and specify another epic name");
            }

            getMavenLog().info("Creating epic branch '" + epicBranchName + "'");
            gitCreateAndCheckout(epicBranchName, baseBranchStartPoint);

            epicIssue = extractIssueNumberFromEpicName(epicName);
            getLog().info("Epic issue number: " + epicIssue);
            String epicStartMessage = substituteWithIssueNumber(commitMessages.getEpicStartMessage(), epicIssue);
            String currentVersion = getCurrentProjectVersion();
            String baseVersion = currentVersion;
            String versionChangeCommit = null;
            if (versionless || !tychoBuild) {
                getLog().info("Creating project version for epic.");
                getLog().info("Base project version: " + currentVersion);
                String version = insertSuffixInVersion(currentVersion, epicIssue);
                getLog().info("Added epic issue number to project version: " + version);
                if (versionless || !currentVersion.equals(version)) {
                    getMavenLog().info("Setting version '" + version + "' for project on epic branch...");
                    versionChangeCommit = mvnSetVersions(version, GitFlowAction.EPIC_START, "On epic branch: ", epicBranchName, epicStartMessage);
                } else {
                    getMavenLog().info(
                            "Project version for epic is same as base project version. Version update not needed.");
                }
            }

            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BRANCH_TYPE, BranchType.EPIC.getType());
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BASE_BRANCH, baseBranch);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.ISSUE_NUMBER, epicIssue);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BASE_VERSION, baseVersion);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.START_COMMIT_MESSAGE, epicStartMessage);
            if (versionChangeCommit != null) {
                branchConfigChanges.set(epicBranchName, BranchConfigKeys.VERSION_CHANGE_COMMIT, versionChangeCommit);
            }
            gitApplyBranchCentralConfigChanges(branchConfigChanges, "epic '" + epicName + "' started");
        } else {
            getMavenLog().info("Restart after failed epic project installation detected");
            epicBranchName = currentBranch;
            epicIssue = gitGetBranchCentralConfig(epicBranchName, BranchConfigKeys.ISSUE_NUMBER);
        }

        if (installProject) {
            getMavenLog().info("Installing the epic project...");
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                getMavenLog().info("Epic start process paused on failed project installation to fix project problems");
                gitSetBranchLocalConfig(epicBranchName, "breakpoint", "epicStart.cleanInstall");
                String reason = null;
                if (e instanceof GitFlowFailureException) {
                    reason = ((GitFlowFailureException) e).getProblem();
                }
                throw new GitFlowFailureException(e,
                        FailureInfoHelper.installProjectFailure(GOAL, epicBranchName, "epic start", reason));
            }
        }
        gitRemoveBranchLocalConfig(epicBranchName, "breakpoint");

        if (pushRemote) {
            getMavenLog().info("Pushing epic branch '" + epicBranchName + "' to remote repository");
            gitPush(epicBranchName, false, false, true);
        }

        if (jobBuild) {
            getMavenLog().info("Configuring automatic Jenkins job creation");
            try {
                gitSetBranchCentralConfig(epicBranchName, "JOB_BUILD", "true");
            } catch (Exception exc) {
                getLog().error("Central branch config for automatical Jenkins job creation couldn't be stored.");
            }
        }

        getMavenLog().info("Epic for issue '" + epicIssue + "' started on branch '" + epicBranchName + "'");
        getMavenLog().info("Epic start process finished");
    }

    private boolean validateEpicName(String anEpicName) {
        if (StringUtils.isEmpty(epicNamePattern)) {
            return true;
        }
        return anEpicName.matches(epicNamePattern);
    }

    /**
     * Extract the epic issue number from epic name using epic name pattern.
     * E.g. extract issue number "GBLD-42" from epic name
     * "GBLD-42-someDescription" if default epic name pattern is used. Return
     * epic name if issue number can't be extracted.
     *
     * @param anEpicName
     *            the epic name
     * @return the extracted epic issue number or epic name if issue number
     *         can't be extracted
     */
    private String extractIssueNumberFromEpicName(String anEpicName) {
        String issueNumber = anEpicName;
        if (StringUtils.isNotEmpty(epicNamePattern)) {
            // extract the issue number only
            Matcher m = Pattern.compile(epicNamePattern).matcher(anEpicName);
            if (m.matches()) {
                if (m.groupCount() == 0) {
                    getLog().warn("Epic branch conforms to <epicNamePattern>, but ther is no matching"
                            + " group to extract the issue number.");
                } else {
                    issueNumber = m.group(1);
                }
            } else {
                getLog().warn("Epic branch does not conform to <epicNamePattern> specified, cannot "
                        + "extract issue number.");
            }
        }
        return issueNumber;
    }

}
