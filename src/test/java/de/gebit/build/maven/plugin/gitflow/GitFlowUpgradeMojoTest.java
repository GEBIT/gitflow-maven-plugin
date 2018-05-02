//
// GitFlowUpgradeMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author Volodymyr Medvid
 */
public class GitFlowUpgradeMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "upgrade";

    private static final String FEATURE_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String FEATURE_NAME = FEATURE_ISSUE + "-someDescription";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;

    private static final String EPIC_ISSUE = TestProjects.BASIC.jiraProject + "-42";

    private static final String EPIC_NAME = EPIC_ISSUE + "-someDescription";

    private static final String EPIC_BRANCH = "epic/" + EPIC_NAME;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_BRANCH = "maintenance/gitflow-tests-" + MAINTENANCE_VERSION;

    private static final String COMMIT_MESSAGE_SET_FEATURE_VERSION = FEATURE_ISSUE
            + ": updating versions for feature branch";

    private static final String COMMIT_MESSAGE_SET_EPIC_VERSION = EPIC_ISSUE + ": updating versions for epic branch";

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
    public void testExecuteFeatureWithMissingMaintenanceBranchLocally() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH, MAINTENANCE_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(null, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_FEATURE_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteFeatureWithMissingMasterBranchLocally() throws Exception {
        // set up
        ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NAME);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);

        git.assertCurrentBranch(repositorySet, FEATURE_BRANCH);
        git.assertLocalBranches(repositorySet, FEATURE_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH, CONFIG_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, FEATURE_BRANCH);
        assertEquals("feature", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(FEATURE_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(null, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_FEATURE_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteEpicWithMissingMaintenanceBranchLocally() throws Exception {
        // set up
        ExecutorHelper.executeMaintenanceStart(this, repositorySet, MAINTENANCE_VERSION);
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MAINTENANCE_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);

        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH, MAINTENANCE_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MAINTENANCE_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(null, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_EPIC_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

    @Test
    public void testExecuteEpicWithMissingMasterBranchLocally() throws Exception {
        // set up
        ExecutorHelper.executeEpicStart(this, repositorySet, EPIC_NAME);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, MASTER_BRANCH);
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, CONFIG_BRANCH);
        git.deleteRemoteBranch(repositorySet, CONFIG_BRANCH);
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        final String EXPECTED_VERSION_CHANGE_COMMIT = git.currentCommit(repositorySet);

        git.assertCurrentBranch(repositorySet, EPIC_BRANCH);
        git.assertLocalBranches(repositorySet, EPIC_BRANCH, CONFIG_BRANCH);
        git.assertRemoteBranches(repositorySet, MASTER_BRANCH, EPIC_BRANCH, CONFIG_BRANCH);

        Properties branchConfig = git.readPropertiesFileInLocalBranch(repositorySet, CONFIG_BRANCH, EPIC_BRANCH);
        assertEquals("epic", branchConfig.getProperty("branchType"));
        assertEquals(MASTER_BRANCH, branchConfig.getProperty("baseBranch"));
        assertEquals(EPIC_ISSUE, branchConfig.getProperty("issueNumber"));
        assertEquals(null, branchConfig.getProperty("baseVersion"));
        assertEquals(COMMIT_MESSAGE_SET_EPIC_VERSION, branchConfig.getProperty("startCommitMessage"));
        assertEquals(EXPECTED_VERSION_CHANGE_COMMIT, branchConfig.getProperty("versionChangeCommit"));
    }

}
