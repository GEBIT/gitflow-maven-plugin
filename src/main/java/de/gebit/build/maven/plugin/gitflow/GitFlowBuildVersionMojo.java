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
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow build version mojo. Used to temporarily append a build
 * identifier to the version, e.g. for a CI build. Always operates on the
 * current branch.
 *
 * @author Erwin Tratar
 */
@Mojo(name = "build-version", aggregator = true)
public class GitFlowBuildVersionMojo extends AbstractGitFlowMojo {

    /**
     * Specifies the build version that is appended to the version using a `-'
     * separator.
     *
     * @since 1.3.0
     */
    @Parameter(property = "buildVersion", defaultValue = "${buildVersion}", required = false)
    private String buildVersion;

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // set git flow configuration
        initGitFlowConfig();

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        String baseVersion = null;
        // get default release version
        try {
            DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
            baseVersion = versionInfo.getReleaseVersionString();
            if (tychoBuild) {
                baseVersion = makeValidTychoVersion(baseVersion);
            }
        } catch (VersionParseException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(e);
            }
        }

        if (baseVersion == null) {
            throw new MojoFailureException("Cannot get default project version.");
        }

        if (settings.isInteractiveMode() && buildVersion == null) {
            try {
                buildVersion = prompter.prompt("What is build version? ");
            } catch (PrompterException e) {
                getLog().error(e);
            }
        }

        if (StringUtils.isBlank(buildVersion)) {
            getLog().info("No Build version set, aborting....");
            return;
        }

        // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
        if (tychoBuild) {
            // convert the base Version to OSGi
            if (StringUtils.countMatches(baseVersion, ".") < 4) {
                mvnSetVersions(baseVersion + "." + buildVersion, "");
            } else {
                mvnSetVersions(baseVersion + "_" + buildVersion, "");
            }
        } else {
            mvnSetVersions(baseVersion + "-" + buildVersion, "");
        }
    }
}
