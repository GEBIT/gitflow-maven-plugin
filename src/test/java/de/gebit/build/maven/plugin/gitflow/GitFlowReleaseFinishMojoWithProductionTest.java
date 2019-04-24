//
// GitFlowReleaseFinishMojoWithProductionTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowReleaseFinishMojoWithProductionTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-finish";

    private static final String RELEASE_VERSION = BasicConstants.RELEASE_WITH_PRODUCTION_VERSION;

    private static final String RELEASE_BRANCH = BasicConstants.RELEASE_WITH_PRODUCTION_BRANCH;

    private static final String NEW_DEVELOPMENT_VERSION = BasicConstants.RELEASE_WITH_PRODUCTION_NEW_DEVELOPMENT_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String RELEASE_ON_MAINTENANCE_BRANCH = BasicConstants.RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_BRANCH;

    private static final String NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION = BasicConstants.RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_NEW_DEVELOPMENT_VERSION;

    private static final String PRODUCTION_BRANCH = BasicConstants.PRODUCTION_BRANCH;

    private static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String RELEASE_MAINTENANCE_TAG = "gitflow-tests-"
            + BasicConstants.RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_VERSION;

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
            + "-NONE: Merge branch " + RELEASE_ON_MAINTENANCE_BRANCH + " into " + MAINTENANCE_PRODUCTION_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + PRODUCTION_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_MAINTENANCE_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + MAINTENANCE_PRODUCTION_BRANCH + " into " + MAINTENANCE_BRANCH;

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:release-finish' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge of release branch '" + RELEASE_BRANCH + "' into production branch '" + PRODUCTION_BRANCH
                    + "' failed.\nCONFLICT (added on " + PRODUCTION_BRANCH + " and on " + RELEASE_BRANCH + "): "
                    + GitExecution.TESTFILE_NAME,
            "Either abort the release process or fix the merge conflicts, mark them as resolved by using 'git add' and "
                    + "run 'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!",
            "'mvn flow:release-abort' to abort the release process",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:release-finish' to continue release process");

    private static final GitFlowFailureInfo EXPECTED_PRODUCTION_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge of production branch '" + PRODUCTION_BRANCH + "' into development branch '" + MASTER_BRANCH
                    + "' failed.\nCONFLICT (added on " + MASTER_BRANCH + " and on " + PRODUCTION_BRANCH + "): "
                    + GitExecution.TESTFILE_NAME,
            "Either abort the release process or fix the merge conflicts, mark them as resolved by using 'git add' and "
                    + "run 'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!",
            "'mvn flow:release-abort' to abort the release process",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:release-finish' to continue release process");

    private static final GitFlowFailureInfo EXPECTED_PRODUCTION_INTO_MAINTENANCE_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge of production branch '" + MAINTENANCE_PRODUCTION_BRANCH + "' into development branch '"
                    + MAINTENANCE_BRANCH + "' failed.\nCONFLICT (added on " + MAINTENANCE_BRANCH + " and on "
                    + MAINTENANCE_PRODUCTION_BRANCH + "): " + GitExecution.TESTFILE_NAME,
            "Either abort the release process or fix the merge conflicts, mark them as resolved by using 'git add' and "
                    + "run 'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!",
            "'mvn flow:release-abort' to abort the release process",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:release-finish' to continue release process");

    private static final GitFlowFailureInfo EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE = new GitFlowFailureInfo(
            "Automatic merge of remote branch into local development branch '" + MASTER_BRANCH
                    + "' failed.\nCONFLICT (added on " + MASTER_BRANCH + " and on remote branch): "
                    + GitExecution.TESTFILE_NAME,
            "Either abort the release process or fix the merge conflicts, mark them as resolved by using 'git add' and "
                    + "run 'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!",
            "'mvn flow:release-abort' to abort the release process",
            "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
            "'mvn flow:release-finish' to continue release process");

    private static final String DEFAULT_DEPLOY_GOAL = "validate";

    private RepositorySet repositorySet;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, RELEASE_BRANCH);
        userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        userProperties.setProperty("flow.productionBranch", PRODUCTION_BRANCH);
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
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
        assertConfigCleanedUpForMaster();
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
    }

    private void assertConfigCleanedUpForMaster() {
        assertConfigCleanedUp(MASTER_BRANCH, RELEASE_BRANCH);
    }

    private void assertConfigCleanedUpForMaintenance() {
        assertConfigCleanedUp(MAINTENANCE_BRANCH, RELEASE_ON_MAINTENANCE_BRANCH);
    }

    private void assertConfigCleanedUp(String developmentBranch, String releaseBranch) {
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, developmentBranch, "releaseBranch");
    }

    private void assertDefaultDeployGoalExecuted() throws IOException {
        assertMavenCommandExecuted(DEFAULT_DEPLOY_GOAL);
    }

    private void assertDefaultDeployGoalNotExecuted() throws IOException {
        assertMavenCommandNotExecuted(DEFAULT_DEPLOY_GOAL);
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingLocally() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertConfigCleanedUpForMaster();
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String EXPECTED_PRODUCTION_COMMIT = git.localBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "The development branch '" + MASTER_BRANCH + "' configured for the current release branch '"
                        + RELEASE_BRANCH + "' doesn't exist.\nThis indicates either a wrong configuration for the "
                        + "release branch or a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'mvn flow:branch-config -DbranchName=" + RELEASE_BRANCH
                        + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to configure correct "
                        + "development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        assertDefaultDeployGoalNotExecuted();

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingRemotely() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteDevelopmentBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "The development branch '" + MASTER_BRANCH + "' configured for the current release branch '"
                        + RELEASE_BRANCH + "' doesn't exist.\nThis indicates either a wrong configuration for the "
                        + "release branch or a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'mvn flow:branch-config -DbranchName=" + RELEASE_BRANCH
                        + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to configure correct "
                        + "development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteReleaseMergeNoFFFalseAndReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "false");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeNoFF", "true");
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
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
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaintenance();
    }

    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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

    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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

    @Test
    public void testExecuteRemoteProductionBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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

    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
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
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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

    @Test
    public void testExecuteLocalAndRemoteProductionBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteRemoteMaintenanceProductionBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + MAINTENANCE_PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteLocalAndRemoteMaintenanceProductionBranchesDivergeAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + MAINTENANCE_PRODUCTION_BRANCH + "' diverge.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteProductionBranchNotExistingLocally() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
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
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndFetchRemoteFalseAndPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
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
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MASTER);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteProductionBranchNotExistingRemotely() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteProductionBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_MASTER);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocally() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
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
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaintenance();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        userProperties.setProperty("flow.pushReleaseBranch", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_REMOTE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_MAINTENANCE_PRODUCTION, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE, COMMIT_MESSAGE_REMOTE, COMMIT_MESSAGE_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE,
                COMMIT_MESSAGE_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingRemotely() throws Exception {
        // set up
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaintenance();
    }

    @Test
    public void testExecuteMaintenanceProductionBranchNotExistingLocallyAndRemotely() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance-testfile.txt", COMMIT_MESSAGE_MAINTENANCE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaintenance();
    }

    @Test
    public void testExecuteReleaseMergeProductionNoFFFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    @Test
    public void testExecuteReleaseMergeProductionNoFFTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.releaseMergeProductionNoFF", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
    }

    @Test
    public void testExecuteWithConflictOnReleaseMerge() throws Exception {
        // set up
        final String EXPECTED_DEVELOPMENT_COMMIT = git.localBranchCurrentCommit(repositorySet, MASTER_BRANCH);
        final String EXPECTED_PRODUCTION_COMMIT = git.localBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "PRODUCTION: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteWithConflictOnProductionMerge() throws Exception {
        // set up
        final String EXPECTED_DEVELOPMENT_COMMIT = git.localBranchCurrentCommit(repositorySet, MASTER_BRANCH);
        final String EXPECTED_PRODUCTION_COMMIT = git.localBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "MASTER: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, EXPECTED_PRODUCTION_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "releaseBranch", RELEASE_BRANCH);
    }

    @Test
    public void testExecuteWithConflictOnRemoteMasterMerge() throws Exception {
        // set up
        final String EXPECTED_DEVELOPMENT_COMMIT = git.localBranchCurrentCommit(repositorySet, MASTER_BRANCH);
        final String EXPECTED_PRODUCTION_COMMIT = git.localBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "LOCAL: Modified test dummy file commit");
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "releaseBranch", RELEASE_BRANCH);
    }

    @Test
    public void testExecuteWithResolvedConflictOnReleaseMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_PRODUCTION = "PRODUCTION: Modified test dummy file commit";
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_PRODUCTION);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, COMMIT_MESSAGE_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteWithResolvedConflictOnProductionMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Modified test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, EXPECTED_PRODUCTION_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_MASTER, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteWithResolvedConflictOnProductionIntoMaintenanceMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Modified test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MAINTENANCE);
        git.switchToBranch(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, EXPECTED_PRODUCTION_INTO_MAINTENANCE_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_ON_MAINTENANCE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_MAINTENANCE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION, COMMIT_MESSAGE_MERGE_MAINTENANCE_PRODUCTION,
                COMMIT_MESSAGE_MAINTENANCE, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_ON_MAINTENANCE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaintenance();
    }

    @Test
    public void testExecuteWithResolvedConflictOnRemoteMasterMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        assertGitFlowFailureException(result, EXPECTED_UPSTREAM_MERGE_CONFLICT_MESSAGE);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_MERGE_REMOTE_MASTER, COMMIT_MESSAGE_MERGE_PRODUCTION, COMMIT_MESSAGE_LOCAL,
                COMMIT_MESSAGE_MERGE_INTO_PRODUCTION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertConfigCleanedUpForMaster();
    }

    @Test
    public void testExecuteContinueAfterConflictOnReleaseIntoOtheBranchMerge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String COMMIT_MESSAGE_OTHER_RELEASE = "OTHER: Modified test dummy file commit";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createBranch(repositorySet, OTHER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_OTHER_RELEASE);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        git.mergeWithExpectedConflict(repositorySet, PRODUCTION_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch '" + PRODUCTION_BRANCH + "' into branch '" + OTHER_BRANCH
                        + "'. After such a conflict release can't be automatically proceeded.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWithSameReleaseAndDevelopmentVersion() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("developmentVersion", RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Failed to finish release process because the next develompent version is same as release version.",
                "Run 'mvn flow:release-finish' and define a development version different from release version.\n"
                        + "Or use property '-Dflow.allowSameVersion=true' to explicitly allow same versions.",
                "'mvn flow:release-finish -DdevelopmentVersion=X.Y.Z-SNAPSHOT' to predefine next development version "
                        + "different from the release version",
                "'mvn flow:release-finish -Dflow.allowSameVersion=true' to explicitly allow same versions");

        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        assertDefaultDeployGoalNotExecuted();
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
        git.assertRemoteFileExists(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
    }

    @Test
    public void testExecuteWithSameReleaseAndDevelopmentVersionAndAllowSameVersionTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("developmentVersion", RELEASE_VERSION);
        userProperties.setProperty("flow.allowSameVersion", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_INTO_PRODUCTION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertDefaultDeployGoalExecuted();
        assertMavenCommandNotExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
        assertConfigCleanedUpForMaster();
        git.assertRemoteFileMissing(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
    }

}
