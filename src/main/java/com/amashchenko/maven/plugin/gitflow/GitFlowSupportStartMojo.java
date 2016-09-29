/*
 * Copyright 2014-2015 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.amashchenko.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow support start mojo.
 * 
 * @author Aleksandr Mashchenko
 */
@Mojo(name = "support-start", aggregator = true)
public class GitFlowSupportStartMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // need to be in master to get correct project version
            // git checkout master (or development is there is no master)
            String baseName = gitFlowConfig.isNoProduction() ? gitFlowConfig.getDevelopmentBranch()
                    : gitFlowConfig.getProductionBranch();
            gitCheckout(baseName);

            String defaultVersion = "1.0.0";

            // get current project version from pom
            String currentVersion = getCurrentProjectVersion();

            // get default support version
            try {
                try {
                    final String lastReleaseTag = gitLastReleaseTag(gitFlowConfig.getVersionTagPrefix());
                    currentVersion = lastReleaseTag.substring(gitFlowConfig.getVersionTagPrefix().length());
                    baseName = currentVersion;
                } catch (CommandLineException e) {
                    getLog().info("Failed to obtain latest release version.");
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                defaultVersion = versionInfo.getNextVersion().getSnapshotVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            String version = null;
            try {
                version = prompter.prompt("What is the support version? [" + currentVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(version)) {
                version = currentVersion;
            }

            // git for-each-ref refs/heads/support/...
            final String supportBranch = executeGitCommandReturn("for-each-ref",
                    "refs/heads/" + gitFlowConfig.getSupportBranchPrefix() + version);

            if (StringUtils.isNotBlank(supportBranch)) {
                throw new MojoFailureException("Support branch with that name already exists. Cannot start support.");
            }

            // git checkout -b support/... master
            gitCreateAndCheckout(gitFlowConfig.getSupportBranchPrefix() + version, baseName);

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(defaultVersion);

            // git commit -a -m updating poms for support
            gitCommit("updating poms for support");

            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
