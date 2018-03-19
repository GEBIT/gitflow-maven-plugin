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

import org.apache.maven.artifact.Artifact;
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
     * Whether to skip changing project version. Default is <code>false</code>
     * (the epic name will be appended to project version).
     */
    @Parameter(property = "skipEpicVersion", defaultValue = "false")
    private boolean skipEpicVersion = false;

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
        // set git flow configuration
        initGitFlowConfig();

        // check uncommitted changes
        checkUncommittedChanges();

        // fetch and check remote
        String currentBranch = gitCurrentBranch();
        String baseBranch = currentBranch.startsWith(gitFlowConfig.getMaintenanceBranchPrefix()) ? currentBranch
                : gitFlowConfig.getDevelopmentBranch();

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

                getLog().info("Using integration branch '" + integrationBranch + "'");
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
                                        + "' is invalid. It does not match the required pattern: " + epicNamePattern;
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

        String epicBranchName = gitFlowConfig.getEpicBranchPrefix() + epicName;
        // git for-each-ref refs/heads/epic/...
        final String epicBranchInfo = gitFindBranch(epicBranchName);
        if (StringUtils.isNotBlank(epicBranchInfo)) {
            throw new GitFlowFailureException("Epic branch '" + epicBranchName + "' already exists.",
                    "Either checkout the existing epic branch or start a new epic with another name.",
                    "'git checkout " + epicBranchName + "' to checkout the epic branch",
                    "'mvn flow:epic-start' to run again and specify another epic name");
        }
        String remoteEpicBranchInfo = gitFindRemoteBranch(epicBranchName);
        if (StringUtils.isNotBlank(remoteEpicBranchInfo)) {
            throw new GitFlowFailureException(
                    "Remote epic branch '" + epicBranchName + "' already exists on the remote '"
                            + gitFlowConfig.getOrigin() + "'.",
                    "Either checkout the existing epic branch or start a new epic with another name.",
                    "'git checkout " + epicBranchName + "' to checkout the epic branch",
                    "'mvn flow:epic-start' to run again and specify another epic name");
        }


        // git checkout -b ... develop
        gitCreateAndCheckout(epicBranchName, baseBranch);

        if (!skipEpicVersion && !tychoBuild) {
            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            String version = null;
            String epicIssue = extractIssueNumberFromEpicName(epicName);
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                version = versionInfo.getReleaseVersionString() + "-" + epicIssue + "-" + Artifact.SNAPSHOT_VERSION;
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            if (StringUtils.isNotBlank(version)) {
                // mvn versions:set -DnewVersion=...
                // -DgenerateBackupPoms=false
                mvnSetVersions(version, "On epic branch: ");

                String epicStartMessage = substituteInEpicMessage(commitMessages.getEpicStartMessage(), epicIssue);
                // git commit -a -m updating versions for epic branch
                gitCommit(epicStartMessage);
            }
        }

        if (installProject) {
            // mvn clean install
            mvnCleanInstall();
        }
    }

    private boolean validateEpicName(String anEpicName) {
        if (epicNamePattern == null) {
            return true;
        }
        return anEpicName.matches(epicNamePattern);
    }

}
