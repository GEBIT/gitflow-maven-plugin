//
// GitFlowHotfixFinishMojoTest.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static de.gebit.build.maven.plugin.gitflow.jgit.GitExecution.COMMIT_MESSAGE_FOR_TESTFILE;
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
public class GitFlowHotfixFinishMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "hotfix-finish";

    private static final String HOTFIX_VERSION = "1.42.0";

    private static final String HOTFIX_TAG = "gitflow-tests-" + HOTFIX_VERSION;

    private static final String NEXT_DEVELPMENT_VERSION = "1.42.1-SNAPSHOT";

    public static final String PROMPT_HOTFIX_VERSION = "Hotfix branches:" + LS + "1. hotfix/" + HOTFIX_VERSION + LS
            + "Choose hotfix branch to finish";

    private static final String COMMIT_MESSAGE_HOTFIX_START_SET_VERSION = "NO-ISSUE: updating versions for hotfix";

    private static final String COMMIT_MESSAGE_HOTFIX_FINISH_SET_VERSION = "NO-ISSUE: updating for next development version";

    private static final String COMMIT_MESSAGE_MERGE_HOTFIX = TestProjects.BASIC.jiraProject
            + "-NONE: Merge branch hotfix/" + HOTFIX_VERSION;

    @Test
    public void testExecute() throws Exception {
        try (RepositorySet repositorySet = git.createGitRepositorySet(TestProjects.BASIC.basedir)) {
            // set up
            ExecutorHelper.executeHotfixStart(this, repositorySet, HOTFIX_VERSION);
            git.createAndCommitTestfile(repositorySet);
            when(promptControllerMock.prompt(PROMPT_HOTFIX_VERSION, Arrays.asList("1"))).thenReturn("1");
            // test
            executeMojo(repositorySet.getWorkingDirectory(), GOAL, promptControllerMock);
            // verify
            verify(promptControllerMock).prompt(PROMPT_HOTFIX_VERSION, Arrays.asList("1"));
            verifyNoMoreInteractions(promptControllerMock);

            git.assertClean(repositorySet);
            git.assertCurrentBranch(repositorySet, MASTER_BRANCH);
            git.assertLocalBranches(repositorySet, MASTER_BRANCH);
            git.assertRemoteBranches(repositorySet, MASTER_BRANCH);

            git.assertLocalAndRemoteBranchesAreIdentical(repositorySet, MASTER_BRANCH, MASTER_BRANCH);
            git.assertCommitsInLocalBranch(repositorySet, MASTER_BRANCH, COMMIT_MESSAGE_HOTFIX_FINISH_SET_VERSION,
                    COMMIT_MESSAGE_MERGE_HOTFIX, COMMIT_MESSAGE_FOR_TESTFILE, COMMIT_MESSAGE_HOTFIX_START_SET_VERSION);
            git.assertLocalTags(repositorySet, HOTFIX_TAG);
            git.assertRemoteTags(repositorySet, HOTFIX_TAG);

            assertVersionsInPom(repositorySet.getWorkingDirectory(), NEXT_DEVELPMENT_VERSION);
        }
    }

}
