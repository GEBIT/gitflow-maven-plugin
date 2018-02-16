//
// GitFlowReleaseStartMojoTest.java
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

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowReleaseStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "release-start";

    private static final String RELEASE_VERSION = "1.42.0";

    private static final String RELEASE_BRANCH = "release/gitflow-tests-" + RELEASE_VERSION;

    private static final String PROMPT_RELEASE_VERSION = ExecutorHelper.RELEASE_START_PROMPT_RELEASE_VERSION;

    private static final String COMMIT_MESSAGE_SET_VERSION = "NO-ISSUE: updating versions for release";

    private static final String GIT_CONFIG_VALUE = MASTER_BRANCH;

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_RELEASE_VERSION)).thenReturn(RELEASE_VERSION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_RELEASE_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, RELEASE_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, RELEASE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, RELEASE_BRANCH, COMMIT_MESSAGE_SET_VERSION);
            git.assertConfigValue(repositorySet, "branch", RELEASE_BRANCH, "development", GIT_CONFIG_VALUE);
            assertVersionsInPom(repositorySet.getWorkingDirectory(), RELEASE_VERSION);
        }
    }

}
