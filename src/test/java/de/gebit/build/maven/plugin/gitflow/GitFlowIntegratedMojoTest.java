//
// GitFlowIntegratedMojoTest.java
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
public class GitFlowIntegratedMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "integrated";

    private static final String INTEGRATION_BRANCH = "integration/" + MASTER_BRANCH;

    private static final String PROMPT_INTEGRATION_BRANCH_NAME = "What is the integration branch name?";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            when(promptControllerMock.prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH)).thenReturn("");
            git.createAndCommitTestfile(repositorySet);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_INTEGRATION_BRANCH_NAME, INTEGRATION_BRANCH);
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, INTEGRATION_BRANCH, INTEGRATION_BRANCH);
            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, INTEGRATION_BRANCH);

            git.assertLocalAndRemoteBranchesAreDifferent(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
        }
    }

}
