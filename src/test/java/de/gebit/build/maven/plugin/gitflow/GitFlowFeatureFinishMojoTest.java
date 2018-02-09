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
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_NUMBER = ExecutorHelper.FEATURE_START_FEATURE_NUMBER;

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_BRANCH = "maintenance/gebit-build-" + MAINTENANCE_VERSION;

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC)) {
            // set up
            ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
            git.createAndCommitTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", git.status(repositorySet).isClean());
            assertEquals("current branch is wrong", MASTER_BRANCH, git.currentBranch(repositorySet));
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH,
                    "GBLD-NONE: Merge branch feature/" + FEATURE_NUMBER, COMMIT_MESSAGE_FOR_TESTFILE);

            verifyZeroInteractions(promptControllerMock);
        }
    }

    @Test
    public void testExecuteOnMaintenanceBranch_GBLD283() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC)) {
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
            git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH,
                    "GBLD-NONE: Merge branch feature/" + FEATURE_NUMBER + " into " + MAINTENANCE_BRANCH,
                    COMMIT_MESSAGE_FOR_TESTFILE, "NO-ISSUE: updating versions for maintenance branch");

            verifyZeroInteractions(promptControllerMock);
        }
    }
}
