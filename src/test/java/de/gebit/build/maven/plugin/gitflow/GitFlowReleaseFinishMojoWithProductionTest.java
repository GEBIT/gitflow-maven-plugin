//
// GitFlowReleaseFinishMojoWithProductionTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verifyZeroInteractions;

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
public class GitFlowReleaseFinishMojoWithProductionTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-finish";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String MAINTENANCE_VERSION = "2.0";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "2.0.0-SNAPSHOT";

    private static final String MAINTENANCE_RELEASE_VERSION = "2.0.0";

    private static final String PRODUCTION_BRANCH = "latest";

    private static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_RELEASE_START_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development "
            + "version";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_MERGE_REMOTE_MASTER = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch origin/" + MASTER_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + RELEASE_BRANCH + " into " + PRODUCTION_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + RELEASE_BRANCH + " into " + MAINTENANCE_PRODUCTION_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + PRODUCTION_BRANCH;

    private static final GitFlowFailureInfo EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of release branch '" + RELEASE_BRANCH + "' into production branch '" + PRODUCTION_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the merge conflicts, mark them as resolved and run "
                    + "'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!\\E",
            "\\Q'mvn flow:release-abort' to abort the release process\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:release-finish' to continue release process\\E");

    private static final GitFlowFailureInfo EXPECTED_PRODUCTION_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of production branch '" + PRODUCTION_BRANCH + "' into development branch '"
                    + MASTER_BRANCH + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the merge conflicts, mark them as resolved and run "
                    + "'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!\\E",
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

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir);
        userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
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

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocally() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, DEVELOPMENT_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, DEVELOPMENT_BRANCH, DEVELOPMENT_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, DEVELOPMENT_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        assertDefaultDeployGoalNotExecuted();
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
        userProperties.setProperty("releaseRebase", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFFalseAndReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFFalseAndReleaseMergeProductionNoFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFTrueAndReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFTrueAndReleaseMergeProductionNoFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
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
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_REMOTE_MASTER, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MERGE_PRODUCTION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_REMOTE_MASTER, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MERGE_PRODUCTION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranch() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_LOCAL, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
                "Remote and local production branches '" + PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
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
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
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
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + MAINTENANCE_PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH,
                RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDivergeAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
                "Remote and local production branches '" + MAINTENANCE_PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH,
                RELEASE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteProductionBranchNotExistingLocally() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndFetchRemoteFalseAndPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteProductionBranchNotExistingRemotely() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocally() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_REMOTE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
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
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingRemotely() throws Exception {
        // set up
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    // TODO: check after fix
    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Unit test dummy file commit";
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_PRODUCTION_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_MAINTENANCE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteReleaseRebaseFalseAndReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("releaseRebase", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    @Test
    public void testExecuteReleaseRebaseFalseAndReleaseMergeProductionNoFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("releaseRebase", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    @Test
    public void testExecuteReleaseRebaseTrueAndReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("releaseRebase", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    @Test
    public void testExecuteReleaseRebaseTrueAndReleaseMergeProductionNoFFTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("releaseRebase", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithConflictOnReleaseMerge() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "PRODUCTION: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
    }

    // TODO: check after fix
    @Test
    public void testExecuteWithConflictOnProductionMerge() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "MASTER: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_PRODUCTION_MERGE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.TESTFILE_NAME);
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE_PATTERN);
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
    }

}