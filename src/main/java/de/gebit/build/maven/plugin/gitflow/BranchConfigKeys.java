//
// BranchConfigKeys.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Property keys used in central branch config.
 *
 * @author Volodymyr Medvid
 */
public interface BranchConfigKeys {

    /**
     * The version of central branch config used to recognize if an upgrade is
     * needed.
     */
    public static final String CENTRAL_BRANCH_CONFIG_VERSION = "1";

    /**
     * Branch config key for branch type.
     */
    public static final String BRANCH_TYPE = "branchType";

    /**
     * Branch config key for base branch.
     */
    public static final String BASE_BRANCH = "baseBranch";

    /**
     * Branch config key for issue number.
     */
    public static final String ISSUE_NUMBER = "issueNumber";

    /**
     * Branch config key for base version (version without feature/epic issue
     * number suffix).
     */
    public static final String BASE_VERSION = "baseVersion";

    /**
     * Branch config key for feature/epic start commit message (version change
     * commit message).
     */
    public static final String START_COMMIT_MESSAGE = "startCommitMessage";

    /**
     * Branch config key for version change commit.
     */
    public static final String VERSION_CHANGE_COMMIT = "versionChangeCommit";

    /**
     * Branch config key for development branch savepoint before release (last
     * commit ID of development branch before release started) that can be used
     * on release abort.
     */
    public static final String RELEASE_DEVELOPMENT_SAVEPOINT = "developmentSavepointCommitRef";

    /**
     * Branch config key for production branch savepoint before release (last
     * commit ID of production branch before release started) that can be used
     * on release abort.
     */
    public static final String RELEASE_PRODUCTION_SAVEPOINT = "productionSavepointCommitRef";

}
