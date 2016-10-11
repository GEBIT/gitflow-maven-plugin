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
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.interpolation.ValueSource;
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

    /**
     * Additional version commands that can prompt for user input or be conditionally enabled.
     * @since 1.3.2
     */
    @Parameter
    protected GitFlowFeatureParameter[] additionalVersionCommands;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // fetch and check remote
            if (fetchRemote) {
                gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
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
            gitCreateAndCheckout(gitFlowConfig.getFeatureBranchPrefix()
                    + featureName, gitFlowConfig.getDevelopmentBranch());

            if (!skipFeatureVersion && !tychoBuild) {
                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();

                String version = null;
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                            currentVersion);
                    version = versionInfo.getReleaseVersionString() + "-"
                            + featureName + "-" + Artifact.SNAPSHOT_VERSION;
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }

                if (StringUtils.isNotBlank(version)) {
                    if (additionalVersionCommands != null) {
                        StringSearchInterpolator interpolator = new StringSearchInterpolator("@{", "}");
                        interpolator.addValueSource(new PropertiesBasedValueSource(getProject().getProperties()));
                        Properties properties = new Properties();
                        properties.setProperty("version", version);
                        properties.setProperty("currentVersion", currentVersion);
                        interpolator.addValueSource(new PropertiesBasedValueSource(properties));

                        // process additional commands/parameters
                        for (GitFlowFeatureParameter parameter : additionalVersionCommands) {
                            if (!parameter.isEnabled()) {
                                continue;
                            }
                            if (parameter.getPrompt() != null) {
                                try {
                                    String value = null;
                                    
                                    String prompt = interpolator.interpolate(parameter.getPrompt());
                                    String defaultValue = parameter.getDefaultValue() != null
                                            ? interpolator.interpolate(parameter.getDefaultValue()) : null;
                                    
                                    while (value == null) {
                                        if (defaultValue != null) {
                                            value = prompter.prompt(prompt, defaultValue);
                                        } else {
                                            value = prompter.prompt(prompt);
                                        }
                                    }
                                    
                                    parameter.setValue(value);
                                } catch (InterpolationException e) {
                                    throw new MojoFailureException("Failed to interpolate values", e);
                                } catch (PrompterException e) {
                                    throw new MojoFailureException("Failed to prompt for parameter", e);
                                }
                            }
                        }
                    }

                    // mvn versions:set -DnewVersion=...
                    // -DgenerateBackupPoms=false
                    mvnSetVersions(version);

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
    protected List<String> getCommandsAfterVersion() throws MojoFailureException {
        List<String> result = new ArrayList<String>();
        if (commandsAfterFeatureVersion.isEmpty()) {
            result.addAll(super.getCommandsAfterVersion());
        } else {
            result.add(commandsAfterFeatureVersion);
        }
        if (additionalVersionCommands != null) {
            for (GitFlowFeatureParameter parameter : additionalVersionCommands) {
                if (!parameter.isEnabled()) {
                    continue;
                }
                if (parameter.isEnabledByPrompt() && !"true".equals(parameter.getValue()) && !"yes".equals(parameter.getValue())) {
                    continue;
                }
                
                StringSearchInterpolator interpolator = new StringSearchInterpolator("@{", "}");
                interpolator.addValueSource(new PropertiesBasedValueSource(getProject().getProperties()));
                interpolator.addValueSource(new SingleResponseValueSource("value", parameter.getValue()));
                
                try {
                    result.add(interpolator.interpolate(parameter.getCommand()));
                } catch (InterpolationException e) {
                    throw new MojoFailureException("Failed to interpolate command", e);
                }
            }
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
