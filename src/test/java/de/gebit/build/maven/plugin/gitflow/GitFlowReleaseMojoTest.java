//
// GitFlowReleaseMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Properties;

import org.apache.maven.execution.MavenExecutionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodymyr Medvid
 */
public class GitFlowReleaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String MAINTENANCE_VERSION = "2.0";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "2.0.0-SNAPSHOT";

    private static final String MAINTENANCE_RELEASE_VERSION = "2.0.0";

    private static final String PRODUCTION_BRANCH = "latest";

    private static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-" + MAINTENANCE_BRANCH;

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String POM_RELEASE_VERSION = ExecutorHelper.RELEASE_START_POM_RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_RELEASE_START_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development version";

    private static final String COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE = "NO-ISSUE: updating versions for"
            + " maintenance branch";

    private static final String PROMPT_RELEASE_VERSION = ExecutorHelper.RELEASE_START_PROMPT_RELEASE_VERSION;

    private static final String PROMPT_NEXT_DEVELOPMENT_VERSION = "What is the next development version?";

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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
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

    @Test
    public void testExecuteOnMaintenanceBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
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
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, PRODUCTION_BRANCH, PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, PRODUCTION_BRANCH, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

    @Test
    public void testExecuteOnMaintenanceWithNotExistingProductionBranch() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION, MAINTENANCE_FIRST_VERSION);
        Properties userProperties = new Properties();
        userProperties.setProperty("flow.noProduction", "false");
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
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, MAINTENANCE_PRODUCTION_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH, MAINTENANCE_PRODUCTION_BRANCH);
        git.assertLocalTags(repositorySet, RELEASE_TAG);
        git.assertRemoteTags(repositorySet, RELEASE_TAG);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                MAINTENANCE_PRODUCTION_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_PRODUCTION_BRANCH,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
        git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                COMMIT_MESSAGE_RELEASE_START_SET_VERSION, COMMIT_MESSAGE_SET_VERSION_FOR_MAINTENANCE);
        assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
        assertDefaultDeployGoalExecuted();
    }

}
