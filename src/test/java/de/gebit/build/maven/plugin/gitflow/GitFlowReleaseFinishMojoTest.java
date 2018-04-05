//
// GitFlowReleaseFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowReleaseFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-finish";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String MAINTENANCE_VERSION = "2.0";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "2.0.0-SNAPSHOT";

    private static final String MAINTENANCE_RELEASE_VERSION = "2.0.0";

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_RELEASE_START_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development "
            + "version";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_MERGE_RELEASE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + RELEASE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_REMOTE_MASTER = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch origin/" + MASTER_BRANCH;

    private static final String PROMPT_NEXT_DEVELOPMENT_VERSION = "What is the next development version?";

    private static final GitFlowFailureInfo EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of release branch '" + RELEASE_BRANCH + "' into development branch '" + MASTER_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the merge conflicts, mark them as resolved and run "
                    + "'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!\\E",
            "\\Q'mvn flow:release-abort' to abort the release process\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:release-finish' to continue release process\\E");

    private static final GitFlowFailureInfo EXPECTED_RELEASE_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic rebase of development branch '" + MASTER_BRANCH + "' onto release branch '" + RELEASE_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the rebase conflicts, mark them as resolved and run "
                    + "'mvn flow:release-finish' again.\nDo NOT run 'git rebase --continue'!\\E",
            "\\Q'mvn flow:release-abort' to abort the release process\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:release-finish' to continue release process\\E");

    private static final GitFlowFailureInfo EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of remote development branch '" + MASTER_BRANCH + "' into local development branch '"
                    + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the merge conflicts, mark them as resolved and run "
                    + "'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!\\E",
            "\\Q'mvn flow:release-abort' to abort the release process\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:release-finish' to continue release process\\E");

    private static final String DEFAULT_DEPLOY_GOAL = "validate";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecuteWithCommandLineException() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertMavenCommandNotExecuted("clean test");
        assertMavenCommandNotExecuted("clean verify");
    }

    private void assertDefaultDeployGoalExecuted() throws IOException {
        assertMavenCommandExecuted(DEFAULT_DEPLOY_GOAL);
    }

    private void assertDefaultDeployGoalNotExecuted() throws IOException {
        assertMavenCommandNotExecuted(DEFAULT_DEPLOY_GOAL);
    }

    @Test
    public void testExecuteWithDeployAndSiteDeploy() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy site site:deploy");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("deploy site site:deploy");
        assertArtifactDeployed(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertSiteDeployed(repositorySet.getWorkingDirectory());
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithUnstagedChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result, "You have some uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    // TODO: check after fix
    @Test
    public void testExecuteOnMasterBranch() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Current branch '" + MASTER_BRANCH + "' is not a release branch.",
                "Please switch to the release branch that you want to finish in order to proceed.",
                "'git checkout BRANCH' to switch to the release branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    // TODO: check after fix
    @Test
    public void testExecuteOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Current branch '" + MAINTENANCE_BRANCH + "' is not a release branch.",
                "Please switch to the release branch that you want to finish in order to proceed.",
                "'git checkout BRANCH' to switch to the release branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
    }

    // TODO: check after fix
    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Current branch '" + OTHER_BRANCH + "' is not a release branch.",
                "Please switch to the release branch that you want to finish in order to proceed.",
                "'git checkout [BRANCH]' to switch to the release branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithoutDevelopmentBranchConfig() throws Exception {
        // set up
        git.switchToBranch(repositorySet, RELEASE_BRANCH, true);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "The release branch '" + RELEASE_BRANCH + "' has no development branch configured.",
                "Please configure development branch for current release branch first in order to proceed.",
                "'git config branch." + RELEASE_BRANCH
                        + ".development [development branch name]' to configure development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertMavenCommandExecuted("clean test");
        assertMavenCommandNotExecuted("clean verify");
    }

    @Test
    public void testExecuteTychoBuildAndSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertMavenCommandNotExecuted("clean test");
        assertMavenCommandExecuted("clean verify");
    }

    @Test
    public void testExecuteWithoutReleaseGoals() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteMultipleReleaseGoals() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "validate,clean");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("validate");
        assertMavenCommandExecuted("clean");
        assertMavenCommandNotExecuted("validate clean");
        assertMavenCommandNotExecuted("validate,clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndNotDeployGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "clean");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndOnlyDeployGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("deploy");
    }

    @Test
    public void testExecuteSkipDeployProjectFalseAndOnlyDeployGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy");
        userProperties.setProperty("flow.skipDeployProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("deploy");
        assertArtifactDeployed(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndDeployReplacementAndOnlyDeployGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        userProperties.setProperty("deployReplacement", "clean");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("clean");
    }

    @Test
    public void testExecuteSkipDeployProjectFalseAndDeployReplacementAndOnlyDeployGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy");
        userProperties.setProperty("flow.skipDeployProject", "false");
        userProperties.setProperty("deployReplacement", "clean");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("deploy");
        assertMavenCommandNotExecuted("clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueWithDeployFirstGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy clean");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("deploy clean");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueWithDeployLastGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "clean deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("clean deploy");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueWithDeployGoalInTheMiddle() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "validate deploy clean");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("validate deploy clean");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("validate clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueWithDeployAsPartOfGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "site-deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandExecuted("site-deploy");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndDeployReplacementWithDeployFirstGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "deploy clean");
        userProperties.setProperty("flow.skipDeployProject", "true");
        userProperties.setProperty("deployReplacement", "initialize");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("deploy clean");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("initialize clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndDeployReplacementWithDeployLastGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "clean deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        userProperties.setProperty("deployReplacement", "initialize");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("clean deploy");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("clean initialize");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndDeployReplacementWithDeployGoalInTheMiddle() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "validate deploy clean");
        userProperties.setProperty("flow.skipDeployProject", "true");
        userProperties.setProperty("deployReplacement", "initialize");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("validate deploy clean");
        assertMavenCommandNotExecuted("deploy");
        assertMavenCommandExecuted("validate initialize clean");
    }

    @Test
    public void testExecuteSkipDeployProjectTrueAndDeployReplacementWithDeployAsPartOfGoal() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.releaseGoals", "site-deploy");
        userProperties.setProperty("flow.skipDeployProject", "true");
        userProperties.setProperty("deployReplacement", "initialize");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertMavenCommandNotExecuted("site-initialize");
        assertMavenCommandExecuted("site-deploy");
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocally() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, DEVELOPMENT_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "The development branch '" + DEVELOPMENT_BRANCH + "' configured for the current release branch '"
                        + RELEASE_BRANCH + "' doesn't exist.\nThis indicates either a wrong configuration for the "
                        + "release branch or a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'git config branch." + RELEASE_BRANCH
                        + ".development [development branch name]' to configure correct development branch");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, DEVELOPMENT_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, DEVELOPMENT_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "The development branch '" + DEVELOPMENT_BRANCH + "' configured for the current release branch '"
                        + RELEASE_BRANCH + "' doesn't exist.\nThis indicates either a wrong configuration for the "
                        + "release branch or a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'git config branch." + RELEASE_BRANCH
                        + ".development [development branch name]' to configure correct development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteSkipTagTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTag", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithDevelopmentVersionInBatchMode() throws Exception {
        // set up
        final String OTHER_DEVELOPMENT_VERSION = "3.0.0-SNAPSHOT";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("developmentVersion", OTHER_DEVELOPMENT_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), OTHER_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithDevelopmentVersionInInteractiveMode() throws Exception {
        // set up
        final String OTHER_DEVELOPMENT_VERSION = "3.0.0-SNAPSHOT";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("developmentVersion", OTHER_DEVELOPMENT_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), OTHER_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithoutDevelopmentVersionInBatchMode() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithoutDevelopmentVersionInInteractiveMode() throws Exception {
        // set up
        final String OTHER_DEVELOPMENT_VERSION = "3.0.0-SNAPSHOT";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION))
                .thenReturn(OTHER_DEVELOPMENT_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), OTHER_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithIvalidProjectVersionInBatchMode() throws Exception {
        // set up
        final String INVALID_RELEASE_VERSION = "invalidReleaseVersion";
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + INVALID_RELEASE_VERSION;
        ExecutorHelper.executeReleaseStart(this, repositorySet, INVALID_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "Failed to calculate next development version. The release version '" + INVALID_RELEASE_VERSION
                        + "' can't be parsed.",
                "Run 'mvn flow:release-finish' in interactive mode or with specified parameter 'developmentVersion'.",
                "'mvn flow:release-finish -DdevelopmentVersion=X.Y.Z-SNAPSHOT -B' to predefine next development version",
                "'mvn flow:release-finish' to run in interactive mode");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EXPECTED_RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithIvalidProjectVersionInInteractiveMode() throws Exception {
        // set up
        final String INVALID_RELEASE_VERSION = "invalidReleaseVersion";
        final String EXPECTED_RELEASE_TAG = "gitflow-tests-" + INVALID_RELEASE_VERSION;
        final String OTHER_DEVELOPMENT_VERSION = "3.0.0-SNAPSHOT";
        ExecutorHelper.executeReleaseStart(this, repositorySet, INVALID_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION)).thenReturn(OTHER_DEVELOPMENT_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, EXPECTED_RELEASE_TAG);
        git.assertRemoteTags(repositorySet, EXPECTED_RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), OTHER_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteKeepBranchTrue() throws Exception {
        // set up
        Properties userPropertiesForReleaseStart = new Properties();
        userPropertiesForReleaseStart.setProperty("flow.pushReleaseBranch", "true");
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userPropertiesForReleaseStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteKeepBranchFalseAndPushReleaseBranchFalse() throws Exception {
        // set up
        Properties userPropertiesForReleaseStart = new Properties();
        userPropertiesForReleaseStart.setProperty("flow.pushReleaseBranch", "true");
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userPropertiesForReleaseStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteKeepBranchFalseAndPushReleaseBranchTrue() throws Exception {
        // set up
        Properties userPropertiesForReleaseStart = new Properties();
        userPropertiesForReleaseStart.setProperty("flow.pushReleaseBranch", "true");
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userPropertiesForReleaseStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteKeepBranchFalseAndPushReleaseBranchTrueAndPushRemoteFalse() throws Exception {
        // set up
        Properties userPropertiesForReleaseStart = new Properties();
        userPropertiesForReleaseStart.setProperty("flow.pushReleaseBranch", "true");
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userPropertiesForReleaseStart);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("keepBranch", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "true");
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseRebaseFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseRebaseTrue() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "false");
        userProperties.setProperty("flow.releaseMergeNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "false");
        userProperties.setProperty("flow.releaseMergeNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteTagCorrectCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_TAG_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertTagCommit(repositorySet, RELEASE_TAG, EXPECTED_TAG_COMMIT);
    }

    // TODO: check after fix
    @Test
    public void testExecuteDetachReleaseCommitTrueAndInstallProjectFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DETACHED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("detachReleaseCommit", "true");
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_DETACHED_RELEASE_COMMIT);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertDefaultDeployGoalExecuted();
        git.assertCurrentCommit(repositorySet, EXPECTED_DETACHED_RELEASE_COMMIT);
        git.assertTestfileMissing(repositorySet, "master-testfile.txt");
        assertArtifactNotInstalled();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDetachReleaseCommitTrueAndInstallProjectTrue() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DETACHED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("detachReleaseCommit", "true");
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_DETACHED_RELEASE_COMMIT);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertDefaultDeployGoalExecuted();
        git.assertCurrentCommit(repositorySet, EXPECTED_DETACHED_RELEASE_COMMIT);
        git.assertTestfileMissing(repositorySet, "master-testfile.txt");
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteDetachReleaseCommitFalseAndInstallProjectFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("detachReleaseCommit", "false");
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteDetachReleaseCommitFalseAndInstallProjectTrue() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("detachReleaseCommit", "false");
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteTychoBuildFalseAndSnapshotVersion() throws Exception {
        // set up
        final String RELEASE_SNAPSHOT_VERSION = "1.42.0-SNAPSHOT";
        final String RELEASE_SNAPSHOT_TAG = "gitflow-tests-" + RELEASE_SNAPSHOT_VERSION;
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_SNAPSHOT_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_SNAPSHOT_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_SNAPSHOT_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteTychoBuildTrueAndSnapshotVersion() throws Exception {
        // set up
        final String RELEASE_SNAPSHOT_VERSION = "1.42.0-SNAPSHOT";
        final String RELEASE_TAG_WITHOUT_SNAPSHOT = "gitflow-tests-1.42.0";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_SNAPSHOT_VERSION);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG_WITHOUT_SNAPSHOT);
        git.assertRemoteTags(repositorySet, RELEASE_TAG_WITHOUT_SNAPSHOT);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteReleaseBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Remote release branch '" + RELEASE_BRANCH + "' is ahead of the local branch.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, RELEASE_BRANCH, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteRemoteReleaseBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteReleaseBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote release branch '" + RELEASE_BRANCH + "' is ahead of the local branch.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, RELEASE_BRANCH, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteReleaseBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local release branches '" + RELEASE_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, RELEASE_BRANCH, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteReleaseBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteReleaseBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, RELEASE_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote and local release branches '" + RELEASE_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, RELEASE_BRANCH, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_REMOTE_MASTER, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_RELEASE, COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_REMOTE_MASTER, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MERGE_RELEASE,
                COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithConflictOnReleaseMerge() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "MASTER: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithConflictOnReleaseRebase() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "MASTER: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseRebase", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_RELEASE_REBASE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertRebaseBranchInProcessOntoBranch(repositorySet, MASTER_BRANCH, RELEASE_BRANCH,
                GitExecution.TESTFILE_NAME);
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithConflictOnRemoteMasterMerge() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "LOCAL: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
    }

}