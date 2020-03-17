//
// GitFlowFeatureResetToRemoteMojoTest.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodja
 */
public class GitFlowFeatureResetToRemoteMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-reset-to-remote";

    private static final String LOCAL_TESTFILE = "local-testfile.txt";

    private static final String REMOTE_TESTFILE = "remote-testfile.txt";

    private static final String FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;

    private static final String COMMIT_MESSAGE_SET_VERSION = BasicConstants.SINGLE_FEATURE_VERSION_COMMIT_MESSAGE;

    private static final String COMMIT_MESSAGE_LOCAL_TESTFILE = "LOCAL: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_REMOTE_TESTFILE = "REMOTE: Unit test dummy file commit";

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT_TEMPLATE = "Feature branches:" + LS + "1. {0}" + LS
            + "Choose feature branch to reset to remote state";

    private static final String PROMPT_MESSAGE_FEATURE_REBASED = "Feature branch seems to be rebased remotely.\n"
            + "If you continue all commits that were not pushed before rebase will be discarded. Continue?";

    private static final String PROMPT_MESSAGE_LOCAL_AHEAD = "Either you have commits on feature branch that were not "
            + "yet pushed or remote feature branch was reset to an older state.\n"
            + "If you continue these commits will be discarded. Continue?";

    private static final String PROMPT_MESSAGE_UNCOMMITTED_CHANGES = "You have some uncommitted files.\n"
            + "Select whether you want to (s)tash the changes before reaset and unstash them afterwards or "
            + "to (d)iscarded the local changes or to (a)bort the reset process.";

    private RepositorySet repositorySet;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
        userProperties = new Properties();
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
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
    public void testExecuteNoFeatureBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result, "There are no feature branches in your repository.", null);
    }

    @Test
    public void testExecuteOnFeatureBranchSameCommit() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchSameCommit() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    private String promptMessageOneFeatureSelect() {
        return promptMessageOneFeatureSelect(FEATURE_BRANCH);
    }

    private String promptMessageOneFeatureSelect(String featureBranch) {
        return MessageFormat.format(PROMPT_MESSAGE_ONE_FEATURE_SELECT_TEMPLATE, featureBranch);
    }

    @Test
    public void testExecuteOnFeatureBranchDivergent() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnFeatureBranchDivergentAnswerNo() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-reset-to-remote process.", null);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchDivergent() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteDivergentAndBranchName() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteDivergentAndNotFeatureBranchName() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        userProperties.setProperty("branchName", MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Branch '" + MASTER_BRANCH + "' defined in 'branchName' property is not a feature branch.",
                "Please define a feature branch in order to proceed.");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchDivergentAnswerNo() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-reset-to-remote process.", null);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnFeatureBranchRemoteAhead() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchRemoteAhead() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
    }

    @Test
    public void testExecuteOnFeatureBranchLocalAhead() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnFeatureBranchLocalAheadAnswerNo() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-reset-to-remote process.", null);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchLocalAhead() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteOnMasterBranchLocalAheadAnswerNo() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        when(promptControllerMock.prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y")).thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(promptMessageOneFeatureSelect(), Arrays.asList("1"));
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_LOCAL_AHEAD, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-reset-to-remote process.", null);
        git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE);
    }

    @Test
    public void testExecuteWithUncommittedChangesAnswerStash() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.modifyTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s"))
                .thenReturn("s");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContentModified(repositorySet);
        git.assertNoStashes(repositorySet);
    }

    @Test
    public void testExecuteWithUncommittedChangesAnswerDiscard() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.modifyTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s"))
                .thenReturn("d");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertNoStashes(repositorySet);
    }

    @Test
    public void testExecuteWithUncommittedChangesAnswerAbort() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.modifyTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s"))
                .thenReturn("a");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "You have aborted feature-reset-to-remote process.", null);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContentModified(repositorySet);
        git.assertNoStashes(repositorySet);
    }

    @Test
    public void testExecuteWithUncommittedChangesAndFailedUnstash() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        final String EXPECTED_INITIAL_COMMIT = git.currentCommit(repositorySet);
        git.createAndAddToIndexTestfile(repositorySet, REMOTE_TESTFILE);
        git.modifyTestfile(repositorySet, REMOTE_TESTFILE);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s"))
                .thenReturn("s");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_UNCOMMITTED_CHANGES, Arrays.asList("s", "d", "a"), "s");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Local feature branch was reset to remote state but changes stashed before reset couldn't be applied "
                        + "afterwards.",
                "Apply stashed changes manually and resolve conflicts or reset local feature branch to initial state.",
                "'git stash apply' to apply stashed changes", "'git stash drop' to remove the stash after applying",
                "'git reset --hard " + EXPECTED_INITIAL_COMMIT + "' to reset local feature branch to initial state");
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContent(repositorySet, REMOTE_TESTFILE);
        git.assertStashesRegEx(repositorySet, ".*stashed by gitflow before feature reset \\(.*\\)");
    }

    @Test
    public void testExecuteWithUncommittedChangesAndStashChangesTrue() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.modifyTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        userProperties.setProperty("stashChanges", "true");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContentModified(repositorySet);
        git.assertNoStashes(repositorySet);
    }

    @Test
    public void testExecuteWithUncommittedChangesAndStashChangesFalse() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
        git.createAndCommitTestfile(repositorySet, LOCAL_TESTFILE, COMMIT_MESSAGE_LOCAL_TESTFILE);
        git.modifyTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, REMOTE_TESTFILE,
                COMMIT_MESSAGE_REMOTE_TESTFILE);
        userProperties.setProperty("stashChanges", "false");
        when(promptControllerMock.prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y")).thenReturn("y");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_FEATURE_REBASED, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_SET_VERSION,
                GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_REMOTE_TESTFILE);
        git.assertTestfileContent(repositorySet);
        git.assertNoStashes(repositorySet);
    }

}
