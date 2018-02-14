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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow feature start mojo.
 *
 * @author Aleksandr Mashchenko
 *
 */
@Mojo(name = "feature-start", aggregator = true)
public class GitFlowFeatureStartMojo extends AbstractGitFlowMojo {

    /**
     * Whether to skip changing project version. Default is <code>false</code>
     * (the feature name will be appended to project version).
     *
     * @since 1.0.5
     */
    @Parameter(property = "skipFeatureVersion", defaultValue = "false")
    private boolean skipFeatureVersion = false;

    /**
     * Additional maven commands/goals after the feature version has been updated. Will be committed together with the version
     * change. Can contain an {@literal @}{version} placeholder which will be replaced with the new version before
     * execution. If empty the <code>commandsAfterVersion</code> property is used.
     *
     * @since 1.3.2
     */
    @Parameter(property = "commandsAfterFeatureVersion", defaultValue = "")
    protected String commandsAfterFeatureVersion;

    /**
     * A natual language description of the <code>featureNamePattern</code> which is used to print an error message.
     * If not specified the pattern is printed in the error message as is, which can be hard to understand.
     *
     * @since 1.3.1
     */
    @Parameter(property = "featureNamePatternDescription", required = false)
    protected String featureNamePatternDescription;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // fetch and check remote
            String currentBranch = gitCurrentBranch();
            String baseBranch = currentBranch.startsWith(gitFlowConfig.getMaintenanceBranchPrefix())
                    ? currentBranch : gitFlowConfig.getDevelopmentBranch();

            // use integration branch?
            final String integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + baseBranch;
            if (!gitBranchExists(integrationBranch) || fetchRemote) {
                // first try to fetch it
                gitFetchRemoteAndCompare(integrationBranch, new Callable<Void>() {

                    @Override
                    public Void call() throws Exception {
                        gitUpdateRef(integrationBranch, "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + integrationBranch);
                        return null;
                    }
                });
            }
            if (fetchRemote) {
                gitFetchRemoteAndCompare(baseBranch);
            }
            if (gitBranchExists(integrationBranch)) {
                boolean useIntegrationBranch = true;
                if (getCurrentCommit(integrationBranch) != getCurrentCommit(baseBranch)) {
                    if (settings.isInteractiveMode()) {
                        try {
                            String answer = prompter.prompt("The current commit on " + baseBranch
                                    + " is not integrated. Create a branch off the last integrated commit ("
                                    + integrationBranch + ")?", Arrays.asList("y", "n"), "y");
                            if (!"y".equalsIgnoreCase(answer)) {
                                useIntegrationBranch = false;
                            }
                        } catch (PrompterException e) {
                            getLog().error(e);
                        }
                    }
                }
                if (useIntegrationBranch) {
                    getLog().info("Using integration branch '" + integrationBranch + "'");
                    baseBranch = integrationBranch;

                    String branchPoint = gitBranchPoint(integrationBranch, baseBranch);
                    if (StringUtils.isEmpty(branchPoint)) {
                        throw new MojoFailureException("Failed to determine branch base of '" + integrationBranch
                                + "' in respect to '" + baseBranch + "'.");
                    }
                }
            }

            String featureName = null;
            try {
                while (StringUtils.isBlank(featureName)) {
                    featureName = prompter
                            .prompt("What is a name of feature branch? "
                                    + gitFlowConfig.getFeatureBranchPrefix());
                    if (!validateFeatureName(featureName)) {
                        if (featureNamePatternDescription != null) {
                            prompter.showMessage(featureNamePatternDescription);
                        } else {
                            prompter.showMessage("Feature name does not match the required pattern: " + featureNamePattern);
                        }
                        featureName = null;
                    }
                }
            } catch (PrompterException e) {
                getLog().error(e);
            }

            featureName = StringUtils.deleteWhitespace(featureName);

            // git for-each-ref refs/heads/feature/...
            final String featureBranch = gitFindBranch(gitFlowConfig
                    .getFeatureBranchPrefix() + featureName);

            if (StringUtils.isNotBlank(featureBranch)) {
                throw new MojoFailureException(
                        "Feature branch with that name already exists. Cannot start feature.");
            }
            final String featureStartMessage = substituteInMessage(commitMessages.getFeatureStartMessage(),
                    gitFlowConfig.getFeatureBranchPrefix() + featureName);

            // git checkout -b ... develop
            gitCreateAndCheckout(gitFlowConfig.getFeatureBranchPrefix() + featureName, baseBranch);

            if (!skipFeatureVersion && !tychoBuild) {
                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();

                String version = null;
                try {
                    String featureIssue = featureName;
                    if (featureNamePattern != null) {
                        // extract the issue number only
                        Matcher m = Pattern.compile(featureNamePattern).matcher(featureName);
                        if (!m.matches()) {
                            // retry with prefixe removed
                            if (featureName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
                                m = Pattern.compile(featureNamePattern).matcher(
                                        featureName.substring(gitFlowConfig.getFeatureBranchPrefix().length()));
                            }
                            if (!m.matches()) {
                                getLog().warn(
                                        "Feature branch does not conform to <featureNamePattern> specified, cannot extract issue number.");
                            }
                        } else if (m.groupCount() == 0){
                            getLog().warn(
                                    "Feature branch conforms to <featureNamePattern>, but ther is no matching group to extract the issue number.");
                        } else {
                            featureIssue = m.group(1);
                        }
                    }

                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                            currentVersion);
                    version = versionInfo.getReleaseVersionString() + "-"
                            + featureIssue + "-" + Artifact.SNAPSHOT_VERSION;
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }

                if (StringUtils.isNotBlank(version)) {
                    // mvn versions:set -DnewVersion=...
                    // -DgenerateBackupPoms=false
                    mvnSetVersions(version, "On feature branch: ");

                    // git commit -a -m updating versions for feature branch
                    gitCommit(featureStartMessage);
                }
            }

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }

    /**
     * If {@link #commandsAfterFeatureVersion} is set use it to replace the {@link AbstractGitFlowMojo#commandsAfterVersion}.
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
     * Check whether the given feature name matches the required pattern, if any.
     */
    protected boolean validateFeatureName(String featureName) {
        if (featureNamePattern == null) {
            return true;
        }
        return featureName.matches(featureNamePattern);
    }
}
