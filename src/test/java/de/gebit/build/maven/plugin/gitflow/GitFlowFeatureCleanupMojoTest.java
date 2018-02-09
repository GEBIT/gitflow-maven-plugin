//
// GitFlowFeatureCleanupMojoTest.java
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
public class GitFlowFeatureCleanupMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase-cleanup";

    private static final String FEATURE_NUMBER = "GBLD-42";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_SET_VERSION_COMMIT_MESSAGE = "GBLD-42: updating versions for feature branch";

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
            assertEquals("current branch is wrong", FEATURE_BRANCH, git.currentBranch(repositorySet));
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                    EXPECTED_SET_VERSION_COMMIT_MESSAGE);
            git.assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
            git.assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                    EXPECTED_SET_VERSION_COMMIT_MESSAGE);

            git.assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE);

            verifyZeroInteractions(promptControllerMock);
        }
    }

}
