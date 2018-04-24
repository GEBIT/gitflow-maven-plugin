//
// GitFlowReleaseAbortMojoTest.java
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

import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowReleaseAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-abort";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String MAINTENANCE_VERSION = "2.0";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "2.0.0-SNAPSHOT";

    private static final String MAINTENANCE_RELEASE_VERSION = "2.0.0";

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String PRODUCTION_BRANCH = "latest";

    private static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION = "You have some uncommitted files. "
            + "If you continue any changes will be discarded. Continue?";

    private static final String PROMPT_MERGE_ABORT_CONTINUE = "You have a merge in process on your current branch.\n"
            + "If you run 'mvn flow:release' or 'mvn flow:release-finish' before and merge had conflicts "
            + "and now you want to abort this release then you can continue.\n"
            + "In other case it is better to clarify the reason of merge in process. Continue?";

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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    private void assertConfigCleanedUp(String developmentBranch) {
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, developmentBranch, "releaseBranch");
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranchPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndAddToIndexTestfile(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"), "n"))
                .thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITED_CHANGES_CONFIRMATION, Arrays.asList("y", "n"),
                "n");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted release-abort process because of uncommitted files.",
                "Commit or discard local changes in order to proceed.",
                "'git add' and 'git commit' to commit your changes", "'git reset --hard' to throw away your changes");
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnReleaseBranchInBatchMode() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChangesOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);

        git.assertAddedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContent(repositorySet);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteNoReleaseBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "There are no release branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnMasterTwoReleaseBranches() throws Exception {
        final String RELEASE_BRANCH2 = RELEASE_PREFIX + "3.0.0";
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH2);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "More than one release branch exists. Cannot abort release from non-release branch.",
                "Please switch to a release branch first in order to proceed.",
                "'git checkout BRANCH' to switch to the release branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, RELEASE_BRANCH2);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteOnReleaseBranchTwoReleaseBranches() throws Exception {
        // set up
        final String RELEASE_BRANCH2 = RELEASE_PREFIX + "3.0.0";
        git.createBranchWithoutSwitch(repositorySet, RELEASE_BRANCH2);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH2, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranchAndMissingDevelopmentBranchConfig() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.removeBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH, "baseBranch");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteReleaseStartedOnMaintenanceBranchAndEmptyDevelopmentBranchConfig() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.setBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH, "baseBranch", "");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocally() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteDevelopmentBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "No development branch found for current release branch. Cannot abort release.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'mvn flow:branch-config -DbranchName=" + RELEASE_BRANCH
                        + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to configure correct "
                        + "development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(DEVELOPMENT_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteMaintenanceBranchMissingLocallyAndRemotelyAndOtherDevelopmentBranch() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION,
                userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, DEVELOPMENT_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, DEVELOPMENT_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteMaintenanceAndDevelopmentBranchesMissingLocallyAndRemotely() throws Exception {
        // set up
        final String DEVELOPMENT_BRANCH = "development";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", DEVELOPMENT_BRANCH);
        git.switchToBranch(repositorySet, DEVELOPMENT_BRANCH, true);
        git.push(repositorySet);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION,
                userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, DEVELOPMENT_BRANCH);
        git.deleteRemoteBranch(repositorySet, DEVELOPMENT_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "No development branch found for current release branch. Cannot abort release.\n"
                        + "This indicates a severe error condition on your branches.",
                "Please configure correct development branch for the current release branch or consult a gitflow expert"
                        + " on how to fix this.",
                "'mvn flow:branch-config -DbranchName=" + RELEASE_BRANCH
                        + " -DpropertyName=baseBranch -DpropertyValue=[development branch name]' to configure correct "
                        + "development branch");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecutePushRemoteTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotely() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteStartedOnMaintenanceAndReleaseBranchExistsOnlyRemotely() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndPushRemoteTrue() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.setOnline(repositorySet);
        assertGitFlowFailureException(result, "There are no release branches in your repository.", null);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
    }

    @Test
    public void testExecuteReleaseBranchExistsOnlyRemotelyAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, RELEASE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH, CONFIG_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
    }

    @Test
    public void testExecuteWithConflictOnRemoteMasterMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnRemoteMaintenanceMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MAINTENANCE_BRANCH, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMaintenanceMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Modified test dummy file commit";
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MAINTENANCE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictsOnRemoteMasterMergeAndReleaseIntoMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME1 = "testfile1.txt";
        final String TESTFILE_NAME2 = "testfile2.txt";
        final String COMMIT_MESSAGE_MASTER1 = "MASTER1: Modified test dummy file commit";
        final String COMMIT_MESSAGE_MASTER2 = "MASTER2: Modified test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Unit test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME2, COMMIT_MESSAGE_RELEASE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME1);
        git.modifyTestfile(repositorySet, TESTFILE_NAME1);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER1);
        git.createTestfile(repositorySet, TESTFILE_NAME2);
        git.modifyTestfile(repositorySet, TESTFILE_NAME2);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER2);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME1, COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, TESTFILE_NAME1);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME1).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME1).call();

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, TESTFILE_NAME2);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoProductionMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_PRODUCTION = "PRODUCTION: Modified test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_PRODUCTION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_PRODUCTION);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
        git.assertLocalBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH, EXPECTED_PRODUCTION_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoProductionMergeAndRemoteMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME1 = "testfile1.txt";
        final String TESTFILE_NAME2 = "testfile2.txt";
        final String COMMIT_MESSAGE_PRODUCTION = "PRODUCTION: Modified test dummy file commit";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        git.createTestfile(repositorySet, TESTFILE_NAME1);
        git.modifyTestfile(repositorySet, TESTFILE_NAME1);
        git.commitAll(repositorySet, COMMIT_MESSAGE_PRODUCTION);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME1, COMMIT_MESSAGE_RELEASE);

        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME2);
        git.modifyTestfile(repositorySet, TESTFILE_NAME2);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME2, COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, TESTFILE_NAME1);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME1).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME1).call();

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, TESTFILE_NAME2);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_PRODUCTION);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
        git.assertLocalBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH, EXPECTED_PRODUCTION_COMMIT);
    }

    @Test
    public void testExecuteWithProductionAndConflictOnRemoteMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME, COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
        git.assertLocalBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH, EXPECTED_PRODUCTION_COMMIT);
    }

    @Test
    public void testExecuteWithNotExistingProductionAndConflictOnRemoteMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE = "REMOTE: Unit test dummy file commit";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, TESTFILE_NAME, COMMIT_MESSAGE_REMOTE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithProductionAndConflictOnProductionIntoMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Modified test dummy file commit";
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_RELEASE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
        git.assertLocalBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH, EXPECTED_PRODUCTION_COMMIT);
    }

    @Test
    public void testExecuteWithNotExistingProductionAndConflictOnProductionIntoMasterMerge() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Modified test dummy file commit";
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_RELEASE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithProductionAndConflictOnProductionIntoMaintenanceMerge() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Modified test dummy file commit";
        final String COMMIT_MESSAGE_MAINTENANCE = "MAINTENANCE: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, true);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION,
                userProperties);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_RELEASE);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MAINTENANCE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, MAINTENANCE_PRODUCTION_BRANCH,
                CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, MAINTENANCE_PRODUCTION_BRANCH,
                CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        assertConfigCleanedUp(MAINTENANCE_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
        git.assertLocalBranchCurrentCommit(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, EXPECTED_PRODUCTION_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoOtheBranchMerge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String COMMIT_MESSAGE_OTHER_RELEASE = "OTHER: Modified test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_OTHER_RELEASE);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        git.mergeWithExpectedConflict(repositorySet, RELEASE_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch '" + RELEASE_BRANCH + "' into branch '" + OTHER_BRANCH
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWithConflictOnRemoteIntoOtheBranchMerge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String COMMIT_MESSAGE_OTHER_RELEASE = "OTHER: Modified test dummy file commit";
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_OTHER_RELEASE);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.fetch(repositorySet);
        git.mergeWithExpectedConflict(repositorySet, "refs/remotes/origin/" + MASTER_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch 'origin/" + MASTER_BRANCH + "' into branch '" + OTHER_BRANCH
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWithConflictOnMasterIntoOtheBranchMerge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String COMMIT_MESSAGE_OTHER_RELEASE = "OTHER: Modified test dummy file commit";
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_OTHER_RELEASE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        git.mergeWithExpectedConflict(repositorySet, MASTER_BRANCH);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(GitExecution.TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(GitExecution.TESTFILE_NAME).call();
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch '" + MASTER_BRANCH + "' into branch '" + OTHER_BRANCH
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWithProductionAndConflictOnReleaseIntoOtheBranchMerge() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        final String COMMIT_MESSAGE_OTHER_RELEASE = "OTHER: Modified test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH, true);
        git.switchToBranch(repositorySet, OTHER_BRANCH, true);
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch '" + PRODUCTION_BRANCH + "' into branch '" + OTHER_BRANCH
                        + "'. After such a conflict release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMergeAndPromptAnswerNo() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_ABORT_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of release abort process aborted by user.", null);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "releaseBranch", RELEASE_BRANCH);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMergeAndMissingTag() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTag", "true");
        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMergeAndDeletedTag() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER = "MASTER: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, RELEASE_BRANCH, GitExecution.TESTFILE_NAME);
        git.deleteTag(repositorySet, RELEASE_TAG);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithNotExistingProductionAndConflictOnProductionIntoMasterMergeAndDeletedProduction()
            throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        final String COMMIT_MESSAGE_RELEASE = "RELEASE: Modified test dummy file commit";
        final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION, userProperties);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_RELEASE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet, userProperties);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, PRODUCTION_BRANCH, TESTFILE_NAME);
        git.deleteLocalBranch(repositorySet, PRODUCTION_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, CONFIG_BRANCH);
        git.assertLocalTags(repositorySet);
        git.assertRemoteTags(repositorySet);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        assertConfigCleanedUp(MASTER_BRANCH);
        git.assertCurrentCommit(repositorySet, EXPECTED_DEVELOPMENT_COMMIT);
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMergeAndDeletedConfigForReleaseBranch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
        git.removeConfigValue(repositorySet, "branch", MASTER_BRANCH, "releaseBranch");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch 'origin/" + MASTER_BRANCH + "' into branch '" + MASTER_BRANCH
                        + "'.\nInformation about release branch couldn't be found in git config.\n"
                        + "Release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteWithConflictOnReleaseIntoMasterMergeAndDeletedConfigForDevelopmentCommitRef()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL = "LOCAL: Modified test dummy file commit";
        ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_LOCAL);
        git.switchToBranch(repositorySet, RELEASE_BRANCH);

        ExecutorHelper.executeReleaseFinishWithResult(this, repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);
        git.removeBranchCentralConfigValue(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH,
                "developmentSavepointCommitRef");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "There is a conflict of merging branch 'origin/" + MASTER_BRANCH + "' into branch '" + MASTER_BRANCH
                        + "'.\nReset point for development branch couldn't be found in git config.\n"
                        + "Release can't be automatically aborted.",
                "Please consult a gitflow expert on how to fix this!");
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, "origin/" + MASTER_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(null, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));

        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion", NEW_DEVELOPMENT_VERSION);
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "releaseTag", RELEASE_TAG);
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "releaseBranch", RELEASE_BRANCH);
    }

}
