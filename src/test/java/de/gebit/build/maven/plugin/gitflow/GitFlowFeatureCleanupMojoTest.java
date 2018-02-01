//
// GitFlowFeatureCleanupMojoTest.java
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
 * @author VMedvid
 */
public class GitFlowFeatureCleanupMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-rebase-cleanup";

    private static final String FEATURE_NUMBER = "GBLD-42";

    private static final String FEATURE_BRANCH = "feature/" + FEATURE_NUMBER;

    private static final String EXPECTED_SET_VERSION_COMMIT_MESSAGE = "GBLD-42: updating versions for feature branch";

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
            assertEquals("current branch is wrong", FEATURE_BRANCH, gitCurrentBranch(repositorySet));
            assertLocalBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);
            assertRemoteBranches(repositorySet, MASTER_BRANCH, FEATURE_BRANCH);

            assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
            assertCommitsInLocalBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                    EXPECTED_SET_VERSION_COMMIT_MESSAGE);
            assertCommitsInRemoteBranch(repositorySet, MASTER_BRANCH);
            assertCommitsInRemoteBranch(repositorySet, FEATURE_BRANCH, COMMIT_MESSAGE_FOR_TESTFILE,
                    EXPECTED_SET_VERSION_COMMIT_MESSAGE);

            assertCommitMesaagesInGitEditorForInteractiveRebase(COMMIT_MESSAGE_FOR_TESTFILE);

            verifyZeroInteractions(promptControllerMock);
        }
    }

}
