//
// GitExecution.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import de.gebit.build.maven.plugin.gitflow.WorkspaceUtils;
import de.gebit.build.maven.plugin.gitflow.jgit.GitRebaseTodo.GitRebaseTodoEntry;

/**
 * Git execution provides methods to execute different git operation using jGit.
 *
 * @author VMedvid
 */
public class GitExecution {

    public static final String TESTFILE_CONTENT = "dummy content";

    public static final String TESTFILE_CONTENT_MODIFIED = "dummy content 2";

    public static final String TESTFILE_NAME = "testfile.txt";

    public static final String COMMIT_MESSAGE_FOR_TESTFILE = "Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP = "Unit test set-up initial commit";

    private static final String GIT_BASEDIR_REMOTE_SUFFIX = "origin.git";

    private static final String GIT_BASEDIR_LOCAL_SUFFIX = "working";

    private static final String GIT_BASEDIR_REMOTE_CLONE_SUFFIX = "cloned-origin";

    private static final String REFS_HEADS_PATH = "refs/heads/";

    private static final String REFS_TAGS_PATH = "refs/tags/";

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
        File clonedRemoteRepoBasedir = new File(repoBasedir, GIT_BASEDIR_REMOTE_CLONE_SUFFIX);
        FileUtils.copyFileToDirectory(new File(sourceBasedir.getParentFile(), "parent-pom.xml"), repoBasedir);
        Git remoteGit = Git.init().setDirectory(remoteRepoBasedir).setBare(true).call();
        Git localGit = null;
        Git clonedRemoteGit = null;
        try {
            localGit = Git.cloneRepository().setURI(remoteGit.getRepository().getDirectory().getAbsolutePath())
                    .setDirectory(localRepoBasedir).call();
            setGitDummyEditorToConfig(localGit);
            FileUtils.copyDirectory(sourceBasedir, localRepoBasedir);
            localGit.add().addFilepattern(".").call();
            localGit.commit().setMessage(COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP).call();
            localGit.push().call();
            clonedRemoteGit = Git.cloneRepository().setURI(remoteGit.getRepository().getDirectory().getAbsolutePath())
                    .setDirectory(clonedRemoteRepoBasedir).call();
            return new RepositorySet(remoteGit, localGit, clonedRemoteGit);
        } catch (GitAPIException | IOException tempExc) {
            remoteGit.close();
            if (localGit != null) {
                localGit.close();
            }
            throw tempExc;
        }
    }

    /**
     * Creates a test file in local repository.
     *
     * @param repositorySet
     *            the repository where file should be created
     * @throws IOException
     *             in case of an I/O error
     */
    public void createTestfile(RepositorySet repositorySet) throws IOException {
        createTestfile(repositorySet, TESTFILE_NAME);
    }

    /**
     * Creates a test file in local repository.
     *
     * @param repositorySet
     *            the repository where file should be created
     * @param filename
     *            the name of the file to be created
     * @throws IOException
     *             in case of an I/O error
     */
    public void createTestfile(RepositorySet repositorySet, String filename) throws IOException {
        createTestfile(repositorySet.getWorkingDirectory(), filename);
    }

    private void createTestfile(File dir, String filename) throws IOException {
        File testFile = new File(dir, filename);
        FileUtils.write(testFile, TESTFILE_CONTENT, "UTF-8");
    }

    /**
     * Modifies the test file in local repository.
     *
     * @param repositorySet
     *            the repository where file is located
     * @throws IOException
     *             in case of an I/O error
     */
    public void modifyTestfile(RepositorySet repositorySet) throws IOException {
        modifyTestfile(repositorySet, TESTFILE_NAME);
    }

    /**
     * Modifies the test file in local repository.
     *
     * @param repositorySet
     *            the repository where file is located
     * @param filename
     *            the name of the file to be modified
     * @throws IOException
     *             in case of an I/O error
     */
    public void modifyTestfile(RepositorySet repositorySet, String filename) throws IOException {
        File testFile = new File(repositorySet.getWorkingDirectory(), filename);
        FileUtils.write(testFile, TESTFILE_CONTENT_MODIFIED, "UTF-8");
    }

    /**
     * Creates a test file in local repository and adds it to the repository
     * index.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createAndAddToIndexTestfile(RepositorySet repositorySet) throws GitAPIException, IOException {
        createAndAddToIndexTestfile(repositorySet, TESTFILE_NAME);
    }

    /**
     * Creates a test file in local repository and adds it to the repository
     * index.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @param filename
     *            the name of the file to be created
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createAndAddToIndexTestfile(RepositorySet repositorySet, String filename)
            throws GitAPIException, IOException {
        createTestfile(repositorySet, filename);
        repositorySet.getLocalRepoGit().add().addFilepattern(".").call();
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
        createAndCommitTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    /**
     * Creates a test file in local repository and commits it.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @param filename
     *            the name of the file to be created and commited
     * @param commitMessage
     *            the message to be used for commit
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createAndCommitTestfile(RepositorySet repositorySet, String filename, String commitMessage)
            throws GitAPIException, IOException {
        createAndAddToIndexTestfile(repositorySet, filename);
        repositorySet.getLocalRepoGit().commit().setMessage(commitMessage).call();
    }

    /**
     * Creates a test file in remote repository.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void remoteCreateTestfile(RepositorySet repositorySet) throws GitAPIException, IOException {
        remoteCreateTestfile(repositorySet, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    /**
     * Creates a test file in remote repository.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @param filename
     *            the name of the file to be created and commited
     * @param commitMessage
     *            the message to be used for commit
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void remoteCreateTestfile(RepositorySet repositorySet, String filename, String commitMessage)
            throws GitAPIException, IOException {
        createTestfile(repositorySet.getClonedRemoteWorkingDirectory(), filename);
        repositorySet.getClonedRemoteRepoGit().add().addFilepattern(".").call();
        repositorySet.getClonedRemoteRepoGit().commit().setMessage(commitMessage).call();
        repositorySet.getClonedRemoteRepoGit().push().call();
    }

    public void remoteCreateTestfileInBranch(RepositorySet repositorySet, String branch)
            throws GitAPIException, IOException {
        remoteCreateTestfileInBranch(repositorySet, branch, TESTFILE_NAME, COMMIT_MESSAGE_FOR_TESTFILE);
    }

    public void remoteCreateTestfileInBranch(RepositorySet repositorySet, String branch, String filename,
            String commitMessage) throws GitAPIException, IOException {
        repositorySet.getClonedRemoteRepoGit().pull().call();
        String currentBranch = currentBranch(repositorySet.getClonedRemoteRepoGit());
        repositorySet.getClonedRemoteRepoGit().checkout().setName(branch).setCreateBranch(true)
                .setUpstreamMode(SetupUpstreamMode.TRACK).setStartPoint("origin/" + branch).call();
        createTestfile(repositorySet.getClonedRemoteWorkingDirectory(), filename);
        repositorySet.getClonedRemoteRepoGit().add().addFilepattern(".").call();
        repositorySet.getClonedRemoteRepoGit().commit().setMessage(commitMessage).call();
        repositorySet.getClonedRemoteRepoGit().push().call();
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), currentBranch, false);

    }

    /**
     * Asserts that the content of the test file is not changed.
     *
     * @param repositorySet
     *            the repository where test file is located
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileContent(RepositorySet repositorySet) throws IOException {
        assertTestfileContent(repositorySet, TESTFILE_NAME);
    }

    /**
     * Asserts that the content of the test file is not changed.
     *
     * @param repositorySet
     *            the repository where test file is located
     * @param filename
     *            the name of the test file
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileContent(RepositorySet repositorySet, String filename) throws IOException {
        assertTestfileContent(repositorySet, filename, TESTFILE_CONTENT);
    }

    /**
     * Asserts that the content of the test file is modified.
     *
     * @param repositorySet
     *            the repository where test file is located
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileContentModified(RepositorySet repositorySet) throws IOException {
        assertTestfileContentModified(repositorySet, TESTFILE_NAME);
    }

    /**
     * Asserts that the content of the test file is modified.
     *
     * @param repositorySet
     *            the repository where test file is located
     * @param filename
     *            the name of the test file
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileContentModified(RepositorySet repositorySet, String filename) throws IOException {
        assertTestfileContent(repositorySet, filename, TESTFILE_CONTENT_MODIFIED);
    }

    private void assertTestfileContent(RepositorySet repositorySet, String filename, String expectedContent)
            throws IOException {
        File testFile = new File(repositorySet.getWorkingDirectory(), filename);
        String content = FileUtils.readFileToString(testFile, "UTF-8");
        assertEquals("testfile content is different", expectedContent, content);
    }

    /**
     * Asserts that the test file doesn't exist.
     *
     * @param repositorySet
     *            the repository where should be checked
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileMissing(RepositorySet repositorySet) throws IOException {
        assertTestfileMissing(repositorySet, TESTFILE_NAME);
    }

    /**
     * Asserts that the test file with passed name doesn't exist.
     *
     * @param repositorySet
     *            the repository where should be checked
     * @param filename
     *            the name of the test file
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTestfileMissing(RepositorySet repositorySet, String filename) throws IOException {
        File testFile = new File(repositorySet.getWorkingDirectory(), filename);
        assertFalse("testfile exists", testFile.exists());
    }

    /**
     * Returns the result of "git status" command.
     *
     * @param repositorySet
     *            the repository where git command should be executed
     * @return the status of the git repository
     * @throws GitAPIException
     *             if an error occurs on command execution
     */
    public Status status(RepositorySet repositorySet) throws GitAPIException {
        return repositorySet.getLocalRepoGit().status().call();
    }

    /**
     * Returns the name of current branch in local repository
     *
     * @param repositorySet
     *            the repository to be used
     * @return the name of current branch in local repository
     * @throws IOException
     *             in case of an I/O error
     */
    public String currentBranch(RepositorySet repositorySet) throws IOException {
        return currentBranch(repositorySet.getLocalRepoGit());
    }

    private String currentBranch(Git git) throws IOException {
        return git.getRepository().getBranch();
    }

    /**
     * Returns a list with names of branches in local repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @return list with names of branches in local repository
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public List<String> localBranches(RepositorySet repositorySet) throws GitAPIException {
        return branches(repositorySet.getLocalRepoGit());
    }

    /**
     * Returns a list with names of branches in remote repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @return list with names of branches in remote repository
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public List<String> remoteBranches(RepositorySet repositorySet) throws GitAPIException {
        return branches(repositorySet.getRemoteRepoGit());
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
     * Returns the name of the tag by the reference. E.g., for "refs/tags/1.0.0"
     * will be returned "1.0.0".
     *
     * @param aBranchRef
     *            the reference of a branch
     * @return the name of the referenced branch
     */
    public String tagNameByRef(Ref aTagRef) {
        return StringUtils.substringAfter(aTagRef.getName(), REFS_TAGS_PATH);
    }

    /**
     * Creates passed branch in local erpository without switching to it.
     *
     * @param repositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to be created
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createBranchWithoutSwitch(RepositorySet repositorySet, String aBranch)
            throws GitAPIException, IOException {
        String currentBranch = currentBranch(repositorySet);
        switchToBranch(repositorySet, aBranch, true);
        switchToBranch(repositorySet, currentBranch);
    }

    /**
     * Deletes the passed local branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to be deleted
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void deleteLocalBranch(RepositorySet repositorySet, String aBranch) throws GitAPIException {
        repositorySet.getLocalRepoGit().branchDelete().setBranchNames(aBranch).call();
    }

    /**
     * Creates passed branch in remote repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to be created
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createRemoteBranch(RepositorySet repositorySet, String aBranch) throws GitAPIException, IOException {
        String currentBranch = currentBranch(repositorySet.getClonedRemoteRepoGit());
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), aBranch, true);
        push(repositorySet.getClonedRemoteRepoGit());
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), currentBranch, false);

    }

    /**
     * Swiches to the passed branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to switch to
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void switchToBranch(RepositorySet repositorySet, String aBranch) throws GitAPIException {
        switchToBranch(repositorySet, aBranch, false);
    }

    /**
     * Swiches to the passed branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param aBranch
     *            the branch to switch to
     * @param createBranch
     *            <code>true</code> if branch should be created before switch
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void switchToBranch(RepositorySet repositorySet, String aBranch, boolean createBranch)
            throws GitAPIException {
        switchToBranch(repositorySet.getLocalRepoGit(), aBranch, createBranch);
    }

    private void switchToBranch(Git git, String aBranch, boolean createBranch) throws GitAPIException {
        git.checkout().setCreateBranch(createBranch).setName(aBranch).call();
    }

    /**
     * Pushes the local repository to remote.
     *
     * @param repositorySet
     *            the repository to be used
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void push(RepositorySet repositorySet) throws GitAPIException {
        push(repositorySet.getLocalRepoGit());
    }

    private void push(Git git) throws GitAPIException {
        git.push().call();
    }

    /**
     * Fetches the remote repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void fetch(RepositorySet repositorySet) throws GitAPIException {
        repositorySet.getLocalRepoGit().fetch().call();
    }

    /**
     * Merges passed branch to current branch and commits the merge.
     *
     * @param repositorySet
     *            the repository to be used
     * @param fromBranch
     *            the local branch to merge from
     * @param mergeMessage
     *            the merge message
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void mergeAndCommit(RepositorySet repositorySet, String fromBranch, String mergeMessage)
            throws GitAPIException, IOException {
        repositorySet.getLocalRepoGit().merge()
                .include(repositorySet.getLocalRepoGit().getRepository().resolve(REFS_HEADS_PATH + fromBranch))
                .setCommit(true).setMessage(mergeMessage).call();
    }

    /**
     * Asserts that working directory of the local repository is clean
     *
     * @param repositorySet
     *            the repository to be used
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertClean(RepositorySet repositorySet) throws GitAPIException {
        assertTrue("working directory is not clean", status(repositorySet).isClean());
    }

    /**
     * Asserts that passes config entry exists in local repository and has
     * passed expected value.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configKey
     *            the key of the config entry
     * @param expectedConfigValue
     *            the expected value of the config entry
     */
    public void assertConfigValue(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName, String expectedConfigValue) {
        StoredConfig config = repositorySet.getLocalRepoGit().getRepository().getConfig();
        String value = config.getString(configSection, configSubsection, configName);
        assertNotNull("git config [section='" + configSection + "', subsection='" + configSubsection + "', name='"
                + configName + "'] not found", value);
        assertEquals("git config value is wrong", expectedConfigValue, value);
    }

    /**
     * Assert that the current branch is equal to the passed one.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranch
     *            expected current branch
     * @throws IOException
     *             if an error occurs on git command execution
     */
    public void assertCurrentBranch(RepositorySet repositorySet, String expectedBranch) throws IOException {
        assertEquals("current branch is wrong", expectedBranch, currentBranch(repositorySet));
    }

    /**
     * Asserts that local repository consists of passed expected tags.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedTags
     *            the expected tags
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertLocalTags(RepositorySet repositorySet, String... expectedTags) throws GitAPIException {
        List<String> tags = tags(repositorySet.getLocalRepoGit());
        this.assertArrayEquals("Tags in local repository are different from expected", expectedTags,
                tags.toArray(new String[tags.size()]));
    }

    /**
     * Asserts that remote repository consists of passed expected tags.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedTags
     *            the expected tags
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertRemoteTags(RepositorySet repositorySet, String... expectedTags) throws GitAPIException {
        List<String> tags = tags(repositorySet.getRemoteRepoGit());
        this.assertArrayEquals("Tags in remote repository are different from expected", expectedTags,
                tags.toArray(new String[tags.size()]));
    }

    public List<String> tags(Git git) throws GitAPIException {
        List<String> tags = new ArrayList<String>();
        List<Ref> tagRefs = git.tagList().call();
        for (Ref tagRef : tagRefs) {
            tags.add(tagNameByRef(tagRef));
        }
        return tags;
    }

    /**
     * Asserts that local repository consists of passed expected branches.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertLocalBranches(RepositorySet repositorySet, String... expectedBranches) throws GitAPIException {
        List<String> branches = localBranches(repositorySet);
        assertBranches(expectedBranches, branches, "local");
    }

    /**
     * Asserts that remote repository consists of passed expected branches.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertRemoteBranches(RepositorySet repositorySet, String... expectedBranches) throws GitAPIException {
        List<String> branches = remoteBranches(repositorySet);
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
    public void assertCommitsInLocalBranch(RepositorySet repositorySet, String branch, String... expectedCommitMessages)
            throws GitAPIException, IOException {
        List<String> commitMessages = commitMessagesInBranch(repositorySet.getLocalRepoGit(), branch);
        assertCommitMessages(expectedCommitMessages, commitMessages, branch, "local");
    }

    /**
     * Asserts that the log of passed branch in local repository consists of
     * passed expected commit messages. The order of commits is ignored.
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
    public void assertCommitsInLocalBranchIgnoringOrder(RepositorySet repositorySet, String branch,
            String... expectedCommitMessages) throws GitAPIException, IOException {
        List<String> commitMessages = commitMessagesInBranch(repositorySet.getLocalRepoGit(), branch);
        if (expectedCommitMessages.length != commitMessages.size()
                || !commitMessages.containsAll(Arrays.asList(expectedCommitMessages))) {
            fail("Commit messages in branch '" + branch
                    + "' of 'local' repository are different from expected. Expected: "
                    + Arrays.toString(expectedCommitMessages) + ", actual: "
                    + Arrays.toString(commitMessages.toArray(new String[commitMessages.size()])));
        }
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

    private void assertArrayEquals(String message, String[] expected, String[] actual) {
        assertEquals(message, Arrays.toString(expected), Arrays.toString(actual));
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

    /**
     * Asserts that passed local and remote branches are different (reference
     * different commits).
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
    public void assertLocalAndRemoteBranchesAreDifferent(RepositorySet repositorySet, String localBranch,
            String remoteBranch) throws IOException {
        ObjectId localBranchObjectId = objectIdOfBranch(repositorySet.getLocalRepoGit(), localBranch);
        ObjectId remoteBranchObjectId = objectIdOfBranch(repositorySet.getRemoteRepoGit(), remoteBranch);
        assertNotEquals("remote branch reference same commit as local branch", localBranchObjectId.getName(),
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

    /**
     * Returns the last local commit in passed branch or <code>null</code> if no
     * commits found in branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the local branch to be checked
     * @return the last local commit in passed branch or <code>null</code> if no
     *         commits found in branch
     * @throws IOException
     *             in case of an I/O error
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public RevCommit lastLocalCommitInBranch(RepositorySet repositorySet, String branch)
            throws IOException, GitAPIException {
        ObjectId branchObjectId = objectIdOfBranch(repositorySet.getLocalRepoGit(), branch);
        Iterator<RevCommit> commitsIterator = repositorySet.getLocalRepoGit().log().add(branchObjectId).setMaxCount(1)
                .call().iterator();
        if (commitsIterator.hasNext()) {
            return commitsIterator.next();
        } else {
            return null;
        }
    }

    /**
     * Tries to read passed properties file from the passed branch in local
     * repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the local branch to be checked
     * @param filepath
     *            the relative path to the properties file to be read
     * @return the properties from the properties file
     * @throws IOException
     *             in case of an I/O error
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public Properties readPropertiesFileInLocalBranch(RepositorySet repositorySet, String branch, String filepath)
            throws IOException, GitAPIException {
        try (ObjectStream stream = readFileInLocalBranch(repositorySet, branch, filepath)) {
            if (stream != null) {
                Properties props = new Properties();
                props.load(stream);
                return props;
            }
            throw new IllegalStateException("File '" + filepath + "' couldn't be found");
        }
    }

    /**
     * Returns the stream of the passed file from the passed branch in local
     * repository or <code>null</code> if file can't be found.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the local branch to be checked
     * @param filepath
     *            the relative path to the file to be read
     * @return the stream of the file or <code>null</code> if file can't be
     *         found
     * @throws IOException
     *             in case of an I/O error
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public ObjectStream readFileInLocalBranch(RepositorySet repositorySet, String branch, String filepath)
            throws IOException, GitAPIException {
        RevCommit lastCommit = lastLocalCommitInBranch(repositorySet, branch);
        if (lastCommit != null) {
            RevTree tree = lastCommit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(repositorySet.getLocalRepoGit().getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filepath));
                if (treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repositorySet.getLocalRepoGit().getRepository().open(objectId);
                    return loader.openStream();
                }
            }
        }
        return null;
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
