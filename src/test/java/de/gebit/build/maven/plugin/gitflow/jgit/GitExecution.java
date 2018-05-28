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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.google.common.base.Objects;

import de.gebit.build.maven.plugin.gitflow.TestProjects;
import de.gebit.build.maven.plugin.gitflow.TestProjects.TestProjectData;
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

    private static final String GIT_BASEDIR_OFFLINE_SUFFIX = "offline-dummy.git";

    private static final String GIT_BASEDIR_LOCAL_SUFFIX = "working";

    private static final String GIT_BASEDIR_REMOTE_CLONE_SUFFIX = "cloned-origin";

    private static final String REFS_HEADS_PATH = "refs/heads/";

    private static final String REFS_TAGS_PATH = "refs/tags/";

    private String gitBaseDir;

    private String gitRepoBaseDir;

    /**
     * Creates an instance of git execution.
     *
     * @param aGitBasedir
     *            the base directory where git repository set will be created
     * @param aGitRepoBasedir
     *            the base directory where existing git repositories are located
     */
    public GitExecution(String aGitBasedir, String aGitRepoBasedir) {
        this.gitBaseDir = aGitBasedir;
        this.gitRepoBaseDir = aGitRepoBasedir;
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
     * Use an existing set of git repositories by copying files from the passed
     * sourceRepoDir to the repo base directory.<br>
     * Initial branch checked out in working directory is 'master'.
     *
     * @param project
     *            the project that will be used
     * @return a repository set that contains references to the remote and local
     *         repositories
     */
    public RepositorySet useGitRepositorySet(TestProjectData project) throws Exception {
        return useGitRepositorySet(project, "master");
    }

    /**
     * Use an existing set of git repositories by copying files from the passed
     * sourceRepoDir to the repo base directory.
     *
     * @param project
     *            the project that will be used
     * @param initialBranch
     *            the initial branch checked out in working directory
     * @return a repository set that contains references to the remote and local
     *         repositories
     */
    public RepositorySet useGitRepositorySet(TestProjectData project, String initialBranch) throws Exception {
        String projectName = project.artifactId;
        File repoBasedir = new File(gitBaseDir, projectName);
        File gitRepoDir = new File(gitRepoBaseDir);
        TestProjects.prepareRepositoryIfNotExisting(gitRepoDir);
//        File sourceRepoFile = new File(gitRepoDir, projectName + ".zip");
//        new ZipUtils().unzip(sourceRepoFile, new File(gitBaseDir));
        FileUtils.copyDirectory(new File(gitRepoDir, projectName), repoBasedir);
        Git remoteGit = Git.open(new File(repoBasedir, GIT_BASEDIR_REMOTE_SUFFIX));
        Git localGit = Git.open(new File(repoBasedir, GIT_BASEDIR_LOCAL_SUFFIX));
        setGitDummyEditorToConfig(localGit);
        RemoteSetUrlCommand remoteSetUrlCommand = localGit.remoteSetUrl();
        remoteSetUrlCommand.setName("origin");
        remoteSetUrlCommand.setUri(new URIish(remoteGit.getRepository().getDirectory().getAbsolutePath()));
        remoteSetUrlCommand.call();
        localGit.checkout().setName(initialBranch).call();
        Git clonedRemoteGit = Git.open(new File(repoBasedir, GIT_BASEDIR_REMOTE_CLONE_SUFFIX));
        remoteSetUrlCommand = clonedRemoteGit.remoteSetUrl();
        remoteSetUrlCommand.setName("origin");
        remoteSetUrlCommand.setUri(new URIish(remoteGit.getRepository().getDirectory().getAbsolutePath()));
        remoteSetUrlCommand.call();
        clonedRemoteGit.checkout().setName("master").call();
        return new RepositorySet(remoteGit, localGit, clonedRemoteGit);
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
        ObjectId branchObjectId = repositorySet.getClonedRemoteRepoGit().getRepository()
                .resolve(REFS_HEADS_PATH + branch);
        if (branchObjectId == null) {
            repositorySet.getClonedRemoteRepoGit().checkout().setName(branch).setCreateBranch(true)
                    .setUpstreamMode(SetupUpstreamMode.TRACK).setStartPoint("origin/" + branch).call();
        } else {
            if (!Objects.equal(currentBranch, branch)) {
                repositorySet.getClonedRemoteRepoGit().checkout().setName(branch).call();
            }
            repositorySet.getClonedRemoteRepoGit().reset().setMode(ResetType.HARD).setRef("origin/" + branch).call();
        }
        createTestfile(repositorySet.getClonedRemoteWorkingDirectory(), filename);
        repositorySet.getClonedRemoteRepoGit().add().addFilepattern(".").call();
        repositorySet.getClonedRemoteRepoGit().commit().setMessage(commitMessage).call();
        repositorySet.getClonedRemoteRepoGit().push().call();
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), currentBranch, false);

    }

    /**
     * @param aRepositorySet
     */
    public void commitAll(RepositorySet repositorySet, String commitMessage) throws GitAPIException {
        repositorySet.getLocalRepoGit().add().addFilepattern(".").call();
        repositorySet.getLocalRepoGit().commit().setMessage(commitMessage).call();
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
        assertFalse("testfile '" + filename + "' exists", testFile.exists());
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
     * Returns ID of the current commit.
     *
     * @param repositorySet
     *            the repository to be used
     * @return the ID of the current commit
     * @throws IOException
     *             in case of an I/O error
     */
    public String currentCommit(RepositorySet repositorySet) throws IOException {
        return commitId(repositorySet, "HEAD");
    }

    public String commitId(RepositorySet repositorySet, String ref) throws IOException {
        return repositorySet.getLocalRepoGit().getRepository().resolve(ref).getName();
    }

    /**
     * Returns ID of the current commit on passed local branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the name of the local branch
     * @return the ID of the current commit on remote branch
     */
    public String localBranchCurrentCommit(RepositorySet repositorySet, String branch) throws IOException {
        return branchCurrentCommit(repositorySet.getLocalRepoGit(), branch);
    }

    /**
     * Returns ID of the current commit on passed remote branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the name of the remote branch
     * @return the ID of the current commit on remote branch
     */
    public String remoteBranchCurrentCommit(RepositorySet repositorySet, String branch) throws IOException {
        return branchCurrentCommit(repositorySet.getRemoteRepoGit(), branch);
    }

    private String branchCurrentCommit(Git git, String branch) throws IOException {
        return git.getRepository().resolve(REFS_HEADS_PATH + branch).getName();
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
    @Deprecated
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
    @Deprecated
    public List<String> remoteBranches(RepositorySet repositorySet) throws GitAPIException {
        return branches(repositorySet.getRemoteRepoGit());
    }

    @Deprecated
    public List<String> branches(Git git) throws GitAPIException {
        return branches(git, null);
    }

    public List<String> branches(Git git, List<String> includes) throws GitAPIException {
        List<String> branches = new ArrayList<String>();
        List<Ref> branchRefs = git.branchList().call();
        for (Ref branchRef : branchRefs) {
            if (branchRef.getName().startsWith(REFS_HEADS_PATH)) {
                String branch = branchNameByRef(branchRef);
                if (includes == null || includes.contains(branch)) {
                    branches.add(branch);
                }
            }
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
     * Creates passed branch in local rpository and switch to it.
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
    public void createBranch(RepositorySet repositorySet, String aBranch) throws GitAPIException, IOException {
        switchToBranch(repositorySet.getLocalRepoGit(), aBranch, true);
    }

    /**
     * Creates passed branch in local rpository without switching to it.
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
        createBranch(repositorySet, aBranch);
        switchToBranch(repositorySet, currentBranch);
    }

    /**
     * Deletes the passed local branch and its remote tracking branch
     * (refs/remotes/origin/branch).
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the branch to be deleted
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void deleteLocalAndRemoteTrackingBranches(RepositorySet repositorySet, String branch)
            throws GitAPIException {
        repositorySet.getLocalRepoGit().branchDelete().setBranchNames(branch, "refs/remotes/origin/" + branch)
                .setForce(true).call();
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
        repositorySet.getLocalRepoGit().branchDelete().setBranchNames(aBranch).setForce(true).call();
    }

    /**
     * Deletes the passed remote branch.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the remote branch to be deleted
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void deleteRemoteBranch(RepositorySet repositorySet, String branch) throws GitAPIException {
        repositorySet.getLocalRepoGit().branchDelete().setBranchNames("refs/remotes/origin/" + branch).setForce(true)
                .call();
        repositorySet.getLocalRepoGit().push()
                .setRefSpecs(new RefSpec().setSource(null).setDestination("refs/heads/" + branch)).setRemote("origin")
                .call();
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
        pull(repositorySet.getClonedRemoteRepoGit());
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), aBranch, true);
        push(repositorySet.getClonedRemoteRepoGit());
        switchToBranch(repositorySet.getClonedRemoteRepoGit(), currentBranch, false);

    }

    /**
     * Creates passed branch as orphan branch in remote repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the branch to be created
     * @param initialCommitMessage
     *            the initial commit message
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createRemoteOrphanBranch(RepositorySet repositorySet, String branch, String initialCommitMessage)
            throws GitAPIException, IOException {
        createOrphanBranch(repositorySet.getClonedRemoteRepoGit(), branch, initialCommitMessage, true);
    }

    /**
     * Creates passed branch as orphan branch in local repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the branch to be created
     * @param initialCommitMessage
     *            the initial commit message
     * @throws GitAPIException
     *             if an error occurs on git command execution
     * @throws IOException
     *             in case of an I/O error
     */
    public void createOrphanBranch(RepositorySet repositorySet, String branch, String initialCommitMessage)
            throws GitAPIException, IOException {
        createOrphanBranch(repositorySet.getLocalRepoGit(), branch, initialCommitMessage, false);
    }

    private void createOrphanBranch(Git git, String branch, String initialCommitMessage, boolean push)
            throws GitAPIException, IOException {
        String currentBranch = currentBranch(git);
        pull(git);
        git.checkout().setName(branch).setOrphan(true).call();
        git.commit().setAllowEmpty(true).setMessage(initialCommitMessage).call();
        if (push) {
            push(git);
        }
        switchToBranch(git, currentBranch, false);
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
        switchToBranch(repositorySet.getLocalRepoGit(), aBranch, false);
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

    private void pull(Git git) throws GitAPIException {
        git.pull().call();
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

    public void mergeWithExpectedConflict(RepositorySet repositorySet, String fromBranch)
            throws GitAPIException, IOException {
        String sourceBranch = fromBranch.startsWith("refs/") ? fromBranch : REFS_HEADS_PATH + fromBranch;
        repositorySet.getLocalRepoGit().merge()
                .include(repositorySet.getLocalRepoGit().getRepository().resolve(sourceBranch)).setCommit(true).call();
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
     * Assert that passed config entry exists in local repository and has passed
     * expected value.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     * @param expectedConfigValue
     *            the expected value of the config entry
     */
    public void assertConfigValue(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName, String expectedConfigValue) {
        String value = getConfigValue(repositorySet, configSection, configSubsection, configName);
        assertNotNull("git config [section='" + configSection + "', subsection='" + configSubsection + "', name='"
                + configName + "'] not found", value);
        assertEquals("git config value is wrong", expectedConfigValue, value);
    }

    /**
     * Assert that passed config entry for passed branch exists in local
     * repository and has passed expected value.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branchName
     *            the name of the branch
     * @param configName
     *            the name part of the config entry key
     * @param expectedConfigValue
     *            the expected value of the config entry
     */
    public void assertBranchLocalConfigValue(RepositorySet repositorySet, String branchName, String configName,
            String expectedConfigValue) {
        assertConfigValue(repositorySet, "branch", branchName, configName, expectedConfigValue);
    }

    /**
     * Assert that passed config entry doesn't exist in local repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     */
    public void assertConfigValueMissing(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName) {
        String value = getConfigValue(repositorySet, configSection, configSubsection, configName);
        assertNull("git config [section='" + configSection + "', subsection='" + configSubsection + "', name='"
                + configName + "'] found but not expected", value);
    }

    /**
     * Assert that passed config entry for passed branch doesn't exist in local
     * repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branchName
     *            the name of the branch
     * @param configName
     *            the name part of the config entry key
     */
    public void assertBranchLocalConfigValueMissing(RepositorySet repositorySet, String branchName, String configName) {
        assertConfigValueMissing(repositorySet, "branch", branchName, configName);
    }

    /**
     * Assert that passed config entry exists in local repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     */
    public void assertConfigValueExists(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName) {
        String value = getConfigValue(repositorySet, configSection, configSubsection, configName);
        assertNotNull("git config [section='" + configSection + "', subsection='" + configSubsection + "', name='"
                + configName + "'] found but not expected", value);
    }

    /**
     * Assert that passed config entry for passed branch exists in local
     * repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branchName
     *            the name of the branch
     * @param configName
     *            the name part of the config entry key
     */
    public void assertBranchLocalConfigValueExists(RepositorySet repositorySet, String branchName, String configName) {
        assertConfigValueExists(repositorySet, "branch", branchName, configName);
    }

    /**
     * Return value of passed config entry or <code>null</code> if config entry
     * doesn't exist.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     * @return the value of the config entry or <code>null</code> if config
     *         entry doesn't exist
     */
    public String getConfigValue(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName) {
        StoredConfig config = repositorySet.getLocalRepoGit().getRepository().getConfig();
        return config.getString(configSection, configSubsection, configName);
    }

    /**
     * Set value for passed config entry.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     * @param value
     *            the value to be set
     * @throws IOException
     *             in case of an I/O error
     */
    public void setConfigValue(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName, String value) throws IOException {
        StoredConfig config = repositorySet.getLocalRepoGit().getRepository().getConfig();
        config.setString(configSection, configSubsection, configName, value);
        config.save();
    }

    public void removeBranchCentralConfigValue(RepositorySet repositorySet, String configBranch, String branch,
            String configName) throws IOException, GitAPIException {
        Git git = repositorySet.getClonedRemoteRepoGit();
        String oldBranch = currentBranch(git);
        git.fetch().call();
        git.checkout().setName(configBranch).setStartPoint("origin/" + configBranch).setCreateBranch(true).call();
        Properties properties = new Properties();
        File branchPropertyFile = new File(repositorySet.getClonedRemoteWorkingDirectory(), branch);
        try (FileInputStream fis = new FileInputStream(branchPropertyFile)) {
            properties.load(new FileInputStream(branchPropertyFile));
        }
        properties.remove(configName);
        try (FileOutputStream fos = new FileOutputStream(branchPropertyFile)) {
            properties.store(new FileOutputStream(branchPropertyFile), null);
        }
        git.add().addFilepattern(".").call();
        git.commit().setMessage("remove property").call();
        git.push().call();
        git.checkout().setName(oldBranch).call();
    }

    public void setBranchCentralConfigValue(RepositorySet repositorySet, String configBranch, String branch,
            String configName, String value) throws IOException, GitAPIException {
        Git git = repositorySet.getClonedRemoteRepoGit();
        String oldBranch = currentBranch(git);
        git.fetch().call();
        git.checkout().setName(configBranch).setStartPoint("origin/" + configBranch).setCreateBranch(true).call();
        Properties properties = new Properties();
        File branchPropertyFile = new File(repositorySet.getClonedRemoteWorkingDirectory(), branch);
        try (FileInputStream fis = new FileInputStream(branchPropertyFile)) {
            properties.load(new FileInputStream(branchPropertyFile));
        }
        properties.setProperty(configName, value);
        try (FileOutputStream fos = new FileOutputStream(branchPropertyFile)) {
            properties.store(new FileOutputStream(branchPropertyFile), null);
        }
        git.add().addFilepattern(".").call();
        git.commit().setMessage("remove property").call();
        git.push().call();
        git.checkout().setName(oldBranch).call();
    }

    /**
     * Remove config entry.
     *
     * @param repositorySet
     *            the repository to be used
     * @param configSection
     *            the section part of the config entry key
     * @param configSubsection
     *            the sub-section part of the config entry key
     * @param configName
     *            the name part of the config entry key
     * @throws IOException
     *             in case of an I/O error
     */
    public void removeConfigValue(RepositorySet repositorySet, String configSection, String configSubsection,
            String configName) throws IOException {
        StoredConfig config = repositorySet.getLocalRepoGit().getRepository().getConfig();
        config.unset(configSection, configSubsection, configName);
        config.save();
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
        assertEqualsElements("Tags in local repository are different from expected", expectedTags, tags);
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
        assertEqualsElements("Tags in remote repository are different from expected", expectedTags, tags);
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
     * Create tags in local repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param tags
     *            the tags to be created
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void createTags(RepositorySet repositorySet, String... tags) throws GitAPIException {
        createTags(repositorySet, false, tags);
    }

    /**
     * Create tags in local repository and optionally push it to the remote
     * repository.
     *
     * @param repositorySet
     *            the repository to be used
     * @param push
     *            <code>true</code> if tags shhould be pushed to remote
     *            repository
     * @param tags
     *            the tags to be created
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void createTags(RepositorySet repositorySet, boolean push, String... tags) throws GitAPIException {
        for (String tag : tags) {
            repositorySet.getLocalRepoGit().tag().setName(tag).call();
            if (push) {
                repositorySet.getLocalRepoGit().push().add(tag).call();
            }
        }
    }

    /**
     * Delete local tag.
     *
     * @param repositorySet
     *            the repository to be used
     * @param tag
     *            the tag to be deleted
     */
    public void deleteTag(RepositorySet repositorySet, String tag) throws GitAPIException {
        repositorySet.getLocalRepoGit().tagDelete().setTags(tag).call();
    }

    /**
     * Assert that passed expected branches exist locally.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertExistingLocalBranches(RepositorySet repositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = branches(repositorySet.getLocalRepoGit(), Arrays.asList(expectedBranches));
        assertExistingBranches(expectedBranches, branches, "local");
    }

    /**
     * Assert that passed expected branches exist remotely.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertExistingRemoteBranches(RepositorySet repositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = branches(repositorySet.getRemoteRepoGit(), Arrays.asList(expectedBranches));
        assertExistingBranches(expectedBranches, branches, "remote");
    }

    private void assertExistingBranches(String[] expectedBranches, List<String> branches, String repoName) {
        List<String> expectedBranchesList = Arrays.asList(expectedBranches);
        List<String> expected = new LinkedList<>(expectedBranchesList);
        List<String> actual = new LinkedList<>(branches);
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals("Not all expected branches exist in " + repoName + " repository.",
                Arrays.toString(expected.toArray(new String[expected.size()])),
                Arrays.toString(actual.toArray(new String[actual.size()])));
    }

    /**
     * Assert that passed expected branches doesn't exist locally.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertMissingLocalBranches(RepositorySet repositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = branches(repositorySet.getLocalRepoGit(), Arrays.asList(expectedBranches));
        assertMissingBranches(expectedBranches, branches, "local");
    }

    /**
     * Assert that passed expected branches doesn't exist remotely.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedBranches
     *            the expected branches
     * @throws GitAPIException
     *             if an error occurs on git command execution
     */
    public void assertMissingRemoteBranches(RepositorySet repositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = branches(repositorySet.getRemoteRepoGit(), Arrays.asList(expectedBranches));
        assertMissingBranches(expectedBranches, branches, "remote");
    }

    private void assertMissingBranches(String[] expectedBranches, List<String> branches, String repoName) {
        if (branches.size() > 0) {
            List<String> expectedBranchesList = Arrays.asList(expectedBranches);
            List<String> expected = new LinkedList<>(expectedBranchesList);
            List<String> actual = new LinkedList<>(expectedBranchesList);
            actual.removeAll(branches);
            Collections.sort(expected);
            Collections.sort(actual);
            assertEquals("Not all expected branches are missing in " + repoName + " repository.",
                    Arrays.toString(expected.toArray(new String[expected.size()])),
                    Arrays.toString(actual.toArray(new String[actual.size()])));
        }
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
            commitMessages.add(commit.getShortMessage().trim());
        }
        return commitMessages;
    }

    private void assertCommitMessages(String[] expectedCommitMessages, List<String> commitMessages, String branch,
            String repoName) {
        List<String> expectedCommitMessagesList = Arrays.asList(expectedCommitMessages);
        List<String> expected = new LinkedList<>(expectedCommitMessagesList);
        List<String> actual = new LinkedList<>(commitMessages);
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(
                "Commit messages in branch '" + branch + "' of " + repoName
                        + " repository are different from expected.",
                Arrays.toString(expected.toArray(new String[expected.size()])),
                Arrays.toString(actual.toArray(new String[actual.size()])));
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
     * @return the properties from the properties file or <code>null</code> if
     *         property file doesn't exist
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
            return null;
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
        assertEqualsElements("Commit messages in git editor for interactive rebase are different from expected",
                expectedCommitMessages, commitMessages);
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
        return GitRebaseTodo.load(new File(gitBaseDir, GitDummyEditor.RELATIVE_PATH_TO_GIT_REBASE_TODO));
    }

    public void defineRebaseTodoCommands(String... commands) throws IOException {
        if (commands != null && commands.length > 0) {
            File gitRebaseTodoCommandsFile = new File(gitBaseDir,
                    GitDummyEditor.REBASE_TODO_COMMANDS_FILE_RELATIVE_PATH);
            File parentDir = gitRebaseTodoCommandsFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter fileWriter = new FileWriter(gitRebaseTodoCommandsFile, false)) {
                for (String command : commands) {
                    fileWriter.write(command + "\n");
                }
            }
        }
    }

    private void setGitEditorToConfig(Git git, String runEditorCommand) throws IOException {
        StoredConfig tempConfig = git.getRepository().getConfig();
        tempConfig.setString("core", null, "editor", runEditorCommand);
        tempConfig.save();
    }

    public void prepareErrorWhileUsingGitEditor(RepositorySet repositorySet) throws IOException {
        setGitEditorToConfig(repositorySet.getLocalRepoGit(), "notExistingGitEditor");
    }

    private void setGitDummyEditorToConfig(Git git) throws IOException {
        setGitEditorToConfig(git, getGitDummyEditorCMD());
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

    public void setOffline(RepositorySet repositorySet) throws GitAPIException, URISyntaxException {
        RemoteSetUrlCommand remoteSetUrlCommand = repositorySet.getLocalRepoGit().remoteSetUrl();
        remoteSetUrlCommand.setName("origin");
        remoteSetUrlCommand.setUri(
                new URIish(new File(repositorySet.getWorkingDirectory().getParentFile(), GIT_BASEDIR_OFFLINE_SUFFIX)
                        .getAbsolutePath()));
        remoteSetUrlCommand.call();
    }

    public void setOnline(RepositorySet repositorySet) throws GitAPIException, URISyntaxException {
        RemoteSetUrlCommand remoteSetUrlCommand = repositorySet.getLocalRepoGit().remoteSetUrl();
        remoteSetUrlCommand.setName("origin");
        remoteSetUrlCommand.setUri(
                new URIish(new File(repositorySet.getWorkingDirectory().getParentFile(), GIT_BASEDIR_REMOTE_SUFFIX)
                        .getAbsolutePath()));
        remoteSetUrlCommand.call();
    }

    public void useClonedRemoteRepository(RepositorySet repositorySet) {
        repositorySet.setUseClonedRemoteRepositoryAsLocal(true);
    }

    public void useLocalRepository(RepositorySet repositorySet) {
        repositorySet.setUseClonedRemoteRepositoryAsLocal(false);
    }

    public void assertNoRebaseInProcess(RepositorySet repositorySet) throws IOException {
        File headNameFile = FileUtils.getFile(repositorySet.getWorkingDirectory(), ".git/rebase-apply/head-name");
        assertFalse("rebase in process found", headNameFile.exists());
        headNameFile = FileUtils.getFile(repositorySet.getWorkingDirectory(), ".git/rebase-merge/head-name");
        assertFalse("rebase in process found", headNameFile.exists());
    }

    public void assertRebaseBranchInProcess(RepositorySet repositorySet, String branch,
            String... expectedConflictingFiles) throws GitAPIException, IOException {
        assertRebaseBranchInProcessOntoBranch(repositorySet, branch, null, expectedConflictingFiles);
    }

    public void assertRebaseBranchInProcessOntoBranch(RepositorySet repositorySet, String branch, String ontoBranch,
            String... expectedConflictingFiles) throws GitAPIException, IOException {
        boolean rebaseApply = true;
        File headNameFile = FileUtils.getFile(repositorySet.getWorkingDirectory(), ".git/rebase-apply/head-name");
        if (!headNameFile.exists()) {
            headNameFile = FileUtils.getFile(repositorySet.getWorkingDirectory(), ".git/rebase-merge/head-name");
            assertTrue("no rebase in process found", headNameFile.exists());
            rebaseApply = false;
        }
        String branchRef = StringUtils.trim(FileUtils.readFileToString(headNameFile, "UTF-8"));
        String rebaseBranch = StringUtils.substringAfter(branchRef, REFS_HEADS_PATH);
        assertEquals("reabes in process for wrong branch", branch, rebaseBranch);
        if (ontoBranch != null) {
            String branchCommitId = repositorySet.getLocalRepoGit().getRepository().resolve(ontoBranch).getName();
            File ontoFile = FileUtils.getFile(repositorySet.getWorkingDirectory(),
                    ".git/rebase-" + (rebaseApply ? "apply" : "merge") + "/onto");
            if (ontoFile.exists()) {
                String ontoRef = StringUtils.trim(FileUtils.readFileToString(ontoFile, "UTF-8"));
                assertEquals("rebase in process is not onto expected branch [" + ontoBranch + "]", branchCommitId,
                        ontoRef);
            }
        }
        if (expectedConflictingFiles != null && expectedConflictingFiles.length > 0) {
            Set<String> conflictingFiles = status(repositorySet).getConflicting();
            assertEquals("number of conflicting files is wrong", expectedConflictingFiles.length,
                    conflictingFiles.size());
            for (String expectedConflictingFile : expectedConflictingFiles) {
                assertTrue("file '" + expectedConflictingFile + "' is not in conflict",
                        conflictingFiles.contains(expectedConflictingFile));
            }
        }
    }

    public void assertMergeInProcess(RepositorySet repositorySet, String... expectedConflictingFiles)
            throws GitAPIException, IOException {
        assertMergeInProcessFromBranch(repositorySet, null, expectedConflictingFiles);
    }

    public void assertMergeInProcessFromBranch(RepositorySet repositorySet, String fromBranch,
            String... expectedConflictingFiles) throws GitAPIException, IOException {
        List<ObjectId> mergeHeads = repositorySet.getLocalRepoGit().getRepository().readMergeHeads();
        assertNotNull("no merge in process found", mergeHeads);
        if (fromBranch != null) {
            String branchCommitId = repositorySet.getLocalRepoGit().getRepository().resolve(fromBranch).getName();
            assertEquals("merge in process is not from expected branch [" + fromBranch + "]", branchCommitId,
                    mergeHeads.get(0).getName());
        }
        if (expectedConflictingFiles != null && expectedConflictingFiles.length > 0) {
            Set<String> conflictingFiles = status(repositorySet).getConflicting();
            assertEquals("number of conflicting files is wrong", expectedConflictingFiles.length,
                    conflictingFiles.size());
            for (String expectedConflictingFile : expectedConflictingFiles) {
                assertTrue("file '" + expectedConflictingFile + "' is not in conflict",
                        conflictingFiles.contains(expectedConflictingFile));
            }
        }
    }

    public void assertMergeWithSquashInProcess(RepositorySet repositorySet, String... expectedConflictingFiles)
            throws GitAPIException, IOException {
        File headNameFile = FileUtils.getFile(repositorySet.getWorkingDirectory(), ".git/SQUASH_MSG");
        assertTrue("no merge in process found", headNameFile.exists());
        if (expectedConflictingFiles != null && expectedConflictingFiles.length > 0) {
            Set<String> conflictingFiles = status(repositorySet).getConflicting();
            assertEquals("number of conflicting files is wrong", expectedConflictingFiles.length,
                    conflictingFiles.size());
            for (String expectedConflictingFile : expectedConflictingFiles) {
                assertTrue("file '" + expectedConflictingFile + "' is not in conflict",
                        conflictingFiles.contains(expectedConflictingFile));
            }
        }
    }

    public void assertUntrackedFiles(RepositorySet repositorySet, String... expectedUntrackedFiles)
            throws GitAPIException {
        assertEqualsElements("Untracked files are different from expected.", expectedUntrackedFiles,
                status(repositorySet).getUntracked());
    }

    public void assertAddedFiles(RepositorySet repositorySet, String... expectedAddedFiles) throws GitAPIException {
        assertEqualsElements("Added files are different from expected.", expectedAddedFiles,
                status(repositorySet).getAdded());
    }

    public void assertModifiedFiles(RepositorySet repositorySet, String... expectedModifiedFiles)
            throws GitAPIException {
        assertEqualsElements("Modified files are different from expected.", expectedModifiedFiles,
                status(repositorySet).getModified());
    }

    private void assertEqualsElements(String message, String[] expectedElements, Collection<String> actualElements) {
        List<String> expected = new LinkedList<String>(Arrays.asList(expectedElements));
        List<String> actual = new LinkedList<String>(actualElements);
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(message, Arrays.toString(expected.toArray(new String[expected.size()])),
                Arrays.toString(actual.toArray(new String[actual.size()])));
    }

    public void createIntegeratedBranch(RepositorySet repositorySet, String integrationBranch) throws Exception {
        repositorySet.getLocalRepoGit().branchCreate().setName(integrationBranch).setStartPoint("HEAD").call();
        repositorySet.getLocalRepoGit().push().setRemote("origin").add(integrationBranch).call();
    }

    /**
     * Return the commit ID referenced by the passed tag.
     *
     * @param repositorySet
     *            the repository to be used
     * @param tagName
     *            the name of the tag
     * @return the commit ID referenced by the tag
     * @throws IOException
     *             in case of an I/O error
     */
    public String tagCommit(RepositorySet repositorySet, String tagName) throws IOException {
        try (RevWalk walk = new RevWalk(repositorySet.getLocalRepoGit().getRepository())) {
            Ref tagRef = repositorySet.getLocalRepoGit().getRepository().findRef(tagName);
            RevTag tag = (RevTag) walk.parseAny(tagRef.getObjectId());
            return tag.getObject().getName();
        }
    }

    /**
     * Assert that passed tag references passed commit ID.
     *
     * @param repositorySet
     *            the repository to be used
     * @param tagName
     *            the name of the tag
     * @param expectedCommitId
     *            the expected commit ID referenced by the tag
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertTagCommit(RepositorySet repositorySet, String tagName, String expectedCommitId)
            throws IOException {
        assertEquals("tag [" + tagName + "] references wrong commit", expectedCommitId,
                tagCommit(repositorySet, tagName));
    }

    /**
     * Assert that the current commit has the passed commit ID.
     *
     * @param repositorySet
     *            the repository to be used
     * @param expectedCommitId
     *            the expected commit ID
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertCurrentCommit(RepositorySet repositorySet, String expectedCommitId) throws IOException {
        assertEquals("current commit is wrong", expectedCommitId, currentCommit(repositorySet));
    }

    /**
     * Assert that the current commit of the passed local branch has the passed
     * commit ID.
     *
     * @param repositorySet
     *            the repository to be used
     * @param branch
     *            the name of the local branch
     * @param expectedCommitId
     *            the expected commit ID
     * @throws IOException
     *             in case of an I/O error
     */
    public void assertLocalBranchCurrentCommit(RepositorySet repositorySet, String branch, String expectedCommitId)
            throws IOException {
        assertEquals("local branch current commit is wrong", expectedCommitId,
                localBranchCurrentCommit(repositorySet, branch));
    }

    public void assertTrackingBranch(RepositorySet repositorySet, String expectedTrackingBranch, String branch) {
        String shortTrackingName = Repository.shortenRefName(expectedTrackingBranch);
        String fullTrackingName = Constants.R_REMOTES + shortTrackingName;
        assertEquals("tracking branch of the branch '" + branch + "' is different from expected", fullTrackingName,
                getUpstreamBranch(repositorySet, branch));
    }

    public String getUpstreamBranch(RepositorySet repositorySet, String branchName) {
        String shortBranchName = Repository.shortenRefName(branchName);
        BranchConfig branchConfig = new BranchConfig(repositorySet.getLocalRepoGit().getRepository().getConfig(),
                shortBranchName);
        return branchConfig.getTrackingBranch();
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
