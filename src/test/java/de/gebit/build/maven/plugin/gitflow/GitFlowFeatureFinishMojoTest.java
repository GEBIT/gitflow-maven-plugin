//
// GitFlowFeatureFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_NUMBER = "GBLD-42";

    private static final String MAINTENANCE_VERSION = "1.42";
    private static final String MAINTENANCE_BRANCH = "maintenance/gebit-build-" + MAINTENANCE_VERSION;
    private static final String MAINTENANCE_SNAPSHOT_VERSION = "1.42.0-SNAPSHOT";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = prepareGitRepo(TestProjects.BASIC)) {
            // set up
            executeFeatureStart(repositorySet, FEATURE_NUMBER);
            gitCreateAndCommitTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingBasedir(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", gitStatus(repositorySet).isClean());
            assertEquals("current branch is wrong", "master", gitCurrentBranch(repositorySet));
            assertLocalBranches(repositorySet, "master");
            assertRemoteBranches(repositorySet, "master");

            assertLocalAndRemoteBranchesReferenceSameCommit(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, "GBLD-NONE: Merge branch feature/GBLD-42",
                    COMMIT_MESSAGE_FOR_TESTFILE);
            assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH, "GBLD-NONE: Merge branch feature/GBLD-42",
                    COMMIT_MESSAGE_FOR_TESTFILE);

            verifyZeroInteractions(promptControllerMock);
        }
    }

    @Test
    public void testExecuteOnMaintenanceBranch_GBLD283() throws Exception {
        try (RepositorySet repositorySet = prepareGitRepo(TestProjects.BASIC)) {
            // set up
            executeMaintenanceStart(repositorySet);
            executeFeatureStart(repositorySet, FEATURE_NUMBER);
            gitCreateAndCommitTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingBasedir(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", gitStatus(repositorySet).isClean());
            assertEquals("current branch is wrong", MAINTENANCE_BRANCH, gitCurrentBranch(repositorySet));
            assertLocalBranches(repositorySet, "master", MAINTENANCE_BRANCH);
            assertRemoteBranches(repositorySet, "master", MAINTENANCE_BRANCH);

            assertLocalAndRemoteBranchesReferenceSameCommit(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

            assertLocalAndRemoteBranchesReferenceSameCommit(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
            assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH,
                    "GBLD-NONE: Merge branch feature/GBLD-42 into " + MAINTENANCE_BRANCH,
                    COMMIT_MESSAGE_FOR_TESTFILE,
                    "NO-ISSUE: updating versions for maintenance branch");

            verifyZeroInteractions(promptControllerMock);
        }
    }

    private void executeMaintenanceStart(RepositorySet repositorySet) throws Exception {
        when(promptControllerMock.prompt(eq("Release:" + LS
                + "0. <current commit>" + LS
                + "1. " + LS
                + "T. <prompt for explicit tag name>" + LS
                + "Choose release to create the maintenance branch from or enter a custom tag or release name"),
                eq(Arrays.asList("0", "1", "T")))).thenReturn("0");
        when(promptControllerMock.prompt("What is the maintenance version? [1.2]")).thenReturn(MAINTENANCE_VERSION);
        when(promptControllerMock.prompt("What is the first version on the maintenance branch? [1.2.4-SNAPSHOT]")
                ).thenReturn(MAINTENANCE_SNAPSHOT_VERSION);
        executeMojo(repositorySet.getWorkingBasedir(), "maintenance-start", promptControllerMock);
        reset(promptControllerMock);
    }
}
