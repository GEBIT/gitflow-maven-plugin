//
// RepositorySet.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.jgit;

import java.io.File;

import org.eclipse.jgit.api.Git;

/**
 * Repository set contains Git APIs for remote and local repositories.
 *
 * @author VMedvid
 */
public class RepositorySet implements AutoCloseable {

    private Git remoteRepoGit;

    private Git localRepoGit;

    /**
     * Initializes repository set with Git APIs for remote and local
     * repositories.
     *
     * @param aRemoteRepoGit
     *            the Git API for remote repository
     * @param aLocalRepoGit
     *            the Git API for local repository
     */
    public RepositorySet(Git aRemoteRepoGit, Git aLocalRepoGit) {
        remoteRepoGit = aRemoteRepoGit;
        localRepoGit = aLocalRepoGit;
    }

    @Override
    public void close() throws Exception {
        if (remoteRepoGit != null) {
            remoteRepoGit.getRepository().close();
            remoteRepoGit.close();
        }
        if (localRepoGit != null) {
            localRepoGit.getRepository().close();
            localRepoGit.close();
        }
    }

    /**
     * @return the Git API for remote repository.
     */
    public Git getRemoteRepoGit() {
        return remoteRepoGit;
    }

    /**
     * @return the Git API for local repository.
     */
    public Git getLocalRepoGit() {
        return localRepoGit;
    }

    /**
     * @return the working directory of local repository
     */
    public File getWorkingDirectory() {
        return localRepoGit.getRepository().getDirectory().getParentFile();
    }

}
