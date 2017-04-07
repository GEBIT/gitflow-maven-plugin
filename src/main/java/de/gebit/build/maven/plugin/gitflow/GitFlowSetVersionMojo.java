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
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow build version mojo. Used to explicitly set the version in all projects.
 * 
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
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            if (settings.isInteractiveMode() && newVersion == null) {
                // get current project version from pom
                final String currentVersion = getCurrentProjectVersion();
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    newVersion = versionInfo.getNextVersion().getReleaseVersionString();
                } catch (VersionParseException e) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
                
                if (newVersion == null) {
                    throw new MojoFailureException(
                            "Cannot get default project version.");
                }
                
                try {
                    newVersion = prompter.prompt("What is new version?", newVersion);
                } catch (PrompterException e) {
                    getLog().error(e);
                }
            }

            if (StringUtils.isBlank(newVersion)) {
                throw new MojoFailureException("No new version set, aborting....");
            }

            

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(newVersion, true);

        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
