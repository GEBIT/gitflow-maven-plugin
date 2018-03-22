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
import java.util.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature start mojo.
 *
 * @author Aleksandr Mashchenko
 *
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

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        initGitFlowConfig();

        checkUncommittedChanges();

        String currentBranch = gitCurrentBranch();
        String baseBranch = currentBranch;
        if (!isMaintenanceBranch(baseBranch) && !isEpicBranch(baseBranch)) {
            baseBranch = gitFlowConfig.getDevelopmentBranch();
            if (!currentBranch.equals(baseBranch)) {
                boolean confirmed = getPrompter().promptConfirmation("Feature branch will be started not from current "
                        + "branch but will be based off branch '" + baseBranch + "'. Continue?", true, true);
                if (!confirmed) {
                    throw new GitFlowFailureException("Feature start process aborted by user.", null);
                }
            }
        }

        // use integration branch?
        String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
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
                                        + "' is invalid. It does not match the required pattern: " + featureNamePattern;
                            }
                            return new ValidationResult(invalidMessage);
                        } else {
                            return ValidationResult.VALID;
                        }
                    }
                },
                new GitFlowFailureInfo("Property 'featureName' is required in non-interactive mode but was not set.",
                        "Specify a featureName or run in interactive mode.",
                        "'mvn flow:feature-start -DfeatureName=XXX -B'", "'mvn flow:feature-start'"));

        featureName = StringUtils.deleteWhitespace(featureName);

        String featureBranchName = gitFlowConfig.getFeatureBranchPrefix() + featureName;
        String featureBranchInfo = gitFindBranch(featureBranchName);
        if (StringUtils.isNotBlank(featureBranchInfo)) {
            throw new GitFlowFailureException("Feature branch '" + featureBranchName + "' already exists.",
                    "Either checkout the existing feature branch or start a new feature with another name.",
                    "'git checkout " + featureBranchName + "' to checkout the feature branch",
                    "'mvn flow:feature-start' to run again and specify another feature name");
        }
        String remoteFeatureBranchInfo = gitFindRemoteBranch(featureBranchName);
        if (StringUtils.isNotBlank(remoteFeatureBranchInfo)) {
            throw new GitFlowFailureException(
                    "Remote feature branch '" + featureBranchName + "' already exists on the remote '"
                            + gitFlowConfig.getOrigin() + "'.",
                    "Either checkout the existing feature branch or start a new feature with another name.",
                    "'git checkout " + featureBranchName + "' to checkout the feature branch",
                    "'mvn flow:feature-start' to run again and specify another feature name");
        }

        String featureIssue = extractIssueNumberFromFeatureName(featureName);
        String featureStartMessage = substituteInFeatureMessage(commitMessages.getFeatureStartMessage(), featureIssue);

        gitCreateAndCheckout(featureBranchName, baseBranch);

        if (!skipFeatureVersion && !tychoBuild) {
            String currentVersion = getCurrentProjectVersion();
            String version = currentVersion;
            if (isEpicBranch(baseBranch)) {
                version = removeEpicIssueFromVersion(version, baseBranch);
            }
            version = insertSuffixInVersion(version, featureIssue);
            if (!currentVersion.equals(version)) {
                mvnSetVersions(version, "On feature branch: ");
                gitCommit(featureStartMessage);
            }
        }

        if (installProject) {
            mvnCleanInstall();
        }
    }

    private String removeEpicIssueFromVersion(String version, String epicBranch) {
        String epicIssueNumber = extractIssueNumberFromEpicBranchName(epicBranch);
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
    protected List<String> getCommandsAfterVersion(boolean processAdditionalCommands) throws MojoFailureException {
        if (StringUtils.isEmpty(commandsAfterFeatureVersion)) {
            return super.getCommandsAfterVersion(processAdditionalCommands);
        }
        List<String> result = new ArrayList<String>();
        result.add(commandsAfterFeatureVersion);
        if (processAdditionalCommands) {
            result.addAll(getAdditionalVersionCommands());
        }
        return result;
    }

    /**
     * Check whether the given feature name matches the required pattern, if
     * any.
     */
    protected boolean validateFeatureName(String featureName) {
        if (featureNamePattern == null) {
            return true;
        }
        return featureName.matches(featureNamePattern);
    }
}
