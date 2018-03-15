//
// GitFlowFeatureRebaseMojo.java
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
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowFeatureRebaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase";

    private static final String FEATURE_NAME = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME_WITH_DESCRIPTION = FEATURE_NAME + "-someDescription";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String FEATURE_BRANCH_WITH_DESCRIPTION = "feature/" + FEATURE_NAME_WITH_DESCRIPTION;

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT = "Feature branches:" + LS + "1. " + FEATURE_BRANCH
            + LS + "Choose feature branch to rebase";

    private static final String PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE = "Updating is configured for merges, a later rebase will not be possible. "
            + "Select if you want to proceed with (m)erge or you want to use (r)ebase instead or (a)bort the process.";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-rebase' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-rebase' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final String FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FEATURE_NAME + "-SNAPSHOT";

    private static final String FEATURE_NUMBER_2 = TestProjects.BASIC.jiraProject + "-4711";

    private static final String FEATURE_BRANCH_2 = "feature/" + FEATURE_NUMBER_2;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_FEATURE_VERSION = "1.42.0-" + FEATURE_NAME + "-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = FEATURE_NAME + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + MASTER_BRANCH + " into " + FEATURE_BRANCH;

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic rebase failed.\nGit error message:\n\\E.*",
            "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-rebase' again. "
                    + "Do NOT run 'git rebase --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-rebase' again. "
                    + "Do NOT run 'git merge --continue'.\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E");

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
    public void testExecuteOnFeatureBranchOneFeatureBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
        assertArtifactNotInstalled();
    }

    private void createFeatureBranchDivergentFromMaster() throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void createFeatureBranchDivergentFromMaintenance() throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void assertFeatureRebasedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    private void assertFeatureMergedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_MARGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    private void assertFeatureRebasedCorrectlyOffline() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
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

    private void assertNoChangesInRepositoriesForDivergentFeatureAndMasterBranch()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    private void assertNoChangesForDivergentFeatureAndMasterBranch()
            throws ComponentLookupException, GitAPIException, IOException {
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertClean(repositorySet);
        assertNoChangesInRepositoriesForDivergentFeatureAndMasterBranch();
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
        assertGitFlowFailureException(result, "There are no feature branches in your repository.",
                "Please start a feature first.", "'mvn flow:feature-start'");
        assertNoChanges();
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteOnFeatureBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH_2);
        createFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchTwoFeatureBranchesAndOtherBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        git.createBranchWithoutSwitch(repositorySet, FEATURE_BRANCH_2);
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        String PROMPT_MESSAGE = "Feature branches:" + LS + "1. " + FEATURE_BRANCH + LS + "2. " + FEATURE_BRANCH_2 + LS
                + "Choose feature branch to rebase";
        when(promptControllerMock.prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE, Arrays.asList("1", "2"));
        verifyNoMoreInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, FEATURE_BRANCH_2, OTHER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchOneFeatureBranchStartedRemotely() throws Exception {
        // set up
        git.useClonedRemoteRepository(repositorySet);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.useLocalRepository(repositorySet);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result,
                "In non-interactive mode 'mvn flow:feature-rebase' can be executed only on a feature branch.",
                "Please switch to a feature branch first or run in interactive mode.",
                "'git checkout BRANCH' to switch to the feature branch",
                "'mvn flow:feature-rebase' to run in interactive mode");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
        assertArtifactInstalled();
    }

    @Test
    public void testExecuteDeleteRemoteBranchOnRebaseTrue() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.deleteRemoteBranchOnRebase", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecutePushRemoteFalse() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMaintenanceBranch() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteOnMaintenanceBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch()
            throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteOnMasterBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMasterBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        createFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnOtherBranchFeatureStartedOnMainteanceBranch() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.createBranchWithoutSwitch(repositorySet, OTHER_BRANCH);
        createFeatureBranchDivergentFromMaintenance();
        git.switchToBranch(repositorySet, OTHER_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, OTHER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, OTHER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        createFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MASTER_BRANCH,
                INTEGRATION_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeIntegerated(this, repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_MAINTENANCE_BRANCH,
                FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, INTEGRATION_MAINTENANCE_BRANCH,
                FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                INTEGRATION_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteMasterWithoutChanges() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteSelectedLocalFeatureBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectlyOffline();
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteSelectedRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteSelectedFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Local base branch '" + MASTER_BRANCH + "' is ahead of remote branch. Pushing of the rebased feature "
                        + "branch will create an inconsistent state in remote repository.",
                "Push the base branch '" + MASTER_BRANCH + "' first or set 'pushRemote' parameter to false in order to "
                        + "avoid inconsistent state in remote repository.");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemoteAndPushRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocal() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemote() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocalAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseRemoteBranchAheadOfLocalAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_REMOTE_TESTFILE, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteBaseBranchHasChangesLocallyAndRemoteAndFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
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
        assertGitFlowFailureException(result, "Remote and local base branches '" + MASTER_BRANCH + "' diverge.",
                "Rebase the changes in local branch '" + MASTER_BRANCH + "' in order to proceed.",
                "'git checkout " + MASTER_BRANCH + "' and 'git rebase' to rebase the changes in base branch '"
                        + MASTER_BRANCH + "'");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommit() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeSetVersion(this, repositorySet, "2.0.0-SNAPSHOT");
        final String COMMIT_MESSAGE_MASTER_UPDATE_VERSION = "MASTER: version update";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), "2.0.0-" + FEATURE_NAME + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndFeatureNameWithDescription() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME_WITH_DESCRIPTION);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeSetVersion(this, repositorySet, "2.0.0-SNAPSHOT");
        final String COMMIT_MESSAGE_MASTER_UPDATE_VERSION = "MASTER: version update";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH_WITH_DESCRIPTION);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH_WITH_DESCRIPTION);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_WITH_DESCRIPTION);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH_WITH_DESCRIPTION);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH_WITH_DESCRIPTION,
                FEATURE_BRANCH_WITH_DESCRIPTION);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH_WITH_DESCRIPTION, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), "2.0.0-" + FEATURE_NAME + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndOtherConflicts() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeSetVersion(this, repositorySet, "2.0.0-SNAPSHOT");
        final String COMMIT_MESSAGE_MASTER_UPDATE_VERSION = "MASTER: version update";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), "2.0.0-" + FEATURE_NAME + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeFalseBatchMode() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertFeatureMergedCorrectly();
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeTrueBatchMode() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "Updating is configured for merges, a later rebase will not be possible.",
                "Run feature-rebase in interactive mode", "'mvn flow:feature-rebase' to run in interactive mode");
        assertNoChangesForDivergentFeatureAndMasterBranch();
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeFalseInteractiveMode() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureMergedCorrectly();
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeTrueInteractiveModeAnswerMerge()
            throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("m");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureMergedCorrectly();
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeTrueInteractiveModeAnswerRebase()
            throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("r");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeTrueInteractiveModeAnswerAbort()
            throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("a");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Feature rebase aborted by user.", null);
        assertNoChangesForDivergentFeatureAndMasterBranch();
    }

    @Test
    public void testExecuteWithMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("m");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteWithRebaseConflictAndUpdateWithMergeTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("r");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndUpdateWithMergeTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        ExecutorHelper.executeSetVersion(this, repositorySet, "2.0.0-SNAPSHOT");
        final String COMMIT_MESSAGE_MASTER_UPDATE_VERSION = "MASTER: version update";
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("r");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_UPDATE_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), "2.0.0-" + FEATURE_NAME + "-SNAPSHOT");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissing() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' doesn't exist remotely. Pushing of the rebased feature branch "
                        + "will create an inconsistent state in remote repository.",
                "Push the base branch '" + MASTER_BRANCH + "' first or set 'pushRemote' parameter to false in order to "
                        + "avoid inconsistent state in remote repository.");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndRemoteMasterBranchMissingAndPushRemoteFalse() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to find base branch for feature branch '" + FEATURE_BRANCH
                        + "'. This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaster();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Failed to find base branch for feature branch '" + FEATURE_BRANCH + "'.",
                "Set 'fetchRemote' parameter to true in order to search for base branch also in remote repository.");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MAINTENANCE_BRANCH, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MAINTENANCE_BRANCH, FEATURE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), MAINTENANCE_FEATURE_VERSION);
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Failed to find base branch for feature branch '" + FEATURE_BRANCH
                        + "'. This indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Ignore("Should be activated again before storing of base branch into branch config will be implemented")
    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        createFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        git.setOnline(repositorySet);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Failed to find base branch for feature branch '" + FEATURE_BRANCH + "'.",
                "Set 'fetchRemote' parameter to true in order to search for base branch also in remote repository.");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature rebase aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictAndUpdateWithMergeTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("r");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after rebase.\nGit error message:\n\\E.*",
                        "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictAndUpdateWithMergeTrue() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("r");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after rebase.\nGit error message:\n\\E.*",
                        "\\QFix the rebase conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again. Do NOT run 'git rebase --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureMergedCorrectly();
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.THEIRS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Continuation of feature rebase aborted by user.", null);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedMergeConflict() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        final String TESTFILE_NAME = "testfile.txt";
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"), "a"))
                .thenReturn("m");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE, Arrays.asList("m", "r", "a"),
                "a");
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo("\\QThere are unresolved conflicts after merge.\nGit error message:\n\\E.*",
                        "\\QFix the merge conflicts and mark them as resolved. After that, run "
                                + "'mvn flow:feature-rebase' again. Do NOT run 'git merge --continue'.\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts "
                                + "as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E"));
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
    }

}
