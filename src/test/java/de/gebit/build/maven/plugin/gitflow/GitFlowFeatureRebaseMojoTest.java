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
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowFeatureRebaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase";

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_VERSION = BasicConstants.EXISTING_FEATURE_VERSION;

    private static final String EPIC_BRANCH = BasicConstants.EXISTING_EPIC_BRANCH;

    private static final String PROMPT_MESSAGE_LATER_REBASE_NOT_POSSIBLE = "Updating is configured for merges, a later rebase will not be possible. "
            + "Select if you want to proceed with (m)erge or you want to use (r)ebase instead or (a)bort the process.";

    private static final String PROMPT_REBASE_CONTINUE = "You have a rebase in process on your current branch. If you "
            + "run 'mvn flow:feature-rebase' before and rebase had conflicts you can continue. In other case it is "
            + "better to clarify the reason of rebase in process. Continue?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:feature-rebase' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String INTEGRATION_MASTER_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String INTEGRATION_MAINTENANCE_BRANCH = "integration/" + MAINTENANCE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.EXISTING_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_FIXUP_VERSION = BasicConstants.EXISTING_FEATURE_ISSUE
            + ": updating versions for new modules on feature branch";

    private static final String COMMIT_MESSAGE_MASTER_TESTFILE = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_MAINTENANCE_TESTFILE = "MAINTENANCE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE_TESTFILE = "FEATURE: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_EPIC_TESTFILE = "EPIC: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_EPIC = BasicConstants.EXISTING_EPIC_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
            + MASTER_BRANCH + " into " + FEATURE_BRANCH;

    private static final GitFlowFailureInfo EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic rebase failed.\nGit error message:\n\\E.*",
            "\\QFix the rebase conflicts and mark them as resolved. After that, run 'mvn flow:feature-rebase' again.\n"
                    + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E",
            "\\Q'mvn flow:feature-rebase-abort' to abort feature rebase process\\E");

    private static final GitFlowFailureInfo EXPECTED_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge failed.\nGit error message:\n\\E.*",
            "\\QFix the merge conflicts and mark them as resolved. After that, run 'mvn flow:feature-rebase' again.\n"
                    + "Do NOT run 'git merge --continue'!\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E",
            "\\Q'mvn flow:feature-rebase-abort' to abort feature rebase process\\E");

    private static final String PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER = "The current commit on " + MASTER_BRANCH
            + " is not integrated. Rebase the feature branch on top of the last integrated commit ("
            + INTEGRATION_MASTER_BRANCH + ")?";

    private static final String PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE = "The current commit on "
            + MAINTENANCE_BRANCH
            + " is not integrated. Rebase the feature branch on top of the last integrated commit ("
            + INTEGRATION_MAINTENANCE_BRANCH + ")?";

    private RepositorySet repositorySet;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
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
    public void testExecuteOnFeatureBranch() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertFeatureRebasedCorrectly();
        assertArtifactNotInstalled();
    }

    private void prepareFeatureBranchDivergentFromMaster() throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void prepareFeatureBranchDivergentFromMaintenance() throws Exception, GitAPIException, IOException {
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
    }

    private void assertFeatureRebasedCorrectly() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitHeadLinesInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE, COMMIT_MESSAGE_MARGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    private void assertFeatureRebasedCorrectlyOffline() throws ComponentLookupException, GitAPIException, IOException {
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
    }

    private void assertNoChangesInRepositoriesExceptCommitedTestfile()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    private void assertNoChangesInRepositoriesForDivergentFeatureAndMasterBranch()
            throws ComponentLookupException, GitAPIException, IOException {
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
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
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
    public void testExecuteOnMasterBranch() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "'mvn flow:feature-rebase' can be executed only on a feature branch.",
                "Please switch to a feature branch first.", "'git checkout BRANCH' to switch to the feature branch");
    }

    @Test
    public void testExecuteWithBatchModeOnFeatureBranch() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertFeatureRebasedCorrectly();
    }

    @Test
    public void testExecuteWithBatchModeOnMasterBranch() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitFlowFailureException(result, "'mvn flow:feature-rebase' can be executed only on a feature branch.",
                "Please switch to a feature branch first.", "'git checkout BRANCH' to switch to the feature branch");
        assertVersionsInPom(repositorySet.getWorkingDirectory(), TestProjects.BASIC.version);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
    }

    @Test
    public void testExecuteInstallProjectTrue() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterBranchOnSameCommitAsMaitenanceBranch()
            throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceBranchOnSameCommitAsMasterBranch()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH;
        final String USED_MAINTENANCE_BRANCH = BasicConstants.MAINTENANCE_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix",
                BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_BRANCH, USED_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(),
                BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_VERSION);
    }

    @Test
    public void testExecuteFeatureStartedOnMaintenanceBranchThatIsNotAvailableLocally_GBLD291() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
        git.deleteLocalBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, MAINTENANCE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterIntegrationBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        prepareFeatureBranchDivergentFromMaster();
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
    public void testExecuteOnFeatureBranchFeatureStartedOnMasterIntegrationBranchAndRebaseOnIntegrated()
            throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        prepareFeatureBranchDivergentFromMaster();
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MASTER, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MASTER_BRANCH,
                INTEGRATION_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceIntegrationBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                INTEGRATION_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnMaintenanceIntegrationBranchAndRebaseOnIntegrated()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        git.createIntegeratedBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "maintenance_testfile.txt", COMMIT_MESSAGE_MAINTENANCE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_ON_LAST_INTEGRATED_MAINTENANCE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                INTEGRATION_MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, INTEGRATION_MAINTENANCE_BRANCH,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteMasterWithoutChanges() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemoteAndFetchRemoteFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
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
        assertFeatureRebasedCorrectlyOffline();
    }

    @Test
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalse() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
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
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalse() throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
    public void testExecuteCurrentRemoteFeatureBranchAheadOfLocalFetchRemoteFalseWithPrefetch() throws Exception {
        // set up
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "feature_testfile.txt",
                COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.fetchRemote", "false");
        userProperties.setProperty("flow.push", "false");
        git.fetch(repositorySet);
        git.setOffline(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
    public void testExecuteCurrentFeatureBranchHasChangesLocallyAndRemoteFetchRemoteFalseWithPrefetch()
            throws Exception {
        // set up
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
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
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote and local feature branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the changes in local feature branch '" + FEATURE_BRANCH + "' first.", "'git rebase'");

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteCurrentLocalFeatureBranchAheadOfRemote() throws Exception {
        // set up
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
        final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "local_testfile.txt", COMMIT_MESSAGE_LOCAL_TESTFILE);
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
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_LOCAL_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_REMOTE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteBaseLocalBranchAheadOfRemote() throws Exception {
        // set up
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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "remote_testfile.txt",
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
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
        final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER_TESTFILE);
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
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION);
    }

    @Test
    public void testExecuteWithRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MASTER_BRANCH, USED_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MASTER_BRANCH,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.MASTER_WITH_NEW_VERSION_RELEASE_VERSION
                + "-" + BasicConstants.FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndFeatureNameWithDescription() throws Exception {
        // set up
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MASTER_BRANCH, USED_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MASTER_BRANCH,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.MASTER_WITH_NEW_VERSION_RELEASE_VERSION
                + "-" + BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndOtherConflicts() throws Exception {
        // set up
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_BRANCH;
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, USED_MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createTestfile(repositorySet, TESTFILE_NAME);
        git.modifyTestfile(repositorySet, TESTFILE_NAME);
        git.commitAll(repositorySet, COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_REBASE_CONFLICT_MESSAGE_PATTERN);
        git.assertRebaseBranchInProcess(repositorySet, USED_FEATURE_BRANCH, TESTFILE_NAME);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.MASTER_WITH_NEW_VERSION_RELEASE_VERSION
                + "-" + BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT");
    }

    @Test
    public void testExecuteWithUpdateWithMergeTrueAndRebaseWithoutVersionChangeFalseBatchMode() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        final String USED_MASTER_BRANCH = BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.developmentBranch", USED_MASTER_BRANCH);
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
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MASTER_BRANCH, USED_MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MASTER_BRANCH,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE,
                BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.MASTER_WITH_NEW_VERSION_RELEASE_VERSION
                + "-" + BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
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
        prepareFeatureBranchDivergentFromMaster();
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.push", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMasterBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        prepareFeatureBranchDivergentFromMaster();
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
        assertGitFlowFailureException(result,
                "Base branch '" + MASTER_BRANCH + "' for feature branch '" + FEATURE_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissing() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, MASTER_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMasterBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, MASTER_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMasterBranchMissingAndFetchRemoteFalse() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
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
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, MASTER_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_MAINTENANCE_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_MAINTENANCE_VERSION);
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndMaintenanceBranchMissingLocallyAndRemotely() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteRemoteBranch(repositorySet, MAINTENANCE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '" + USED_FEATURE_BRANCH
                        + "' doesn't exist.\nThis indicates a severe error condition on your branches.",
                "Please consult a gitflow expert on how to fix this!");
    }

    @Test
    public void testExecuteStartedOnMaintenanceBranchAndLocalMaintenanceBranchMissingAndFetchRemoteFalse()
            throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_MAINTENANCE_BRANCH;
        prepareFeatureBranchDivergentFromMaintenance();
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
        assertGitFlowFailureException(result,
                "Base branch '" + MAINTENANCE_BRANCH + "' for feature branch '" + USED_FEATURE_BRANCH
                        + "' doesn't exist locally.",
                "Set 'fetchRemote' parameter to true in order to try to fetch branch from remote repository.");
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedRebaseConflictAndUpdateWithMergeTrue() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
                                + "'mvn flow:feature-rebase' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature rebase process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterNotResolvedRebaseConflictAndUpdateWithMergeTrue() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
                                + "'mvn flow:feature-rebase' again.\n"
                                + "Do NOT run 'git rebase --continue' and 'git rebase --abort'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark "
                                + "conflicts as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature rebase process\\E"));
        git.assertRebaseBranchInProcess(repositorySet, FEATURE_BRANCH, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflict() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        git.assertTestfileContent(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterResolvedMergeConflictAndPromptAnswerNo() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
                                + "'mvn flow:feature-rebase' again.\nDo NOT run 'git merge --continue'!\\E",
                        "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts "
                                + "as resolved\\E",
                        "\\Q'mvn flow:feature-rebase' to continue feature rebase process\\E",
                        "\\Q'mvn flow:feature-rebase-abort' to abort feature rebase process\\E"));
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertMergeInProcess(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteOnFeatureBranchFeatureStartedOnEpicBranch() throws Exception {
        // set up
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_EPIC_BRANCH;
        git.switchToBranch(repositorySet, EPIC_BRANCH);
        git.createAndCommitTestfile(repositorySet, "epic_testfile.txt", COMMIT_MESSAGE_EPIC_TESTFILE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, EPIC_BRANCH, EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, EPIC_BRANCH, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_EPIC_VERSION_COMMIT_MESSAGE, COMMIT_MESSAGE_EPIC_TESTFILE,
                COMMIT_MESSAGE_SET_VERSION_FOR_EPIC);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), BasicConstants.FEATURE_ON_EPIC_VERSION);
    }

    @Test
    public void testExecuteWithRebaseConflictOnSetVersionCommitAndFeatureStartedOnEpicBranch() throws Exception {
        // set up
        final String USED_EPIC_BRANCH = BasicConstants.EPIC_WITH_NEW_VERSION_BRANCH;
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_ON_EPIC_WITH_NEW_VERSION_BRANCH;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        git.createAndCommitTestfile(repositorySet, "feature-testfile.txt", COMMIT_MESSAGE_FEATURE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_EPIC_BRANCH, USED_EPIC_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_EPIC_BRANCH,
                BasicConstants.EPIC_WITH_NEW_VERSION_UPGRADE_COMMIT_MESSAGE,
                BasicConstants.EPIC_WITH_NEW_VERSION_COMMIT_MESSAGE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE_TESTFILE,
                BasicConstants.FEATURE_ON_EPIC_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE,
                BasicConstants.EPIC_WITH_NEW_VERSION_UPGRADE_COMMIT_MESSAGE,
                BasicConstants.EPIC_WITH_NEW_VERSION_COMMIT_MESSAGE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(),
                BasicConstants.FEATURE_ON_EPIC_WITH_NEW_VERSION_VERSION);
    }

    @Test
    public void testExecuteContinueAfterRebaseConflictResolvedKeepingBaseBranchVersion_GBLD324() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.OURS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_REBASE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertTestfileContent(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteContinueAfterMergeConflictResolvedKeepingBaseBranchVersion() throws Exception {
        // set up
        final String TESTFILE_NAME = "testfile.txt";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
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
        repositorySet.getLocalRepoGit().checkout().setStage(Stage.OURS).addPath(TESTFILE_NAME).call();
        repositorySet.getLocalRepoGit().add().addFilepattern(TESTFILE_NAME).call();
        when(promptControllerMock.prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MERGE_CONTINUE, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertFeatureMergedCorrectly();
        git.assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    @Test
    public void testExecuteFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareFeatureBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Failed to install the project on feature branch after rebase.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:feature-rebase' again in order to continue.\n"
                        + "Do NOT push the feature branch!");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);

        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
    }

    @Test
    public void testExecuteContinueAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        prepareFeatureBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Failed to install the project on feature branch after rebase.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:feature-rebase' again in order to continue.\n"
                        + "Do NOT push the feature branch!");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE, COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteContinueWithInstallProjectFalseAfterFailureOnCleanInstall() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        prepareFeatureBranchDivergentFromMaster();
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Failed to install the project on feature branch after rebase.",
                "Please fix the problems on project and commit or use parameter 'installProject=false' and run "
                        + "'mvn flow:feature-rebase' again in order to continue.\n"
                        + "Do NOT push the feature branch!");
        git.assertBranchLocalConfigValue(repositorySet, FEATURE_BRANCH, "breakpoint", "featureRebase.cleanInstall");
        userProperties.setProperty("flow.installProject", "false");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER_TESTFILE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_INVALID_JAVA_FILE,
                COMMIT_MESSAGE_FEATURE_TESTFILE, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), FEATURE_VERSION);
        git.assertBranchLocalConfigValueMissing(repositorySet, FEATURE_BRANCH, "breakpoint");
    }

    @Test
    public void testExecuteWithNewFeatureModuleAndChangedMasterVersion() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "7.6.5-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        final String COMMIT_MESSAGE_NEW_FEATURE_MODULE = "FEATURE: added module";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceVersion(TestProjects.BASIC.version, NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        createNewModule(FEATURE_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_FEATURE_MODULE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FIXUP_VERSION,
                COMMIT_MESSAGE_NEW_FEATURE_MODULE, COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), NEW_FEATURE_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^^");

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_MASTER_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithNewFeatureModuleAndChangedMasterVersionAndSquashNewModuleVersionFixCommitTrue()
            throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "7.6.5-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        final String COMMIT_MESSAGE_NEW_FEATURE_MODULE = "FEATURE: added module";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceVersion(TestProjects.BASIC.version, NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        createNewModule(FEATURE_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_FEATURE_MODULE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("squashNewModuleVersionFixCommit", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_NEW_FEATURE_MODULE,
                COMMIT_MESSAGE_SET_VERSION, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), NEW_FEATURE_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^");

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_MASTER_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithNewFeatureModuleAndCommitAndChangedMasterVersionAndSquashNewModuleVersionFixCommitTrue()
            throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String NEW_FEATURE_VERSION = "7.6.5-" + BasicConstants.EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        final String COMMIT_MESSAGE_NEW_FEATURE_MODULE = "FEATURE: added module";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceVersion(TestProjects.BASIC.version, NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, FEATURE_BRANCH);
        createNewModule(FEATURE_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_FEATURE_MODULE);
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("squashNewModuleVersionFixCommit", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FIXUP_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_NEW_FEATURE_MODULE, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_FEATURE_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), NEW_FEATURE_VERSION);

        final String EXPECTED_VERSION_CHANGE_COMMIT = git.commitId(repositorySet, "HEAD^^^");

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals(NEW_MASTER_VERSION, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithNewFeatureModuleAndChangedMasterVersionAndUpdateWithMerge() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        final String COMMIT_MESSAGE_NEW_FEATURE_MODULE = "FEATURE: added module";
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        final String USED_COMMIT_MESSAGE_FIXUP_VERSION = BasicConstants.FEATURE_WITHOUT_VERSION_ISSUE
                + ": updating versions for new modules on feature branch";
        final String USED_COMMIT_MESSAGE_MARGE = TestProjects.BASIC.jiraProject + "-NONE: Merge branch "
                + MASTER_BRANCH + " into " + USED_FEATURE_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceVersion(TestProjects.BASIC.version, NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        createNewModule(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_FEATURE_MODULE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.updateWithMerge", "true");
        userProperties.setProperty("flow.rebaseWithoutVersionChange", "false");
        userProperties.setProperty("squashNewModuleVersionFixCommit", "true");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, USED_COMMIT_MESSAGE_FIXUP_VERSION,
                USED_COMMIT_MESSAGE_MARGE, COMMIT_MESSAGE_NEW_FEATURE_MODULE, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_MASTER_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), NEW_MASTER_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertNull(branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteWithNewFeatureModuleAndChangedMasterVersionAndWithoutFeatureVersion() throws Exception {
        // set up
        final String NEW_MASTER_VERSION = "7.6.5-SNAPSHOT";
        final String COMMIT_MESSAGE_MASTER_VERSION_UPDATE = "MASTER: update project version";
        final String COMMIT_MESSAGE_NEW_FEATURE_MODULE = "FEATURE: added module";
        final String USED_COMMIT_MESSAGE_FIXUP_VERSION = BasicConstants.FEATURE_WITHOUT_VERSION_ISSUE
                + ": updating versions for new modules on feature branch";
        final String USED_FEATURE_BRANCH = BasicConstants.FEATURE_WITHOUT_VERSION_BRANCH;
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        replaceVersion(TestProjects.BASIC.version, NEW_MASTER_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        createNewModule(BasicConstants.FEATURE_WITHOUT_VERSION_VERSION);
        git.commitAll(repositorySet, COMMIT_MESSAGE_NEW_FEATURE_MODULE);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);

        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, USED_FEATURE_BRANCH);

        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_FEATURE_BRANCH, USED_FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_FEATURE_BRANCH, USED_COMMIT_MESSAGE_FIXUP_VERSION,
                COMMIT_MESSAGE_NEW_FEATURE_MODULE, COMMIT_MESSAGE_MASTER_VERSION_UPDATE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_MASTER_VERSION);
        assertParentVersionsInPom(new File(repositorySet.getWorkingDirectory(), "module"), NEW_MASTER_VERSION);

        Properties branchConfig = git.readPropertiesFileInRemoteBranch(repositorySet, CONFIG_BRANCH,
                USED_FEATURE_BRANCH);
        assertNull(branchConfig.getProperty("versionChangeCommit"));
    }

    private void createNewModule(String version) throws IOException {
        File workingDir = repositorySet.getWorkingDirectory();
        File moduleDir = new File(workingDir, "module");
        moduleDir.mkdir();
        FileUtils.fileWrite(new File(moduleDir, "pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "       xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n"
                        + "       <modelVersion>4.0.0</modelVersion>\n" + "       <parent>\n"
                        + "               <groupId>de.gebit.build.maven.test</groupId>\n"
                        + "               <artifactId>basic-project</artifactId>\n" + "               <version>"
                        + version + "</version>\n" + "       </parent>\n" + "       <artifactId>module</artifactId>\n"
                        + "</project>\n");
        File pom = new File(workingDir, "pom.xml");
        String pomContents = FileUtils.fileRead(pom);
        pomContents = pomContents.replaceAll("</project>",
                "\t<modules><module>module</module></modules>\n\t<packaging>pom</packaging>\n</project>");
        FileUtils.fileWrite(pom, pomContents);
    }

    private void replaceVersion(String oldVersion, String newVersion) throws IOException {
        File pom = new File(repositorySet.getWorkingDirectory(), "pom.xml");
        String pomContents = FileUtils.fileRead(pom);
        pomContents = pomContents.replaceAll("<version>" + oldVersion + "</version>",
                "<version>" + newVersion + "</version>");
        pomContents = pomContents.replaceAll("<version.build>" + oldVersion + "</version.build>",
                "<version.build>" + newVersion + "</version.build>");
        FileUtils.fileWrite(pom, pomContents);
    }

}
