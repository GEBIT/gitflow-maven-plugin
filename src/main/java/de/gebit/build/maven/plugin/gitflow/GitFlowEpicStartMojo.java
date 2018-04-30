//
// GitFlowEpicStartMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Start an Epic Branch. Epic branches are more like maintenance branches, but
 * will not live forever and will be merged back to the base branch when the
 * Epic is done. Epic branches can be started on development or maintenance
 * branches and they are starting points for feature branches.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
@Mojo(name = "epic-start", aggregator = true)
public class GitFlowEpicStartMojo extends AbstractGitFlowEpicMojo {

    /**
     * A natual language description of the <code>epicNamePattern</code> which
     * is used to print an error message. If not specified the pattern is
     * printed in the error message as is, which can be hard to understand.
     */
    @Parameter(property = "epicNamePatternDescription", required = false)
    protected String epicNamePatternDescription;

    /**
     * The epic name that will be used for epic branch.
     */
    @Parameter(property = "epicName", defaultValue = "${epicName}", required = false, readonly = true)
    protected String epicName;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getLog().info("Starting epic start process.");
        initGitFlowConfig();

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
            getLog().info("Base branch for new epic: " + baseBranch);
            String originalBaseBranch = baseBranch;

            // use integration branch?
            final String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
            gitEnsureLocalBranchIsUpToDateIfExists(integrationBranch,
                    new GitFlowFailureInfo(
                            "Local and remote integration branches '" + integrationBranch
                                    + "' diverge, this indicates a severe error condition on your branches.",
                            "Please consult a gitflow expert on how to fix this!"));
            gitAssertLocalAndRemoteBranchesOnSameState(baseBranch);
            if (gitBranchExists(integrationBranch)) {
                boolean useIntegrationBranch = true;
                if (!Objects.equals(getCurrentCommit(integrationBranch), getCurrentCommit(baseBranch))) {
                    useIntegrationBranch = getPrompter().promptConfirmation("The current commit on " + baseBranch
                            + " is not integrated. Create a branch of the last integrated commit (" + integrationBranch
                            + ")?", true, true);
                }
                if (useIntegrationBranch) {
                    if (!gitIsAncestorBranch(integrationBranch, baseBranch)) {
                        throw new GitFlowFailureException(
                                "Integration branch '" + integrationBranch + "' is ahead of base branch '" + baseBranch
                                        + "', this indicates a severe error condition on your branches.",
                                " Please consult a gitflow expert on how to fix this!");
                    }

                    getLog().info("Using integration branch '" + integrationBranch + "' as start point for new epic.");
                    baseBranch = integrationBranch;
                }
            }

            epicName = getPrompter().promptRequiredParameterValue(
                    "What is a name of epic branch? " + gitFlowConfig.getEpicBranchPrefix(), "epicName", epicName,
                    new StringValidator() {

                        @Override
                        public ValidationResult validate(String value) {
                            if (!validateEpicName(value)) {
                                String invalidMessage;
                                if (epicNamePatternDescription != null) {
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

            gitCreateAndCheckout(epicBranchName, baseBranch);

            epicIssue = extractIssueNumberFromEpicName(epicName);
            getLog().info("Epic issue number: " + epicIssue);
            String epicStartMessage = substituteWithIssueNumber(commitMessages.getEpicStartMessage(), epicIssue);
            String currentVersion = getCurrentProjectVersion();
            String baseVersion = currentVersion;
            String versionChangeCommit = null;
            if (!tychoBuild) {
                getLog().info("Creating project version for epic.");
                getLog().info("Base project version: " + currentVersion);
                String version = insertSuffixInVersion(currentVersion, epicIssue);
                getLog().info("Added epic issue number to project version: " + version);
                if (!currentVersion.equals(version)) {
                    mvnSetVersions(version, "On epic branch: ");
                    gitCommit(epicStartMessage);
                    versionChangeCommit = getCurrentCommit();
                } else {
                    getLog().info(
                            "Project version for epic is same as base project version. Version update not needed.");
                }
            }

            BranchCentralConfigChanges branchConfigChanges = new BranchCentralConfigChanges();
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BRANCH_TYPE, BranchType.EPIC.getType());
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BASE_BRANCH, originalBaseBranch);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.ISSUE_NUMBER, epicIssue);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.BASE_VERSION, baseVersion);
            branchConfigChanges.set(epicBranchName, BranchConfigKeys.START_COMMIT_MESSAGE, epicStartMessage);
            if (versionChangeCommit != null) {
                branchConfigChanges.set(epicBranchName, BranchConfigKeys.VERSION_CHANGE_COMMIT, versionChangeCommit);
            }
            gitApplyBranchCentralConfigChanges(branchConfigChanges, "epic '" + epicName + "' started");
        } else {
            epicBranchName = currentBranch;
            epicIssue = gitGetBranchCentralConfig(epicBranchName, BranchConfigKeys.ISSUE_NUMBER);
        }

        if (installProject) {
            try {
                mvnCleanInstall();
            } catch (MojoFailureException e) {
                gitSetBranchLocalConfig(epicBranchName, "breakpoint", "epicStart.cleanInstall");
                throw new GitFlowFailureException(e,
                        "Failed to execute 'mvn clean install' on the project on epic branch after epic start.",
                        "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                                + "'mvn flow:epic-start' again in order to continue.");
            }
        }
        gitRemoveBranchLocalConfig(epicBranchName, "breakpoint");

        if (pushRemote) {
            gitPush(epicBranchName, false, false);
        }
        getLog().info("Epic for issue '" + epicIssue + "' started on branch '" + epicBranchName + "'.");
        getLog().info("Epic start process finished.");
    }

    private boolean validateEpicName(String anEpicName) {
        if (epicNamePattern == null) {
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
        if (epicNamePattern != null) {
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
