//
// GitFlowFeatureAbortMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowFeatureAbortMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-abort";

    private static final String FEATURE_NUMBER = ExecutorHelper.FEATURE_START_FEATURE_NUMBER;

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
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

            verifyZeroInteractions(promptControllerMock);
        }
    }

}
