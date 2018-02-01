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

import java.io.File;

import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.wagon.CommandExecutor;
import org.apache.maven.wagon.Streams;
import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * This mojo can be used to verify everything is set up correctly:
 * <ul>
 * <li>Commandline git available</li>
 * <li>Access to remote repository without password prompt</li>
 * <li>Commandline ssh available</li>
 * <li>Access to site repository without password prompt</li>
 * <li>Commandline ssh available</li>
 * <li>Access to site repository without password prompt</li>
 * 
 * </ul>
 * 
 * @author Erwin Tratar
 * @since 1.5.6
 */
@Mojo(name = "check", aggregator = true, defaultPhase =LifecyclePhase.VALIDATE)
public class GitFlowCheckMojo extends AbstractGitFlowMojo {
    @Component(role = WagonManager.class)
    private WagonManager wagonManager;
    
    @Component(role = ArtifactRepositoryLayout.class)
    private ArtifactRepositoryLayout artifactRepositoryLayout;
    
    @Parameter(required = false, defaultValue = "${project.distributionManagement.repository.id}|${project.distributionManagement.repository.url},${project.distributionManagement.snapshotRepository.id}|${project.distributionManagement.snapshotRepository.url}")
    private Repository[] deploymentRepositories;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check for git executable
            boolean tempFailures = false;
            getLog().info("Check if 'git' command is available...");
            if (!isGitAvailable()) {
                getLog().error("");
                getLog().error("git commandline was not found. Please install it, e.g. from https://git-scm.com/downloads");
                getLog().error("");
                tempFailures = true;
            }
            getLog().info("   [OK]");

            // check if we have read access to the configured repository using passwordless SSH
            getLog().info("Check if remote repository is accessible...");
            try {
                hasRemoteBranch(gitFlowConfig.getDevelopmentBranch());
                getLog().info("   [OK]");
            } catch (MojoFailureException ex) {
                getLog().error("");
                getLog().error("Failed to access the remote git repository: " + ex.getMessage());
                if (ex.getMessage().contains("password)")) {
                    getLog().error(
                            "The repository must be accessible with a SSH key (no username/password!). If not yet set up, please send your public key to the Administration to link it to your account.");
                }
                getLog().error("");
                tempFailures = true;
            }

            // now check repository write permissions by pushing the current commit to a new remote branch
            getLog().info("Check if remote repository is writable...");
            try {
                checkGitWriteable();
                getLog().info("   [OK]");
            } catch (MojoFailureException ex) {
                getLog().error("");
                getLog().error("Failed to write to the remote git repository: " + ex.getMessage());
                getLog().error("");
                tempFailures = true;
            }
            
            if (deploymentRepositories != null) {
                for (Repository repository : deploymentRepositories) {
                    tempFailures &= checkRepository("deployment repository", repository.getId(), repository.getUrl(), repository.isMandatory());
                }
            }

            // new we need to check whether we can publish the site
            if (getProject().getDistributionManagement().getSite() != null) {
                tempFailures &= checkRepository("site publishing", getProject().getDistributionManagement().getSite().getId(),
                        getProject().getDistributionManagement().getSite().getUrl(), true);
            }
            if (tempFailures) {
                throw new MojoExecutionException(
                        "Your environment is not configured correctly (see above for details). After correcting the errors please re-run to check again.");
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }

    /**
     * Check whether deployment on the configured repository works. Assumes standard gebit-build repository properties
     * @param type
     * @param mandatory
     * @return
     */
    private boolean checkRepository(String type, String id, String url, boolean mandatory) {
        getLog().info("Checking " + type + " access for " + id + " at " + url);

        try {
            ArtifactRepository tempArtifactRepository = new MavenArtifactRepository(id, url, artifactRepositoryLayout, null, null);
            Wagon tempWagon = wagonManager.getWagon(tempArtifactRepository.getProtocol());
            DefaultArtifactRepository tempRepository = new DefaultArtifactRepository(id, url, artifactRepositoryLayout, false);
            tempWagon.connect(tempRepository, wagonManager.getAuthenticationInfo(id));
            try {
                File tempFile = File.createTempFile(System.getProperty("user.name").toLowerCase().replace(' ', '-') + "-access-test-", "");
                tempFile.deleteOnExit();
                String tempRemoteFile = tempArtifactRepository.getBasedir() + ".temp/" + tempFile.getName();
                getLog().debug("   uploading to " + tempRemoteFile);
                tempWagon.put(tempFile, tempRemoteFile);
                if (tempWagon instanceof CommandExecutor) {
                    // try to cleanup
                    String hostFile = tempWagon.getRepository().getBasedir() + tempRemoteFile;
                    getLog().debug("   trying cleanup " + hostFile);
                    Streams command = ((CommandExecutor) tempWagon).executeCommand("rm " + hostFile, true);
                    if (!command.getOut().trim().isEmpty()) {
                        getLog().debug("   " + command.getOut());
                    }
                    if (!command.getErr().trim().isEmpty()) {
                        getLog().warn("   " + command.getErr());
                    }
                    
                    // try to cleanup directory
                    getLog().debug("   trying cleanup directory");
                    command = ((CommandExecutor) tempWagon).executeCommand("rmdir "
                            + tempWagon.getRepository().getBasedir() + tempArtifactRepository.getBasedir() + ".temp",
                            true);
                    if (!command.getOut().trim().isEmpty()) {
                        getLog().debug("   " + command.getOut());
                    }
                    if (!command.getErr().trim().isEmpty()) {
                        getLog().warn("   " + command.getErr());
                    }
                }
            } finally {
                tempWagon.disconnect();
            }
            getLog().info("   [OK]");
            return true;
        } catch (Exception ex) {
            if (mandatory) {
                getLog().error("");
                getLog().error("Failed to write to the remote git repository: " + ex.getMessage());
                getLog().error("");
                return false;
            } else {
                getLog().warn("");
                getLog().warn("Failed to write to the remote git repository: " + ex.getMessage());
                getLog().warn("");
                getLog().info("   [OK]");
                return true;
            }
        }
    }
}
