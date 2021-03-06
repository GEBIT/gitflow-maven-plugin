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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow hotfix finish mojo.
 *
 * @author Aleksandr Mashchenko
 *
 */
@Mojo(name = GitFlowHotfixFinishMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowHotfixFinishMojo extends AbstractGitFlowMojo {

    static final String GOAL = "hotfix-finish";

    /** Whether to skip tagging the hotfix in Git. */
    @Parameter(property = "skipTag", defaultValue = "false")
    private boolean skipTag = false;

    /** Whether to keep hotfix branch after finish. */
    @Parameter(property = "keepBranch", defaultValue = "false")
    private boolean keepBranch = false;

    /**
     * Whether to skip calling Maven test goal before merging the branch.
     *
     * @since 1.0.5
     */
    @Parameter(property = "flow.skipTestProject", defaultValue = "false")
    private boolean skipTestProject = false;
    
    /**
     * Whether to call Maven install goal after hotfix finish. By default the
     * value of <code>installProject</code> parameter
     * (<code>flow.installProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.installProjectOnHotfixFinish")
    private Boolean installProjectOnHotfixFinish;
    
    /**
     * Whether to skip calling Maven test goal before merging the hotfix branch
     * into base branch. By default the value of <code>skipTestProject</code>
     * parameter (<code>flow.skipTestProject</code> property) is used.
     *
     * @since 2.2.0
     */
    @Parameter(property = "flow.skipTestProjectOnHotfixFinish")
    private Boolean skipTestProjectOnHotfixFinish;
    
    /**
     * Maven goals (separated by space) to be used after hotfix finish. By
     * default the value of <code>installProjectGoals</code> parameter
     * (<code>flow.installProjectGoals</code> property) is used.
     *
     * @since 2.3.1
     */
    @Parameter(property = "flow.installProjectGoalsOnHotfixFinish")
    private String installProjectGoalsOnHotfixFinish;

    /**
     * Maven goals (separated by space) to be used before merging the hotfix
     * branch into base branch. By default the value of
     * <code>testProjectGoals</code> parameter
     * (<code>flow.testProjectGoals</code> property) is used.
     *
     * @since 2.3.1
     */
    @Parameter(property = "flow.testProjectGoalsOnHotfixFinish")
    private String testProjectGoalsOnHotfixFinish;

    @Override
    protected String getCurrentGoal() {
        return GOAL;
    }

    /** {@inheritDoc} */
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        // check uncommitted changes
        checkUncommittedChanges();

        // git for-each-ref --format='%(refname:short)' refs/heads/hotfix/*
        final String hotfixBranches = gitFindBranches(gitFlowConfig.getHotfixBranchPrefix(), false);

        if (StringUtils.isBlank(hotfixBranches)) {
            throw new MojoFailureException("There is no hotfix branches.");
        }

        // fetch and check remote
        gitFetchRemoteAndCompare(gitFlowConfig.getDevelopmentBranch());
        if (!gitFlowConfig.isNoProduction()) {
            gitFetchRemoteAndCompare(gitFlowConfig.getProductionBranch());
        }

        String[] branches = hotfixBranches.split("\\r?\\n");

        List<String> numberedList = new ArrayList<String>();
        StringBuilder str = new StringBuilder("Hotfix branches:").append(LS);
        for (int i = 0; i < branches.length; i++) {
            str.append((i + 1) + ". " + branches[i] + LS);
            numberedList.add(String.valueOf(i + 1));
        }
        str.append("Choose hotfix branch to finish");

        String hotfixNumber = null;
        try {
            while (StringUtils.isBlank(hotfixNumber)) {
                hotfixNumber = prompter.prompt(str.toString(), numberedList);
            }
        } catch (PrompterException e) {
            getLog().error(e);
        }

        String hotfixBranchName = null;
        if (hotfixNumber != null) {
            int num = Integer.parseInt(hotfixNumber);
            hotfixBranchName = branches[num - 1];
        }

        if (StringUtils.isBlank(hotfixBranchName)) {
            throw new MojoFailureException("Hotfix branch name to finish is blank.");
        }

        if (!isSkipTestProject()) {
            // git checkout hotfix/...
            gitCheckout(hotfixBranchName);

            mvnCleanVerify();
        }

        // git checkout master
        gitCheckout(gitFlowConfig.isNoProduction() ? gitFlowConfig.getDevelopmentBranch()
                : gitFlowConfig.getProductionBranch());

        // git merge --no-ff hotfix/...
        gitMergeNoff(hotfixBranchName);

        if (!skipTag) {
            String tagVersion = getCurrentProjectVersion();
            if (tychoBuild && ArtifactUtils.isSnapshot(tagVersion)) {
                tagVersion = tagVersion.replace("-" + Artifact.SNAPSHOT_VERSION, "");
            }

            // git tag -a ...
            gitTag(gitFlowConfig.getVersionTagPrefix() + tagVersion, commitMessages.getTagHotfixMessage());
        }

        // check whether release branch exists
        // git for-each-ref --count=1 --format="%(refname:short)"
        // refs/heads/release/*
        final String releaseBranch = gitFindBranches(gitFlowConfig.getReleaseBranchPrefix(), true);

        // if release branch exists merge hotfix changes into it
        if (StringUtils.isNotBlank(releaseBranch)) {
            // git checkout release
            gitCheckout(releaseBranch);
            // git merge --no-ff hotfix/...
            gitMergeNoff(hotfixBranchName);
        } else {
            if (!gitFlowConfig.isNoProduction()) {
                // git checkout develop
                gitCheckout(gitFlowConfig.getDevelopmentBranch());

                // git merge --no-ff hotfix/...
                gitMergeNoff(hotfixBranchName);
            }

            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            String nextSnapshotVersion = null;
            // get next snapshot version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(currentVersion);
                nextSnapshotVersion = versionInfo.getNextVersion().getSnapshotVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            if (StringUtils.isBlank(nextSnapshotVersion)) {
                throw new MojoFailureException("Next snapshot version is blank.");
            }

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            mvnSetVersions(nextSnapshotVersion, GitFlowAction.HOTFIX_FINISH, null);

            // git commit -a -m updating for next development version
            if (executeGitHasUncommitted()) {
                gitCommit(commitMessages.getHotfixFinishMessage());
            }
        }

        if (isInstallProject()) {
            // mvn clean install
            mvnCleanInstall();
        }

        // first push modified branches
        if (pushRemote) {
            // if no release branch
            if (StringUtils.isBlank(releaseBranch) && !gitFlowConfig.isNoProduction()) {
                gitPush(gitFlowConfig.getProductionBranch(), !skipTag, false);
            }
            gitPush(gitFlowConfig.getDevelopmentBranch(), !skipTag, false);
        }

        // then delete if wanted
        if (!keepBranch) {
            // git branch -d hotfix/...
            gitBranchDelete(hotfixBranchName);
        }
    }
    
    @Override
    protected Boolean getIndividualInstallProjectConfig() {
        return installProjectOnHotfixFinish;
    }
    
    @Override
    protected boolean getSkipTestProjectConfig() {
        return skipTestProject;
    }

    @Override
    protected Boolean getIndividualSkipTestProjectConfig() {
        return skipTestProjectOnHotfixFinish;
    }
    
    @Override
    protected String getIndividualInstallProjectGoals() {
        return installProjectGoalsOnHotfixFinish;
    }
    
    @Override
    protected String getIndividualTestProjectGoals() {
        return testProjectGoalsOnHotfixFinish;
    }
}
