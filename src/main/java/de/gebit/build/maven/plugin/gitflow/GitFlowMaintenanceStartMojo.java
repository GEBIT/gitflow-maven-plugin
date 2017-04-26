/*
 * Copyright 2014-2015 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.List;

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
 * The git flow maintenance branch start mojo.
 * 
 * @author Erwin Tratar
 * @since 1.5.3
 */
@Mojo(name = "maintenance-start", aggregator = true)
public class GitFlowMaintenanceStartMojo extends AbstractGitFlowMojo {

    /**
     * The release version to create the support branch from.
     * 
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${releaseVersion}", required = false)
    protected String releaseVersion;

    /**
     * The version used for the branch itself.
     * 
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${supportVersion}", required = false)
    protected String supportVersion;

    /**
     * The first version to set on the branch.
     * 
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${firstSupportVersion}", required = false)
    protected String firstSupportVersion;

    /**
     * Filter to query for relaese branches to limit the results.
     * 
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${releaseBranchFilter}", required = false)
    protected String releaseBranchFilter;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            String baseName = null;
            if (releaseVersion == null) {
                final String[] releaseTags = gitListReleaseTags(gitFlowConfig.getVersionTagPrefix(), releaseBranchFilter);
                List<String> numberedList = new ArrayList<String>();
                StringBuilder str = new StringBuilder("Release:").append(LS);
                str.append("0. <current commit>" + LS);
                numberedList.add(String.valueOf(0));
                for (int i = 0; i < releaseTags.length; i++) {
                    str.append((i + 1) + ". " + releaseTags[i] + LS);
                    numberedList.add(String.valueOf(i + 1));
                }
                str.append("Choose release create suppport branch or enter custom tag or release name");

                String releaseNumber = null;
                try {
                    while (StringUtils.isBlank(releaseNumber)) {
                        releaseNumber = prompter.prompt(str.toString(),numberedList);
                    }
                } catch (PrompterException e) {
                    getLog().error(e);
                }

                if (!StringUtils.isBlank(releaseNumber) && !releaseNumber.equals("0")) {
                    int num = Integer.parseInt(releaseNumber);
                    baseName = releaseTags[num - 1];
                } else {
                    // get off current commit
                    baseName = getCurrentCommit();
                }

            } else {
                if (gitTagExists(releaseVersion)) {
                    baseName = releaseVersion;
                } else {
                    if (gitTagExists(gitFlowConfig.getVersionTagPrefix() + releaseVersion)) {
                        baseName = gitFlowConfig.getVersionTagPrefix() + releaseVersion;
                    } else {
                        throw new MojoFailureException("Release '" + releaseVersion + "' does not exist.");
                    }
                }
            }

            if (StringUtils.isBlank(baseName)) {
                throw new MojoFailureException("Release to create support branch from is blank.");
            }

            // checkout the tag
            gitCheckout(baseName);

            // get current project version from pom
            String currentVersion = getCurrentProjectVersion();

            // get default support version
            String supportBranchVersion = supportVersion;
            String supportBranchFirstVersion = firstSupportVersion;
            
            if (firstSupportVersion == null && supportVersion == null) {
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    getLog().info("Version info: " +versionInfo);
                    final DefaultVersionInfo branchVersionInfo = new DefaultVersionInfo(versionInfo.getDigits().subList(0, versionInfo.getDigits().size()-1),
                            versionInfo.getAnnotation(), 
                            versionInfo.getAnnotationRevision(),
                            versionInfo.getBuildSpecifier() != null  ? "-" + versionInfo.getBuildSpecifier() : null, 
                            null, null, null);
                    getLog().info("Branch Version info: " +branchVersionInfo);
                    supportBranchVersion = branchVersionInfo.getReleaseVersionString();
                    supportBranchFirstVersion = versionInfo.getNextVersion().getSnapshotVersionString();
                } catch (VersionParseException e) {
                    throw new MojoFailureException("Failed to calculate support versions", e);
                }
            } else if (firstSupportVersion == null || supportVersion == null) {
                throw new MojoFailureException(
                        "Either both <supportVersion> and <firstSupportVersion> must be specified or none");
            }

            String branchVersion = null;
            try {
                branchVersion = prompter.prompt("What is the support version? [" + supportBranchVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(branchVersion)) {
                branchVersion = supportBranchVersion;
            }

            String branchFirstVersion = null;
            try {
                branchFirstVersion = prompter.prompt("What is the first version on the support branch? [" + supportBranchFirstVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }
            
            if (StringUtils.isBlank(branchFirstVersion)) {
                branchFirstVersion = supportBranchFirstVersion;
            }

            // git for-each-ref refs/heads/support/...
            final boolean supportBranchExists = gitBranchExists(gitFlowConfig.getSupportBranchPrefix() + branchVersion);
            if (supportBranchExists) {
                throw new MojoFailureException("Support branch with that name already exists. Cannot start support.");
            }

            // git checkout -b support/... master
            gitCreateAndCheckout(gitFlowConfig.getSupportBranchPrefix() + branchVersion, baseName);

            if (!currentVersion.equals(branchFirstVersion)) {
                // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
                mvnSetVersions(branchFirstVersion, "On support branch: ");
    
                // git commit -a -m updating poms for support
                gitCommit(commitMessages.getSupportStartMessage());
            }

            if (pushRemote)  {
                gitPush(gitFlowConfig.getSupportBranchPrefix() + branchVersion, false);
            }
            
            if (installProject) {
                // mvn clean install
                mvnCleanInstall();
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
