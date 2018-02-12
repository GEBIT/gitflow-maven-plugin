//
// GitFlowHotfixStartMojoTest.java
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
public class GitFlowHotfixStartMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "hotfix-start";

    private static final String PROMPT_HOTFIX_VERSION = ExecutorHelper.HOTFIX_START_PROMPT_HOTFIX_VERSION;

    private static final String HOTFIX_VERSION = "1.42.0";

    private static final String HOTFIX_BRANCH = "hotfix/" + HOTFIX_VERSION;

    private static final String COMMIT_MESSAGE_HOTFIX_SET_VERSION = "NO-ISSUE: updating versions for hotfix";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_HOTFIX_VERSION)).thenReturn(HOTFIX_VERSION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_HOTFIX_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, HOTFIX_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, HOTFIX_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, HOTFIX_BRANCH, COMMIT_MESSAGE_HOTFIX_SET_VERSION);
            assertVersionInPom(repositorySet.getWorkingDirectory(), HOTFIX_VERSION);
        }
    }

}
