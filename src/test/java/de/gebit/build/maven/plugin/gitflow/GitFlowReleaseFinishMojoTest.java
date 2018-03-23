//
// GitFlowReleaseFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowReleaseFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-finish";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String NEW_DEVELOPMENT_VERSION = "1.42.1-SNAPSHOT";

    private static final String RELEASE_TAG = "gitflow-tests-" + RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_RELEASE_START_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION = "NO-ISSUE: updating for next development version";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeReleaseStart(this, repositorySet, RELEASE_VERSION);
            git.createAndCommitTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verifyZeroInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertLocalTags(repositorySet, RELEASE_TAG);
            git.assertRemoteTags(repositorySet, RELEASE_TAG);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_RELEASE_FINISH_SET_VERSION,
                    GitExecution.COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_RELEASE_START_SET_VERSION);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), NEW_DEVELOPMENT_VERSION);
            assertArtifactDeployed(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
            assertSiteDeployed(repositorySet.getWorkingDirectory());
        }
    }

}
