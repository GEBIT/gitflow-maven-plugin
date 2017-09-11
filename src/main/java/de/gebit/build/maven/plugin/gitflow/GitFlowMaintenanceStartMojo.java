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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import edu.emory.mathcs.backport.java.util.Arrays;

/**
 * The git flow maintenance branch start mojo.
 * 
 * @author Erwin Tratar
 * @since 1.5.3
 */
@Mojo(name = "maintenance-start", aggregator = true)
public class GitFlowMaintenanceStartMojo extends AbstractGitFlowMojo {

    /**
     * The release version to create the maintenance branch from.
     * 
     * @since 1.3.0
     */
    @Parameter(defaultValue = "${releaseVersion}", required = false)
    protected String releaseVersion;

    /**
     * The version used for the branch itself.
     * 
     * @since 1.3.0
     * @deprecated use {@link #maintenanceVersion}
     */
    @Parameter(defaultValue = "${supportVersion}", required = false)
    protected String supportVersion;
    
    /**
     * The version used for the branch itself.
     * 
     * @since 1.5.3
     */
    @Parameter(defaultValue = "${maintenanceVersion}", required = false)
    protected String maintenanceVersion;

    /**
     * The first version to set on the branch.
     * 
     * @since 1.3.0
     * @deprecated use {@link #firstMaintenanceVersion}
     */
    @Parameter(defaultValue = "${firstSupportVersion}", required = false)
    protected String firstSupportVersion;
    
    /**
     * The first version to set on the branch.
     * 
     * @since 1.5.3
     */
    @Parameter(defaultValue = "${firstMaintenanceVersion}", required = false)
    protected String firstMaintenanceVersion;

    /**
     * Filter to query for release branches to limit the results. The value is a shell glob pattern if not starting
     * with a ^ and as a regular expression otherwise. 
     * 
     * @since 1.3.0
     * @since 1.5.9
     */
    @Parameter(defaultValue = "${releaseBranchFilter}", required = false)
    protected String releaseBranchFilter;

    /**
     * Number of release versions to offer. If not specified the selection is unlimited. The order is from highest to
     * lowest.
     * 
     * @since 1.5.9
     */
    @Parameter(defaultValue = "${releaseVersionLimit}", required = false)
    protected Integer releaseVersionLimit;

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
                boolean regexMode = releaseBranchFilter != null && releaseBranchFilter.startsWith("^");
                List<String> releaseTags = gitListReleaseTags(gitFlowConfig.getVersionTagPrefix(), regexMode ? null : releaseBranchFilter);

                // post process selection
                if (regexMode) {
                    Pattern versionFilter = Pattern.compile("^\\Q" + gitFlowConfig.getVersionTagPrefix() + "\\E" + releaseBranchFilter.substring(1));
                    for (Iterator<String> it = releaseTags.iterator(); it.hasNext(); ) {
                        if (!versionFilter.matcher(it.next()).matches()) {
                            it.remove();
                        }
                    }
                }
                if (releaseVersionLimit != null && releaseTags.size() > releaseVersionLimit) {
                    releaseTags = releaseTags.subList(0, releaseVersionLimit);
                }
                List<String> numberedList = new ArrayList<String>();
                StringBuilder str = new StringBuilder("Release:").append(LS);
                str.append("0. <current commit>" + LS);
                numberedList.add(String.valueOf(0));
                for (int i = 0; i < releaseTags.size(); i++) {
                    str.append((i + 1) + ". " + releaseTags.get(i) + LS);
                    numberedList.add(String.valueOf(i + 1));
                }

                // add explicit choice
                str.append("T. <prompt for explicit tag name>" + LS);
                numberedList.add("T");

                str.append("Choose release to create the maintenance branch from or enter a custom tag or release name");

                String releaseNumber = null;
                try {
                    while (StringUtils.isBlank(releaseNumber)) {
                        releaseNumber = prompter.prompt(str.toString(),numberedList);
                    }
                } catch (PrompterException e) {
                    getLog().error(e);
                }

                if (StringUtils.isBlank(releaseNumber) || "0".equals(releaseNumber)) {
                    // get off current commit
                    baseName = getCurrentCommit();
                } else if ("T".equals(releaseNumber)) {
                    // prompt for tag name
                    try {
                        baseName = prompter.prompt("Enter explicit tag name");
                    } catch (PrompterException e) {
                        getLog().error(e);
                    }
                } else {
                    int num = Integer.parseInt(releaseNumber);
                    baseName = releaseTags.get(num - 1);
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
                throw new MojoFailureException("Release to create maintenance branch from is blank.");
            }

            // checkout the tag
            gitCheckout(baseName);

            // get current project version from pom
            String currentVersion = getCurrentProjectVersion();

            // get default maintenance version
            String maintenanceBranchVersion = maintenanceVersion != null ? maintenanceVersion : supportVersion;
            String maintenanceBranchFirstVersion = firstMaintenanceVersion != null ? firstMaintenanceVersion : firstSupportVersion;
            
            if (maintenanceBranchVersion == null && firstMaintenanceVersion == null) {
                try {
                    final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                    getLog().info("Version info: " +versionInfo);
                    final DefaultVersionInfo branchVersionInfo = new DefaultVersionInfo(versionInfo.getDigits().subList(0, versionInfo.getDigits().size()-1),
                            versionInfo.getAnnotation(), 
                            versionInfo.getAnnotationRevision(),
                            versionInfo.getBuildSpecifier() != null  ? "-" + versionInfo.getBuildSpecifier() : null, 
                            null, null, null);
                    getLog().info("Branch Version info: " +branchVersionInfo);
                    maintenanceBranchVersion = branchVersionInfo.getReleaseVersionString();
                    maintenanceBranchFirstVersion = versionInfo.getNextVersion().getSnapshotVersionString();
                } catch (VersionParseException e) {
                    throw new MojoFailureException("Failed to calculate maintenance versions", e);
                }
            } else if (maintenanceBranchVersion == null || firstMaintenanceVersion == null) {
                throw new MojoFailureException(
                        "Either both <maintenanceVersion> and <firstMaintenanceVersion> must be specified or none");
            }

            String branchVersion = null;
            try {
                branchVersion = prompter.prompt("What is the maintenance version? [" + maintenanceBranchVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(branchVersion)) {
                branchVersion = maintenanceBranchVersion;
            }

            String branchFirstVersion = null;
            try {
                branchFirstVersion = prompter.prompt("What is the first version on the maintenance branch? [" + maintenanceBranchFirstVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }
            
            if (StringUtils.isBlank(branchFirstVersion)) {
                branchFirstVersion = maintenanceBranchFirstVersion;
            }

            // git for-each-ref refs/heads/maintenance/...
            final boolean maintenanceBranchExists = gitBranchExists(gitFlowConfig.getMaintenanceBranchPrefix() + branchVersion);
            if (maintenanceBranchExists) {
                throw new MojoFailureException("Maintenance branch with that name already exists. Cannot start maintenance.");
            }

            // git checkout -b maintenance/... master
            gitCreateAndCheckout(gitFlowConfig.getMaintenanceBranchPrefix() + branchVersion, baseName);

            if (!currentVersion.equals(branchFirstVersion)) {
                // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
                mvnSetVersions(branchFirstVersion, "On maintenance branch: ");
    
                // git commit -a -m updating poms for maintenance
                gitCommit(commitMessages.getMaintenanceStartMessage());
            }

            if (pushRemote)  {
                gitPush(gitFlowConfig.getMaintenanceBranchPrefix() + branchVersion, false);
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