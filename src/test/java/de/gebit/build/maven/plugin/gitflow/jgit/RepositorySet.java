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
 * Repository set contains Git APIs for origin and working repositories.
 *
 * @author VMedvid
 */
public class RepositorySet implements AutoCloseable {

    private Git originRepoGit;

    private Git workingRepoGit;

    /**
     * Initializes repository set with Git APIs for origin and working
     * repositories.
     *
     * @param aOriginRepoGit
     *            the Git API for origin repository
     * @param aWorkingRepoGit
     *            the Git API for working repository
     */
    public RepositorySet(Git aOriginRepoGit, Git aWorkingRepoGit) {
        originRepoGit = aOriginRepoGit;
        workingRepoGit = aWorkingRepoGit;
    }

    @Override
    public void close() throws Exception {
        if (originRepoGit != null) {
            originRepoGit.getRepository().close();
            originRepoGit.close();
        }
        if (workingRepoGit != null) {
            workingRepoGit.getRepository().close();
            workingRepoGit.close();
        }
    }

    /**
     * @return the Git API for origin repository.
     */
    public Git getOriginRepoGit() {
        return originRepoGit;
    }

    /**
     * @return the Git API for working repository.
     */
    public Git getWorkingRepoGit() {
        return workingRepoGit;
    }

    /**
     * @return the basedir for working repository
     */
    public File getWorkingBasedir() {
        return getRepoBasedir(workingRepoGit);
    }

    private File getRepoBasedir(Git repoGit) {
        return repoGit.getRepository().getDirectory().getParentFile();
    }

}
