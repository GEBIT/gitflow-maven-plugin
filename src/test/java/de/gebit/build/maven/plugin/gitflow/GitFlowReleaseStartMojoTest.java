//
// GitFlowReleaseStartMojoTest.java
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

import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowReleaseStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-start";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String MAINTENANCE_FIRST_VERSION = BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION;

    private static final String MAINTENANCE_RELEASE_VERSION = BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION;

    private static final String PRODUCTION_BRANCH = "latest";

    private static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;

    private static final String PROMPT_RELEASE_VERSION = ExecutorHelper.RELEASE_START_PROMPT_RELEASE_VERSION;

    private static final String POM_RELEASE_VERSION = ExecutorHelper.RELEASE_START_POM_RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC);
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
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteOnMaintenanceBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteOnReleaseBranch() throws Exception {
        // set up
        final String OTHER_RELEASE_BRANCH = BasicConstants.EXISTING_RELEASE_BRANCH;
        git.switchToBranch(repositorySet, OTHER_RELEASE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Release can be started only on development branch '" + MASTER_BRANCH + "' or on a maintenance branch.",
                "Please switch to the development branch '" + MASTER_BRANCH
                        + "' or to a maintenance branch first in order to proceed.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, OTHER_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, OTHER_RELEASE_BRANCH);
    }

    @Test
    public void testExecuteOnOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranch(repositorySet, OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Release can be started only on development branch '" + MASTER_BRANCH + "' or on a maintenance branch.",
                "Please switch to the development branch '" + MASTER_BRANCH
                        + "' or to a maintenance branch first in order to proceed.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, OTHER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
    }

    @Test
    public void testExecuteWithSnapshotDependenciesAndAllowSnapshotsFalse() throws Exception {
        try (RepositorySet otherRepositorySet = git
                .createGitRepositorySet(TestProjects.SNAPSHOT_DEPENDENCIES.basedir)) {
            // set up
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.allowSnapshots", "false");
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    userProperties, promptControllerMock);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "There are some SNAPSHOT dependencies in the project. Release cannot be started.",
                    "Change the dependencies or ignore with parameter 'allowSnapshots'.");

            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertMissingLocalBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, RELEASE_BRANCH);
        }
    }

    @Test
    public void testExecuteWithSnapshotDependenciesAndAllowSnapshotsTrue() throws Exception {
        try (RepositorySet otherRepositorySet = git
                .createGitRepositorySet(TestProjects.SNAPSHOT_DEPENDENCIES.basedir)) {
            // set up
            git.createAndCommitTestfile(otherRepositorySet);
            git.push(otherRepositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.allowSnapshots", "true");
            when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, RELEASE_BRANCH);
            git.assertExistingLocalBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(otherRepositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
            git.assertCommitsInLocalBranch(otherRepositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                    GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
            Properties branchConfig = git.readPropertiesFileInRemoteBranch(otherRepositorySet, CONFIG_BRANCH,
                    RELEASE_BRANCH);
            assertEquals("release", branchConfig.getProperty("branchType"));
            assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), RELEASE_VERSION);
        }
    }

    @Test
    public void testExecuteWithNonSnapshotDependenciesAndAllowSnapshotsFalse() throws Exception {
        try (RepositorySet otherRepositorySet = git
                .createGitRepositorySet(TestProjects.NON_SNAPSHOT_DEPENDENCIES.basedir)) {
            // set up
            git.createAndCommitTestfile(otherRepositorySet);
            git.push(otherRepositorySet);
            Properties userProperties = new Properties();
            userProperties.setProperty("flow.allowSnapshots", "false");
            when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
            // test
            executeMojo(otherRepositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, RELEASE_BRANCH);
            git.assertExistingLocalBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(otherRepositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(otherRepositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
            git.assertCommitsInLocalBranch(otherRepositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                    GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
            Properties branchConfig = git.readPropertiesFileInRemoteBranch(otherRepositorySet, CONFIG_BRANCH,
                    RELEASE_BRANCH);
            assertEquals("release", branchConfig.getProperty("branchType"));
            assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
            assertVersionsInPom(otherRepositorySet.getWorkingDirectory(), RELEASE_VERSION);
        }
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
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
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
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);

        git.assertModifiedFiles(repositorySet, GitExecution.TESTFILE_NAME);
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteLocalMasterBranchAheadOfRemote() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteRemoteMasterBranchAheadOfLocal() throws Exception {
        // set up
        git.remoteCreateTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote branch '" + MASTER_BRANCH + "' is ahead of the local branch.",
                "Either pull changes on remote branch into local branch or reset the changes on remote branch in order "
                        + "to proceed.",
                "'git pull' to pull remote changes into local branch");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local branches '" + MASTER_BRANCH + "' diverge.",
                "Either rebase/merge the changes into local branch '" + MASTER_BRANCH
                        + "' or reset the changes on remote branch in order to proceed.",
                "'git rebase'");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRemoteMasterBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.remoteCreateTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_LOCAL_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteRemoteMasterBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        git.remoteCreateTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote branch '" + MASTER_BRANCH + "' is ahead of the local branch.",
                "Either pull changes on remote branch into local branch or reset the changes on remote branch in order "
                        + "to proceed.",
                "'git pull' to pull remote changes into local branch");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteLocalAndRemoteMasterBranchesDivergeAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.remoteCreateTestfile(repositorySet, "remote-testfile.txt", COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local branches '" + MASTER_BRANCH + "' diverge.",
                "Either rebase/merge the changes into local branch '" + MASTER_BRANCH
                        + "' or reset the changes on remote branch in order to proceed.",
                "'git rebase'");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteLocalMaintenanceBranchAheadOfRemote() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteRemoteMaintenanceBranchAheadOfLocal() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote branch '" + MAINTENANCE_BRANCH + "' is ahead of the local branch.",
                "Either pull changes on remote branch into local branch or reset the changes on remote branch in order "
                        + "to proceed.",
                "'git pull' to pull remote changes into local branch");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteLocalAndRemoteMaintenanceBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local branches '" + MAINTENANCE_BRANCH + "' diverge.",
                "Either rebase/merge the changes into local branch '" + MAINTENANCE_BRANCH
                        + "' or reset the changes on remote branch in order to proceed.",
                "'git rebase'");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInRemoteBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FIRST_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndMissingProductionBranchLocallyAndRemotely() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndMissingProductionBranchLocally() throws Exception {
        // set up
        git.createRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        final String EXPECTED_PRODUCTION_COMMIT = git.remoteBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH, PRODUCTION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndMissingProductionBranchRemotely() throws Exception {
        // set up
        git.createBranchWithoutSwitch(repositorySet, PRODUCTION_BRANCH);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndLocalProductionBranchAheadOfRemote() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createBranch(repositorySet, PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String EXPECTED_PRODUCTION_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndRemoteProductionBranchAheadOfLocal() throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createBranch(repositorySet, PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        final String EXPECTED_PRODUCTION_COMMIT = git.remoteBranchCurrentCommit(repositorySet, PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithNoProductionFalseAndLocalAndRemoteProductionBranchesDiverge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createBranch(repositorySet, PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "local-testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote and local production branches '" + PRODUCTION_BRANCH
                        + "' diverge. This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranchWithNoProductionFalseAndRemoteProductionBranchAheadOfLocal()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, "remote-testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        final String EXPECTED_PRODUCTION_COMMIT = git.remoteBranchCurrentCommit(repositorySet,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(EXPECTED_PRODUCTION_COMMIT, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteInvalidProjectVersion() throws Exception {
        try (RepositorySet otherRepositorySet = git.createGitRepositorySet(TestProjects.INVALID_VERSION.basedir)) {
            // set up
            git.createAndCommitTestfile(repositorySet);
            git.push(repositorySet);
            // test
            MavenExecutionResult result = executeMojoWithResult(otherRepositorySet.getWorkingDirectory(), GOAL,
                    promptControllerMock);
            // verify
            verifyZeroInteractions(promptControllerMock);
            assertGitFlowFailureException(result,
                    "Failed to calculate release version. The project version '" + TestProjects.INVALID_VERSION.version
                            + "' can't be parsed.",
                    "Check the version of the project or run 'mvn flow:release-start' with specified parameter "
                            + "'releaseVersion'.",
                    "'mvn flow:release-start -DreleaseVersion=X.Y.Z' to predefine release version");

            git.assertClean(otherRepositorySet);
            git.assertCurrentBranch(otherRepositorySet, MASTER_BRANCH);
            git.assertMissingLocalBranches(otherRepositorySet, RELEASE_BRANCH);
            git.assertMissingRemoteBranches(otherRepositorySet, RELEASE_BRANCH);
        }
    }

    @Test
    public void testExecuteWithDefaultReleaseVersion() throws Exception {
        // set up
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + TestProjects.BASIC.releaseVersion;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.releaseVersion);
    }

    @Test
    public void testExecuteInBatchModeWithoutReleaseVersion() throws Exception {
        // set up
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + TestProjects.BASIC.releaseVersion;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.releaseVersion);
    }

    @Test
    public void testExecuteInBatchModeWithReleaseVersion() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteInInteractiveModeWithReleaseVersion() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithSameBranchNameTrue() throws Exception {
        // set up
        final String SINGLE_RELEASE_BRANCH = "single-release-branch";
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.sameBranchName", "true");
        userProperties.setProperty("flow.releaseBranchPrefix", SINGLE_RELEASE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, SINGLE_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, SINGLE_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, SINGLE_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, SINGLE_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                SINGLE_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertArtifactInstalled();
    }

    @Test
    public void testExecutePushReleaseBranchTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.pushReleaseBranch", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, RELEASE_BRANCH, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecutePushReleaseBranchTrueAndPushRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.pushReleaseBranch", "true");
        userProperties.setProperty("flow.push", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithSameVersion() throws Exception {
        // set up
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + TestProjects.BASIC.version;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(TestProjects.BASIC.version);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteRelaseBranchAlreadyExists() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = BasicConstants.EXISTING_RELEASE_BRANCH;
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(BasicConstants.EXISTING_RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Release branch '" + USED_RELEASE_BRANCH + "' already exists. Cannot start release.",
                "Either checkout the existing release branch or start a new release with another release version.",
                "'git checkout " + USED_RELEASE_BRANCH + "' to checkout the release branch",
                "'mvn flow:release-start' to run again and specify another release version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
    }

    @Test
    public void testExecuteRelaseBranchAlreadyExistsRemotely() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = BasicConstants.EXISTING_RELEASE_BRANCH;
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, USED_RELEASE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(BasicConstants.EXISTING_RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Remote release branch '" + USED_RELEASE_BRANCH + "' already exists on the remote 'origin'. "
                        + "Cannot start release.",
                "Either checkout the existing release branch or start a new release with another release version.",
                "'git checkout " + USED_RELEASE_BRANCH + "' to checkout the release branch",
                "'mvn flow:release-start' to run again and specify another release version");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, USED_RELEASE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_RELEASE_BRANCH);
    }

    @Test
    public void testExecuteTychoBuild() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteTychoBuildWithFourDigitsInReleaseVersion() throws Exception {
        // set up
        final String PLUGIN_RELEASE_VERSION = "1.2.3.4-rc1";
        final String EXPECTED_RELEASE_VERSION = "1.2.3.4";
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + EXPECTED_RELEASE_VERSION;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(PLUGIN_RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_RELEASE_VERSION);
    }

    @Test
    public void testExecuteTychoBuildWithMoreThenFourDigitsInReleaseVersion() throws Exception {
        // set up
        final String PLUGIN_RELEASE_VERSION = "1.2.3.4.5-rc1";
        final String EXPECTED_RELEASE_VERSION = "1.2.3.4_5";
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + EXPECTED_RELEASE_VERSION;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(PLUGIN_RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_RELEASE_VERSION);
    }

    @Test
    public void testExecuteTychoBuildWithInvalidReleaseVersion() throws Exception {
        // set up
        final String PLUGIN_RELEASE_VERSION = "invalidVersion";
        final String EXPECTED_RELEASE_VERSION = PLUGIN_RELEASE_VERSION;
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + EXPECTED_RELEASE_VERSION;
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.tychoBuild", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION))
                .thenReturn(PLUGIN_RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, EXPECTED_RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                EXPECTED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), EXPECTED_RELEASE_VERSION);
    }

    @Test
    public void testExecuteAndCheckIfUpstreamSet() throws Exception {
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("flow.pushReleaseBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertTrackingBranch(repositorySet, "origin/" + RELEASE_BRANCH, RELEASE_BRANCH);
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, RELEASE_BRANCH, "release start");
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "breakpoint", "releaseStart.cleanInstall");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertArtifactInstalled();
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteAfterSuccessfulReleaseStart() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Release can be started only on development branch '" + MASTER_BRANCH + "' or on a maintenance branch.",
                "Please switch to the development branch '" + MASTER_BRANCH
                        + "' or to a maintenance branch first in order to proceed.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteAfterSuccessfulReleaseStartAndCleanupBeforeStartTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.cleanupReleaseBeforeStart", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertArtifactNotInstalled();
    }

    @Test
    public void testExecuteAfterFailedReleaseStartAndCleanupBeforeStartTrue() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, RELEASE_BRANCH, "release start");
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "breakpoint", "releaseStart.cleanInstall");
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        userProperties.setProperty("flow.cleanupReleaseBeforeStart", "true");
        userProperties.setProperty("flow.installProject", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteAfterConflictOnReleaseMergeAndCleanupBeforeStartTrue() throws Exception {
        // set up
        final String USED_RELEASE_BRANCH = BasicConstants.EXISTING_RELEASE_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, "MASTER: Modified test dummy file commit");
        git.switchToBranch(repositorySet, USED_RELEASE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        final String EXPECTED_RELEASE_COMMIT = git.currentCommit(repositorySet);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), "release-finish");
        assertGitFlowFailureException(result, new GitFlowFailureInfo(
                "Automatic merge of release branch '" + USED_RELEASE_BRANCH + "' into development branch '"
                        + MASTER_BRANCH + "' failed.\nCONFLICT (added on " + MASTER_BRANCH + " and on "
                        + USED_RELEASE_BRANCH + "): " + GitExecution.TESTFILE_NAME,
                "Either abort the release process or fix the merge conflicts, mark them as resolved by using 'git add' "
                        + "and run 'mvn flow:release-finish' again.\nDo NOT run 'git merge --continue'!",
                "'mvn flow:release-abort' to abort the release process",
                "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                "'mvn flow:release-finish' to continue release process"));
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMergeInProcessFromBranch(repositorySet, USED_RELEASE_BRANCH, GitExecution.TESTFILE_NAME);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        git.assertBranchLocalConfigValue(repositorySet, USED_RELEASE_BRANCH, "releaseCommit", EXPECTED_RELEASE_COMMIT);
        git.assertBranchLocalConfigValueExists(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueExists(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "releaseBranch", USED_RELEASE_BRANCH);

        Properties userProperties = new Properties();
        userProperties.setProperty("flow.cleanupReleaseBeforeStart", "true");
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, USED_RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, RELEASE_BRANCH, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, MASTER_BRANCH, "releaseBranch");
    }

    @Test
    public void testExecuteWithBaseBranchCurrentBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("baseBranch", MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithBaseBranchNotCurrentBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("baseBranch", MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

    @Test
    public void testExecuteWithBaseBranchNotDevelopment() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        Properties userProperties = new Properties();
        userProperties.setProperty("baseBranch", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Release can be started only on development branch 'master' or on a maintenance branch.",
                "Please switch to the development branch 'master' or to a maintenance branch first in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingMaintenance() throws Exception {
        // set up
        final String NON_EXISTING_MAINTENANCE_BRANCH = "maintenance/gitflow-tests-nonExisting";
        Properties userProperties = new Properties();
        userProperties.setProperty("baseBranch", NON_EXISTING_MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        assertGitFlowFailureException(result,
                "Branch '" + NON_EXISTING_MAINTENANCE_BRANCH + "' defined in 'baseBranch' property doesn't exist.",
                "Please define an existing branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNameNotExistingLocalFeature() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("baseBranch", MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, RELEASE_BRANCH);
        assertEquals("release", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EXPECTED_DEVELOPMENT_COMMIT, branchConfig.getProperty("developmentSavepointCommitRef"));
        assertEquals(null, branchConfig.getProperty("productionSavepointCommitRef"));
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
    }

}
