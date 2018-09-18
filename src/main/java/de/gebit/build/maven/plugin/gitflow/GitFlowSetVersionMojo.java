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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Update the version(s) in all POMs.
 * <p>
 * Updates the version in all <code>pom.xml</code> files to the specified version. If no version is specified, you will
 * be interactively asked for it.
 * <p>
 * Example:
 * <pre>
 * mvn flow:set-version [-⁠DnewVersion=XXXX]
 * </pre>
 * @author Erwin Tratar
 */
@Mojo(name = "set-version", aggregator = true)
public class GitFlowSetVersionMojo extends AbstractGitFlowMojo {

    /**
     * Specifies a specific (complete) version to update the project to.
     *
     * @since 1.3.0
     */
    @Parameter(property = "newVersion", defaultValue = "${newVersion}", required = false)
    private String newVersion;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting set version process");
        // set git flow configuration
        initGitFlowConfig();
        checkCentralBranchConfig();

        if (newVersion == null) {
            if (settings.isInteractiveMode()) {
                // get current project version from pom
                String currentVersion = getCurrentProjectVersion();
                try {
                    DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    newVersion = versionInfo.getNextVersion().getReleaseVersionString();
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
                newVersion = getPrompter().promptValue("What is the new version?", newVersion);
            } else {
                throw new GitFlowFailureException(
                        "Property 'newVersion' is required in non-interactive mode but was not set.",
                        "Specify a new version or run in interactive mode.",
                        "'mvn flow:set-version -DnewVersion=X.Y.Z-SNAPSHOT -B' to predifine new version",
                        "'mvn flow:set-version' to run in interactive mode");
            }
        }

        getMavenLog().info("Setting version '" + newVersion + "' for project on current branch...");
        // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
        mvnSetVersions(newVersion, "");
        getMavenLog().info("Set version process finished");
    }
}
