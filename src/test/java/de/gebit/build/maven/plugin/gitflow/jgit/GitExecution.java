//
// GitExecution.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import de.gebit.build.maven.plugin.gitflow.WorkspaceUtils;
import de.gebit.build.maven.plugin.gitflow.jgit.GitRebaseTodo.GitRebaseTodoEntry;

/**
 * Git execution provides methods to execute different git operation using jGit.
 *
 * @author VMedvid
 */
public class GitExecution {

    public static final String COMMIT_MESSAGE_FOR_TESTFILE = "Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP = "Unit test set-up initial commit";

    private static final String GIT_BASEDIR_REMOTE_SUFFIX = "origin.git";

    private static final String GIT_BASEDIR_LOCAL_SUFFIX = "working";

    private static final String REFS_HEADS_PATH = "refs/heads/";

    private String gitBaseDir;

    /**
     * Creates an instance of git execution.
     *
     * @param aGitBasedir
     *            the base directory where git repository set will be created.
     */
    public GitExecution(String aGitBasedir) {
        this.gitBaseDir = aGitBasedir;
    }

    /**
     * Removes the git base directory.
     *
     * @throws IOException
     */
    public void cleanupGitBasedir() throws IOException {
        File gitBasedir = new File(gitBaseDir);
        int maxTries = 10;
        while (gitBasedir.exists()) {
            try {
                FileUtils.deleteDirectory(gitBasedir);
            } catch (IOException ex) {
                // wait some time
                try {
                    Thread.sleep(500);
                } catch (InterruptedException exc) {
                    Thread.currentThread().interrupt();
                }
                if (maxTries-- <= 0) {
                    throw ex;
                }
            }
        }
    }

    /**
     * Creates a remote git repositories in target directory and clones a local
     * repository. Copies files from the passed sourceBasedir to the local
     * repository. Commits all files and pushes them to the remote repository.
     *
     * @param sourceBasedir
     *            the source directory of the project that will be used in
     *            remote and local repositories
     * @return a repository set that contains references to the remote and local
     *         repositories
     * @throws IOException
     *             if the source directory can't be copied to the target
     *             directory
     * @throws GitAPIException
     *             if an error occurs while executiong of git commands
     */
    public RepositorySet createGitRepositorySet(File sourceBasedir) throws IOException, GitAPIException {
        String basedirName = sourceBasedir.getName();
        File repoBasedir = new File(gitBaseDir, basedirName);
        File remoteRepoBasedir = new File(repoBasedir, GIT_BASEDIR_REMOTE_SUFFIX);
        File localRepoBasedir = new File(repoBasedir, GIT_BASEDIR_LOCAL_SUFFIX);
        Git remoteGit = Git.init().setDirectory(remoteRepoBasedir).setBare(true).call();
        Git localGit = null;
        try {
            localGit = Git.cloneRepository().setURI(remoteGit.getRepository().getDirectory().getAbsolutePath())
                    .setDirectory(localRepoBasedir).call();
            setGitDummyEditorToConfig(localGit);
            FileUtils.copyDirectory(sourceBasedir, localRepoBasedir);
            localGit.add().addFilepattern(".").call();
            localGit.commit().setMessage(COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP).call();
            localGit.push().call();
            return new RepositorySet(remoteGit, localGit);
        } catch (GitAPIException | IOException tempExc) {
            remoteGit.close();
            if (localGit != null) {
                localGit.close();
            }
            throw tempExc;
        }
    }

    /**
     * Creates a test file in local repository and commits it.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createAndCommitTestfile(RepositorySet repositorySet) throws GitAPIException, IOException {
        createAndCommitTestfile(repositorySet, "testfile.txt", COMMIT_MESSAGE_FOR_TESTFILE);
    }

    /**
     * Creates a test file in local repository and commits it.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @param filename
     *            the name of the file to be created and commited
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createAndCommitTestfile(RepositorySet repositorySet, String filename, String commitMessage)
            throws GitAPIException, IOException {
        File testFile = new File(repositorySet.getWorkingDirectory(), filename);
        FileUtils.write(testFile, "dummy content", "UTF-8");
        repositorySet.getLocalRepoGit().add().addFilepattern(".").call();
        repositorySet.getLocalRepoGit().commit().setMessage(commitMessage).call();
    }

    /**
     * Returns the result of "git status" command.
     *
     * @param aRepositorySet
     *            the repository where git command should be executed
     * @return the status of the git repository
     * @throws GitAPIException
     *             if an error occurs on command execution
     */
    public Status status(RepositorySet aRepositorySet) throws GitAPIException {
        return aRepositorySet.getLocalRepoGit().status().call();
    }

    /**
     * Returns the name of current branch in local repository
     *
     * @param aRepositorySet
     *            the repository to be used
     * @return the name of current branch in local repository
     * @throws IOException
     *             in case of an I/O error
     */
    public String currentBranch(RepositorySet aRepositorySet) throws IOException {
        return aRepositorySet.getLocalRepoGit().getRepository().getBranch();
    }

    /**
     * Returns a list with names of branches in local repository.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @return list with names of branches in local repository
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public List<String> localBranches(RepositorySet aRepositorySet) throws GitAPIException {
        return branches(aRepositorySet.getLocalRepoGit());
    }

    /**
     * Returns a list with names of branches in remote repository.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @return list with names of branches in remote repository
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public List<String> remoteBranches(RepositorySet aRepositorySet) throws GitAPIException {
        return branches(aRepositorySet.getRemoteRepoGit());
    }

    public List<String> branches(Git git) throws GitAPIException {
        List<String> branches = new ArrayList<String>();
        List<Ref> branchRefs = git.branchList().call();
        for (Ref branchRef : branchRefs) {
            branches.add(branchNameByRef(branchRef));
        }
        return branches;
    }

    /**
     * Returns the name of the branch by the reference. E.g., for
     * "refs/heads/master" will be returned "master".
     *
     * @param aBranchRef
     *            the reference of a branch
     * @return the name of the referenced branch
     */
    public String branchNameByRef(Ref aBranchRef) {
        return StringUtils.substringAfter(aBranchRef.getName(), REFS_HEADS_PATH);
    }

    /**
     * Swiches to the passed branch.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to switch to
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void switchToBranch(RepositorySet aRepositorySet, String aBranch) throws GitAPIException {
        aRepositorySet.getLocalRepoGit().checkout().setName(aBranch).call();
    }

    /**
     * Pushes the local repository to remote.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void push(RepositorySet aRepositorySet) throws GitAPIException {
        aRepositorySet.getLocalRepoGit().push().call();
    }

    /**
     * Asserts that local repository consists of passed expected branches.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertLocalBranches(RepositorySet aRepositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = localBranches(aRepositorySet);
        assertBranches(expectedBranches, branches, "local");
    }

    /**
     * Asserts that remote repository consists of passed expected branches.
     *
     * @param aRepositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertRemoteBranches(RepositorySet aRepositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = remoteBranches(aRepositorySet);
        assertBranches(expectedBranches, branches, "remote");
    }

    @SuppressWarnings("null")
    private void assertBranches(String[] expectedBranches, List<String> branches, String repoName) {
        int expectedBranchesNumber = (expectedBranches == null) ? 0 : expectedBranches.length;
        if (expectedBranchesNumber > 0) {
            for (String expectedBranch : expectedBranches) {
                if (!branches.contains(expectedBranch)) {
                    fail("branch '" + expectedBranch + "' is missing in " + repoName + " repository");
                }
            }
        }
        assertEquals("number of branches in " + repoName + " repository is other then expected", expectedBranchesNumber,
                branches.size());
    }

    /**
     * Asserts that the log of passed branch in local repository consists of
     * passed expected commit messages.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the branch to be checked
     * @param expectedCommitMessages
     *            the expected commit messages
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertCommitsInLocalBranch(RepositorySet repositorySet, String branch,
            String... expectedCommitMessages) throws GitAPIException, IOException {
        List<String> commitMessages = commitMessagesInBranch(repositorySet.getLocalRepoGit(), branch);
        assertCommitMessages(expectedCommitMessages, commitMessages, branch, "local");
    }

    /**
     * Asserts that the log of passed branch in remote repository consists of
     * passed expected commit messages.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the branch to be checked
     * @param expectedCommitMessages
     *            the expected commit messages
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertCommitsInRemoteBranch(RepositorySet repositorySet, String branch,
            String... expectedCommitMessages) throws GitAPIException, IOException {
        List<String> commitMessages = commitMessagesInBranch(repositorySet.getRemoteRepoGit(), branch);
        assertCommitMessages(expectedCommitMessages, commitMessages, branch, "remote");
    }

    private List<String> commitMessagesInBranch(Git git, String branch) throws GitAPIException, IOException {
        List<String> commitMessages = new ArrayList<String>();
        List<RevCommit> commits = readCommits(git, branch);
        for (RevCommit commit : commits) {
            commitMessages.add(commit.getFullMessage().trim());
        }
        return commitMessages;
    }

    private void assertCommitMessages(String[] expectedCommitMessages, List<String> commitMessages, String branch,
            String repoName) {
        assertArrayEquals(
                "Commit messages in branch '" + branch + "' of '" + repoName
                        + "' repository are different from expected",
                expectedCommitMessages, commitMessages.toArray(new String[commitMessages.size()]));
    }

    /**
     * Asserts that passed local and remote branches are identical (reference
     * same commit).
     *
     * @param repositorySet
     *            the repository to be used
     * @param localBranch
     *            the local branch to be checked
     * @param remoteBranch
     *            the remote branch to be checked
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertLocalAndRemoteBranchesAreIdentical(RepositorySet repositorySet, String localBranch,
            String remoteBranch) throws IOException {
        ObjectId localBranchObjectId = objectIdOfBranch(repositorySet.getLocalRepoGit(), localBranch);
        ObjectId remoteBranchObjectId = objectIdOfBranch(repositorySet.getRemoteRepoGit(), remoteBranch);
        assertEquals("remote branch reference different commit then local branch", localBranchObjectId.getName(),
                remoteBranchObjectId.getName());
    }

    private List<RevCommit> readCommits(Git git, String branch, String... commitMessagesToBeExcluded)
            throws GitAPIException, IOException {
        List<RevCommit> commits = new ArrayList<RevCommit>();
        ObjectId branchObjectId = objectIdOfBranch(git, branch);
        Iterable<RevCommit> commitsIterator = git.log().add(branchObjectId)
                .setRevFilter(createUnitTestRevFilter(commitMessagesToBeExcluded)).call();
        for (RevCommit tempRevCommit : commitsIterator) {
            commits.add(tempRevCommit);
        }
        return commits;
    }

    private ObjectId objectIdOfBranch(Git git, String branch) throws IOException {
        return git.getRepository().resolve(REFS_HEADS_PATH + branch);
    }

    private RevFilter createUnitTestRevFilter(String... commitMessagesToBeExcluded) {
        List<String> excludedMessages = new ArrayList<String>();
        excludedMessages.add(COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP);
        if (commitMessagesToBeExcluded != null) {
            for (String messageToBeExcluded : commitMessagesToBeExcluded) {
                excludedMessages.add(messageToBeExcluded);
            }
        }
        return new CommitRevFilter(excludedMessages);
    }

    /**
     * Asserts that git editor lists passed expected commit messages while
     * interactive rebase.
     *
     * @param expectedCommitMessages
     *            the expected commit messages
     * @throws FileNotFoundException
     *             if git-rebase-todo file copied by dummy editor can't be found
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertCommitMesaagesInGitEditorForInteractiveRebase(String... expectedCommitMessages)
            throws FileNotFoundException, IOException {
        List<String> commitMessages = readCommitMesaagesForInteractiveRebaseInGitEditor();
        assertArrayEquals("Commit messages in git editor for interactive rebase are different from expected",
                expectedCommitMessages, commitMessages.toArray(new String[commitMessages.size()]));
    }

    private List<String> readCommitMesaagesForInteractiveRebaseInGitEditor() throws FileNotFoundException, IOException {
        List<String> commitMessages = new ArrayList<String>();
        GitRebaseTodo gitRebaseTodo = loadGitRebaseTodoUsedInGitDummyEditor();
        for (GitRebaseTodoEntry gitRebaseTodoEntry : gitRebaseTodo.getEntries()) {
            commitMessages.add(gitRebaseTodoEntry.getMessage());
        }
        return commitMessages;
    }

    /**
     * Loads the information shown in git editor while interactive rebase.
     *
     * @return the container with information shown in git editor while
     *         interactive rebase
     * @throws FileNotFoundException
     *             if git-rebase-todo file copied by dummy editor can't be found
     * @throws IOException
     *             in case of an I/O error
     */
    public GitRebaseTodo loadGitRebaseTodoUsedInGitDummyEditor() throws FileNotFoundException, IOException {
        return GitRebaseTodo.load(new File(gitBaseDir, GitDummyEditor.EDIT_FILE_RELATIVE_PATH));
    }

    private void setGitDummyEditorToConfig(Git git) throws IOException {
        StoredConfig tempConfig = git.getRepository().getConfig();
        tempConfig.setString("core", null, "editor", getGitDummyEditorCMD());
        tempConfig.save();
    }

    private String getGitDummyEditorCMD() {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("'");
        cmdBuilder.append(WorkspaceUtils.getJavaExecutable().getAbsolutePath());
        cmdBuilder.append("' -cp '");
        cmdBuilder.append(WorkspaceUtils.getWorkspaceTestClasspath().getAbsolutePath());
        cmdBuilder.append("' ");
        String editorTargetPropertyKey = GitDummyEditor.PROPERTY_KEY_TARGET_BASEDIR;
        String editorTargetPropertyValue = new File(gitBaseDir).getAbsolutePath();
        cmdBuilder.append("-D");
        cmdBuilder.append(editorTargetPropertyKey);
        cmdBuilder.append("='");
        cmdBuilder.append(editorTargetPropertyValue);
        cmdBuilder.append("' ");
        cmdBuilder.append(GitDummyEditor.class.getName());
        return cmdBuilder.toString();
    }

    private class CommitRevFilter extends RevFilter {

        private List<String> excludedMessages;

        public CommitRevFilter(List<String> aExcludedMessages) {
            this.excludedMessages = aExcludedMessages == null ? null : new ArrayList<String>(aExcludedMessages);
        }

        @Override
        public boolean include(RevWalk aWalker, RevCommit aCmit)
                throws StopWalkException, MissingObjectException, IncorrectObjectTypeException, IOException {
            if (excludedMessages.contains(aCmit.getShortMessage())) {
                return false;
            }
            return true;
        }

        @Override
        public RevFilter clone() {
            return new CommitRevFilter(excludedMessages);
        }

    }

}
