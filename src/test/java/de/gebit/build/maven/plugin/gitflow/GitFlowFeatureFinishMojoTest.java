//
// GitFlowFeatureFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_NUMBER = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String FEATURE_NUMBER_2 = TestProjects.BASIC.jiraProject + "-4711";

    private static final String FEATURE_BRANCH_2 = "feature/" + FEATURE_NUMBER_2;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch feature/" + FEATURE_NUMBER + " into " + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NUMBER + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_REVERT_VERSION = FEATURE_NUMBER
            + ": reverting versions for development branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for maintenance branch";

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
    public void testExecuteOnFeatureBranchOneFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandNotExecuted("clean test");
        assertMavenCommandNotExecuted("clean verify");
        assertArtifactNotInstalled();
    }

    private void assertFeatureFinishedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteOnMaintenanceBranch_GBLD283() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        assertTrue("working directory is not clean", git.status(repositorySet).isClean());
        assertEquals("current branch is wrong", MAINTENANCE_BRANCH, git.currentBranch(repositorySet));
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MERGE_INTO_MAINTENANCE,
                COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);

        verifyZeroInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithStagedButUncommitedChanges() throws Exception {
        // set up
        git.createAndAddToIndexTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        assertMavenFailureException(result,
                "You have some uncommitted files. Commit or discard local changes in order to proceed.");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositories();

        Set<String> addedFiles = git.status(repositorySet).getAdded();
        assertEquals("number of added files is wrong", 1, addedFiles.size());
        assertEquals("added file is wrong", GitExecution.TESTFILE_NAME, addedFiles.iterator().next());
        git.assertTestfileContent(repositorySet);
    }

    private void assertNoChangesInRepositories() throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    private void assertNoChanges() throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertClean(repositorySet);
        assertNoChangesInRepositories();
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
        assertMavenFailureException(result,
                "You have some uncommitted files. Commit or discard local changes in order to proceed.");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        assertNoChangesInRepositoriesExceptCommitedTestfile();

        Set<String> modifiedFiles = git.status(repositorySet).getModified();
        assertEquals("number of modified files is wrong", 1, modifiedFiles.size());
        assertEquals("modified file is wrong", GitExecution.TESTFILE_NAME, modifiedFiles.iterator().next());
        git.assertTestfileContentModified(repositorySet);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertMavenFailureException(result, "There are no feature branches.");
        assertNoChanges();
    }

    @Test
    public void testExecuteOnMasterOneFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);

        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteOnFeatureBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER_2);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS + "2. " + FEATURE_BRANCH_2 + LS
                + "Choose feature branch to finish";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        assertFeatureFinishedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertMavenFailureException(result,
                "Feature finish in batch mode can be executed only from feature branch. Please switch to the "
                        + "feature branch you want to finish or execute feature finish in interactive mode.");

        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    @Test
    public void testExecuteSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean test");
        assertMavenCommandNotExecuted("clean verify");
    }

    @Test
    public void testExecuteTychoBuildAndSkipTestProjectFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("flow.tychoBuild", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertMavenCommandExecuted("clean verify");
        assertMavenCommandNotExecuted("clean test");
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureFinishedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteKeepFeatureBranchTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.keepFeatureBranch", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeFalseAndVersionWithoutFeatureName() throws Exception {
        // set up
        Properties properties = new Properties();
        properties.setProperty("flow.skipFeatureVersion", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER, properties);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndVersionWithoutFeatureName() throws Exception {
        // set up
        Properties properties = new Properties();
        properties.setProperty("flow.skipFeatureVersion", "true");
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER, properties);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteFeatureSquashTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureSquash", "true");
        final String EXPECTED_SQUASHED_COMMIT_MESSAGE = FEATURE_BRANCH;
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, EXPECTED_SQUASHED_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }

    @Test
    public void testExecuteRebaseWithoutVersionChangeTrueAndHasMergeCommit() throws Exception {
        // set up
        final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";
        final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_textfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        String COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH = "Merging master to feature branch";
        git.mergeAndCommit(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH);
        git.createAndCommitTestfile(repositorySet, "feature_textfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MARGE,
                COMMIT_MESSAGE_REVERT_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_MERGE_BETWEEN_START_AND_FINISH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
    }
}
