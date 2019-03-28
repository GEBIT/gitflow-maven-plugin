//
// GitFlowReleaseMojoTest.java
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
public class GitFlowReleaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_PREFIX = "release/gitflow-tests-";

    private static final String RELEASE_BRANCH = RELEASE_PREFIX + RELEASE_VERSION;

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String MAINTENANCE_BRANCH = BasicConstants.EXISTING_MAINTENANCE_BRANCH;

    private static final String MAINTENANCE_RELEASE_VERSION = BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION;

    private static final String PRODUCTION_BRANCH = BasicConstants.PRODUCTION_BRANCH;

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String POM_RELEASE_VERSION = ExecutorHelper.RELEASE_START_POM_RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_RELEASE_START_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development version";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String COMMIT_MESSAGE_MERGE_INTO_PRODUCTION = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch " + RELEASE_BRANCH + " into " + PRODUCTION_BRANCH;

    private static final String PROMPT_RELEASE_VERSION = ExecutorHelper.RELEASE_START_PROMPT_RELEASE_VERSION;

    private static final String PROMPT_NEXT_DEVELOPMENT_VERSION = "What is the next development version?";

    private static final String PROMPT_MERGE_CONTINUE = "You have a merge in process on your current branch. If you "
            + "run 'mvn flow:release' before and merge had conflicts you can continue. In other case it is "
            + "better to clarify the reason of merge in process. Continue?";

    private static final GitFlowFailureInfo EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN = new GitFlowFailureInfo(
            "\\QAutomatic merge of release branch '" + RELEASE_BRANCH + "' into production branch '" + PRODUCTION_BRANCH
                    + "' failed.\nGit error message:\n\\E.*",
            "\\QEither abort the release process or fix the merge conflicts, mark them as resolved and run "
                    + "'mvn flow:release' again.\nDo NOT run 'git merge --continue'!\\E",
            "\\Q'mvn flow:release-abort' to abort the release process\\E",
            "\\Q'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved\\E",
            "\\Q'mvn flow:release' to continue release process\\E");

    private static final String DEFAULT_DEPLOY_GOAL = "validate";

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
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    private void assertDefaultDeployGoalExecuted() throws IOException {
        assertMavenCommandExecuted(DEFAULT_DEPLOY_GOAL);
    }

    private void assertDefaultDeployGoalNotExecuted() throws IOException {
        assertMavenCommandNotExecuted(DEFAULT_DEPLOY_GOAL);
    }

    @Test
    public void testExecuteOnMaintenanceBranch() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithNotExistingProductionBranch() throws Exception {
        // set up
        final String USED_PRODUCTION_BRANCH = BasicConstants.MISSING_PRODUCTION_BRANCH;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        userProperties.setProperty("flow.productionBranch", USED_PRODUCTION_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_PRODUCTION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_PRODUCTION_BRANCH, USED_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_PRODUCTION_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteOnMaintenanceWithNotExistingProductionBranch() throws Exception {
        // set up
        final String USED_PRODUCTION_BRANCH = BasicConstants.MISSING_PRODUCTION_BRANCH;
        final String USED_MAINTENANCE_PRODUCTION_BRANCH = USED_PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        userProperties.setProperty("flow.productionBranch", USED_PRODUCTION_BRANCH);
        git.switchToBranch(repositorySet, MAINTENANCE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION))
                .thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, MAINTENANCE_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MAINTENANCE_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, USED_MAINTENANCE_PRODUCTION_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertExistingRemoteBranches(repositorySet, USED_MAINTENANCE_PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, USED_MAINTENANCE_PRODUCTION_BRANCH,
                USED_MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, USED_MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithIvalidProjectVersionInBatchMode() throws Exception {
        // set up
        final String INVALID_RELEASE_VERSION = "invalidReleaseVersion";
        final String EXPECTED_RELEASE_BRANCH = RELEASE_PREFIX + INVALID_RELEASE_VERSION;
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", INVALID_RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Failed to calculate next development version. The release version '" + INVALID_RELEASE_VERSION
                        + "' can't be parsed.",
                "Run 'mvn flow:release' in interactive mode or with specified parameter 'developmentVersion'.",
                "'mvn flow:release -DdevelopmentVersion=X.Y.Z-SNAPSHOT -B' to predefine next development version",
                "'mvn flow:release' to run in interactive mode");
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, EXPECTED_RELEASE_BRANCH);
        assertDefaultDeployGoalNotExecuted();
    }

    @Test
    public void testExecuteWithResolvedConflictOnReleaseMerge() throws Exception {
        // set up
        final String COMMIT_MESSAGE_PRODUCTION = "PRODUCTION: Modified test dummy file commit";
        git.switchToBranch(repositorySet, PRODUCTION_BRANCH);
        git.createTestfile(repositorySet);
        git.modifyTestfile(repositorySet);
        git.commitAll(repositorySet, COMMIT_MESSAGE_PRODUCTION);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        git.createAndCommitTestfile(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
        userProperties.setProperty("flow.productionBranch", PRODUCTION_BRANCH);
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("developmentVersion", NEW_DEVELOPMENT_VERSION);
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result, EXPECTED_RELEASE_MERGE_CONFLICT_MESSAGE_PATTERN);
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
    }

    @Test
    public void testExecuteWithSameReleaseAndDevelopmentVersion() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.skipTestProject", "false");
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
        userProperties.setProperty("developmentVersion", RELEASE_VERSION);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "Failed to finish release process because the next develompent version is same as release version.",
                "Run 'mvn flow:release' and define a development version different from release version.\n"
                        + "Or use property '-Dflow.allowSameVersion=true' to explicitly allow same versions.",
                "'mvn flow:release -DdevelopmentVersion=X.Y.Z-SNAPSHOT' to predefine next development version "
                        + "different from the release version",
                "'mvn flow:release -Dflow.allowSameVersion=true' to explicitly allow same versions");

        git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
        git.assertExistingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
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
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
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
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteWithoutDevelopmentVersionAndAllowSameVersionTrue() throws Exception {
        // set up
        Properties userProperties = new Properties();
        userProperties.setProperty("releaseVersion", RELEASE_VERSION);
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
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteContinueAfterFailedProjectBuildonFinish() throws Exception {
        // set up
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE = "Invalid java file";
        final String COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED = "Invalid java file removed";
        git.createAndCommitTestfile(repositorySet, "src/main/java/InvalidJavaFile.java",
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        git.push(repositorySet);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.installProject", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertInstallProjectFailureException(result, GOAL, MASTER_BRANCH, "release finish");
        git.assertBranchLocalConfigValue(repositorySet, MASTER_BRANCH, "breakpoint", "releaseFinish.cleanInstall");
        repositorySet.getLocalRepoGit().rm().addFilepattern("src/main/java/InvalidJavaFile.java").call();
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED,
                COMMIT_MESSAGE_INVALID_JAVA_FILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertBranchLocalConfigsRemoved(RELEASE_BRANCH, MASTER_BRANCH);
    }

    private void assertBranchLocalConfigsRemoved(String releaseBranch, String developmentBranch) {
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "breakpoint");
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "releaseTag");
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "releaseCommit");
        git.assertBranchLocalConfigValueMissing(repositorySet, releaseBranch, "nextSnapshotVersion");
        git.assertBranchLocalConfigValueMissing(repositorySet, developmentBranch, "releaseBranch");
    }

    @Test
    public void testExecuteAfterSuccessfulReleaseStart() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        final String EXPECTED_DEVELOPMENT_COMMIT = git.currentCommit(repositorySet);
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        executeMojo(repositorySet.getWorkingDirectory(), "release-start", promptControllerMock);
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
        git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION,
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
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        executeMojo(repositorySet.getWorkingDirectory(), "release-start", promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.cleanupBeforeStart", "true");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyNoMoreInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertBranchLocalConfigsRemoved(RELEASE_BRANCH, MASTER_BRANCH);
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
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), "release-start",
                userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verifyNoMoreInteractionsAndReset(promptControllerMock);
        assertInstallProjectFailureException(result, "release-start", RELEASE_BRANCH, "release start");
        git.assertBranchLocalConfigValue(repositorySet, RELEASE_BRANCH, "breakpoint", "releaseStart.cleanInstall");
        git.commitAll(repositorySet, COMMIT_MESSAGE_INVALID_JAVA_FILE_REMOVED);
        userProperties.setProperty("flow.cleanupBeforeStart", "true");
        userProperties.setProperty("flow.installProject", "false");
        when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
        when(promptControllerMock.prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION)).thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION, POM_RELEASE_VERSION);
        verify(promptControllerMock).prompt(PROMPT_NEXT_DEVELOPMENT_VERSION, NEW_DEVELOPMENT_VERSION);
        verifyZeroInteractions(promptControllerMock);
        git.assertClean(repositorySet);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertMissingLocalBranches(repositorySet, RELEASE_BRANCH);
        git.assertMissingRemoteBranches(repositorySet, RELEASE_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_INVALID_JAVA_FILE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
        assertBranchLocalConfigsRemoved(RELEASE_BRANCH, MASTER_BRANCH);
    }

}
