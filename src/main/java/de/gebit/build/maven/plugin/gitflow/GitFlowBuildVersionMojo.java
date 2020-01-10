/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Update the build version in all POMs.
 * <p>
 * This is primarly used for CI builds to append a build identifier to the
 * version. Always operates on the current branch.
 * <p>
 * Example:
 * 
 * <pre>
 * mvn flow:build-version [-DbuildVersion=Ixxx]
 * </pre>
 *
 * @author Erwin Tratar
 */
@Mojo(name = GitFlowBuildVersionMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowBuildVersionMojo extends AbstractGitFlowMojo {

    static final String GOAL = "build-version";

    /**
     * Specifies the build version that is appended to the version using a `-'
     * separator.
     *
     * @since 1.3.0
     */
    @Parameter(property = "buildVersion", readonly = true)
    private String buildVersion;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting build version process");
        checkCentralBranchConfig();

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        String baseVersion;
        // get default release version
        try {
            DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
            baseVersion = versionInfo.getReleaseVersionString();
            if (tychoBuild) {
                baseVersion = makeValidTychoVersion(baseVersion);
            }
        } catch (VersionParseException e) {
            throw new GitFlowFailureException(
                    "Failed to calculate base version for build version. The project version '" + currentVersion
                            + "' can't be parsed.",
                    "Check the version of the project.\n"
                            + "'mvn flow:build-version' can not be used for projectes with invalid version.");
        }

        String buildVersionPrefix = baseVersion;
        if (tychoBuild) {
            // convert the base Version to OSGi
            if (StringUtils.countMatches(baseVersion, ".") < 3) {
                buildVersionPrefix += ".";
            } else {
                buildVersionPrefix += "_";
            }
        } else {
            buildVersionPrefix += "-";
        }

        buildVersion = getPrompter().promptRequiredParameterValue("What is build version? " + buildVersionPrefix,
                "buildVersion", buildVersion,
                new GitFlowFailureInfo("Property 'buildVersion' is required in non-interactive mode but was not set.",
                        "Specify a buildVersion or run in interactive mode.",
                        "'mvn flow:build-version -DbuildVersion=XXX -B' to predefine build version",
                        "'mvn flow:build-version' to run in interactive mode"));

        String version = buildVersionPrefix + buildVersion;
        getMavenLog().info("Setting version '" + version + "' for project on current branch...");
        // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
        mvnSetVersions(version, GitFlowAction.BUILD_VERSION, "");
        getMavenLog().info("Build version process finished");
    }
}
