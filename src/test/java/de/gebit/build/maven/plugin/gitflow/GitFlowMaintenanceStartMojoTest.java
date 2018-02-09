//
// GitFlowMaintenanceStartMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;

import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 *
 * @author VMedvid
 */
public class GitFlowMaintenanceStartMojoTest extends AbstractGitFlowMojoTestCase {

   private static final String GOAL = "maintenance-start";

    private static final String MAINTENANCE_VERSION = "1.42";

    private static final String MAINTENANCE_BRANCH = "maintenance/gebit-build-" + MAINTENANCE_VERSION;

    private static final String MAINTENANCE_FIRST_VERSION = "1.42.0-SNAPSHOT";

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC)) {
            // set up
            when(promptControllerMock.prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_SELECTING_RELEASE,
                    Arrays.asList("0", "T"))).thenReturn("0");
            when(promptControllerMock.prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION)).thenReturn(
                    MAINTENANCE_VERSION);
            when(promptControllerMock.prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION)
                    ).thenReturn(MAINTENANCE_FIRST_VERSION);
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_SELECTING_RELEASE,
                    Arrays.asList("0", "T"));
            verify(promptControllerMock).prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_VERSION);
            verify(promptControllerMock).prompt(ExecutorHelper.MAINTENANCE_START_PROMPT_MAINTENANCE_FIRST_VERSION);
            verifyNoMoreInteractions(promptControllerMock);

            assertTrue("working directory is not clean", git.status(repositorySet).isClean());
            assertEquals("current branch is wrong", MAINTENANCE_BRANCH, git.currentBranch(repositorySet));
            git.assertLocalBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH, MAINTENANCE_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MAINTENANCE_BRANCH, MAINTENANCE_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MAINTENANCE_BRANCH,
                    "NO-ISSUE: updating versions for maintenance branch");
        }
    }

}
