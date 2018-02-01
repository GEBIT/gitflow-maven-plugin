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
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author VMedvid
 */
public class GitFlowFeatureFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-finish";

    private static final String FEATURE_NUMBER = "GBLD-42";

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
}
