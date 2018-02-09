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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowFeatureRebaseMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase";

    private static final String FEATURE_NUMBER = ExecutorHelper.FEATURE_START_FEATURE_NUMBER;

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String COMMIT_MESSAGE_MASTER = "MASTER: Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FEATURE = "FEATURE: Unit test dummy file commit";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC)) {
            // set up
            ExecutorHelper.executeFeatureStart(this, repositorySet, FEATURE_NUMBER);
            git.switchToBranch(repositorySet, MASTER_BRANCH);
            git.createAndCommitTestfile(repositorySet, "master_testfile.txt", COMMIT_MESSAGE_MASTER);
            git.push(repositorySet);
            git.switchToBranch(repositorySet, FEATURE_BRANCH);
            git.createAndCommitTestfile(repositorySet, "feature_testfile.txt", COMMIT_MESSAGE_FEATURE);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            assertTrue("working directory is not clean", git.status(repositorySet).isClean());
            assertEquals("current branch is wrong", FEATURE_BRANCH, git.currentBranch(repositorySet));
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_MASTER);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, FEATURE_BRANCH, FEATURE_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FEATURE,
                    ExecutorHelper.FEATURE_START_COMMIT_MESSAGE_SET_VERSION,
                    COMMIT_MESSAGE_MASTER);

            verifyZeroInteractions(promptControllerMock);
        }
    }

}
