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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow hotfix start mojo.
 *
 * @author Aleksandr Mashchenko
 *
 */
@Mojo(name = GitFlowHotfixStartMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowHotfixStartMojo extends AbstractGitFlowMojo {

    static final String GOAL = "hotfix-start";

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check uncommitted changes
        checkUncommittedChanges();

        // need to be in master to get correct project version
        // git checkout master
        gitCheckout(gitFlowConfig.isNoProduction() ? gitFlowConfig.getDevelopmentBranch()
                : gitFlowConfig.getProductionBranch());

        // fetch and check remote
        gitFetchRemoteAndCompare(gitFlowConfig.isNoProduction() ? gitFlowConfig.getDevelopmentBranch()
                : gitFlowConfig.getProductionBranch());

        // get current project version from pom
        final String currentVersion = getCurrentProjectVersion();

        String defaultVersion = null;
        // get default hotfix version
        try {
            final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
            defaultVersion = versionInfo.getNextVersion().getReleaseVersionString();

            if (tychoBuild && ArtifactUtils.isSnapshot(currentVersion)) {
                defaultVersion += "-" + Artifact.SNAPSHOT_VERSION;
            }
        } catch (VersionParseException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(e);
            }
        }

        if (defaultVersion == null) {
            throw new MojoFailureException("Cannot get default project version.");
        }

        String version = null;
        try {
            version = prompter.prompt("What is the hotfix version? [" + defaultVersion + "]");
        } catch (PrompterException e) {
            getLog().error(e);
        }

        if (StringUtils.isBlank(version)) {
            version = defaultVersion;
        }

        // git for-each-ref refs/heads/hotfix/...
        if (gitBranchExists(gitFlowConfig.getHotfixBranchPrefix() + version)) {
            throw new MojoFailureException("Hotfix branch with that name already exists. Cannot start hotfix.");
        }

        // git checkout -b hotfix/... master
        gitCreateAndCheckout(gitFlowConfig.getHotfixBranchPrefix() + version, gitFlowConfig.isNoProduction()
                ? gitFlowConfig.getDevelopmentBranch() : gitFlowConfig.getProductionBranch());

        // execute if version changed
        if (versionless || !version.equals(currentVersion)) {
            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(version, GitFlowAction.HOTFIX_START, null, null, commitMessages.getHotfixStartMessage());
        }

        if (installProject) {
            // mvn clean install
            mvnCleanInstall();
        }
    }
}
