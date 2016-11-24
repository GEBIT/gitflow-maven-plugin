/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Abstract git flow mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
public abstract class AbstractGitFlowMojo extends AbstractMojo {
    /** A full name of the versions-maven-plugin set goal. */
    private static final String VERSIONS_MAVEN_PLUGIN_SET_GOAL = "org.codehaus.mojo:versions-maven-plugin:2.1:set";
    /** Name of the tycho-versions-plugin set-version goal. */
    private static final String TYCHO_VERSIONS_PLUGIN_SET_GOAL = "org.eclipse.tycho:tycho-versions-plugin:set-version";

    /** System line separator. */
    protected static final String LS = System.getProperty("line.separator");

    /** Success exit code. */
    private static final int SUCCESS_EXIT_CODE = 0;

    /** Command line for Git executable. */
    private final Commandline cmdGit = new Commandline();
    /** Command line for Maven executable. */
    private final Commandline cmdMvn = new Commandline();

    /** Git flow configuration. */
    @Parameter(defaultValue = "${gitFlowConfig}")
    protected GitFlowConfig gitFlowConfig;

    /**
     * Git commit messages.
     * 
     * @since 1.2.1
     */
    @Parameter(defaultValue = "${commitMessages}")
    protected CommitMessages commitMessages;

    /**
     * Whether this is Tycho build.
     * 
     * @since 1.1.0
     */
    @Parameter(defaultValue = "false")
    protected boolean tychoBuild;

    /**
     * Whether to call Maven install goal during the mojo execution.
     * 
     * @since 1.0.5
     */
    @Parameter(property = "installProject", defaultValue = "false")
    protected boolean installProject = false;

    /**
     * Whether to allow SNAPSHOT versions in dependencies.
     * 
     * @since 1.2.2
     */
    @Parameter(property = "allowSnapshots", defaultValue = "false")
    protected boolean allowSnapshots = false;

    /**
     * Whether to fetch remote branch and compare it with the local one.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "fetchRemote", defaultValue = "true")
    protected boolean fetchRemote;

    /**
     * Whether to push to the remote.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "pushRemote", defaultValue = "true")
    protected boolean pushRemote;

    /**
     * Additional maven commands/goals after the version has been updated. Will be committed together with the version
     * change. Can contain an {@literal @}{version} placeholder which will be replaced with the new version before
     * execution.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "commandsAfterVersion", defaultValue = "")
    protected String commandsAfterVersion;


    /**
     * A regex pattern that a new feature name must match. It is also used to extract a "key" from a 
     * branch name which can be referred to as <code>@key</code> in commit messages. The extraction will be performed
     * using the first matching group (if present). You will need this if your commit messages need to refer to e.g. an
     * issue tracker key. 
     * 
     * @since 1.3.0
     */
    @Parameter(property = "featureNamePattern", required = false)
    protected String featureNamePattern;

    /**
     * When set to <code>true</code> the output generated from executing the tests is written to the console.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "printTestOutput", required = false, defaultValue = "false")
    private boolean printTestOutput;

    /**
     * When set to <code>true</code> the output generated from executing the install is written to the console.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "printInstallOutput", required = false, defaultValue = "false")
    private boolean printInstallOutput;

    /**
     * When set to <code>true</code> the output generated from executing the release goals is written to the console.
     * 
     * @since 1.3.0
     */
    @Parameter(property = "printReleaseOutput", required = false, defaultValue = "true")
    private boolean printReleaseOutput;

    /**
     * When set to <code>true</code> before checking for local changes a `git status` will be performed. This way
     * any non-real changes (CRLF) will be reconciled.
     * 
     * @since 1.3.6
     */
    @Parameter(property = "statusBeforeCheck", required = false, defaultValue = "false")
    private boolean statusBeforeCheck;

    /**
     * Whether to print commands output into the console.
     * 
     * @since 1.0.7
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose = false;

    /**
     * The path to the Maven executable. Defaults to "mvn".
     */
    @Parameter(property = "mvnExecutable")
    private String mvnExecutable;
    /**
     * The path to the Git executable. Defaults to "git".
     */
    @Parameter(property = "gitExecutable")
    private String gitExecutable;

    /** Maven project. */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    @Parameter(defaultValue = "${session}")
    private MavenSession session;
    /** Default prompter. */
    @Component
    protected Prompter prompter;
    /** Maven settings. */
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    /**
     * Initializes command line executables.
     * 
     */
    private void initExecutables() {
        if (StringUtils.isBlank(cmdMvn.getExecutable())) {
            if (StringUtils.isBlank(mvnExecutable)) {
                mvnExecutable = "mvn";
            }
            cmdMvn.setExecutable(mvnExecutable);
        }
        if (StringUtils.isBlank(cmdGit.getExecutable())) {
            if (StringUtils.isBlank(gitExecutable)) {
                gitExecutable = "git";
            }
            cmdGit.setExecutable(gitExecutable);
        }
    }

    /**
     * Gets current project version from pom.xml file.
     * 
     * @return Current project version.
     * @throws MojoFailureException
     */
    protected String getCurrentProjectVersion() throws MojoFailureException {
        try {
            // read pom.xml
            final MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            final FileReader fileReader = new FileReader(project.getFile()
                    .getAbsoluteFile());
            try {
                final Model model = mavenReader.read(fileReader);

                if (model.getVersion() == null) {
                    throw new MojoFailureException(
                            "Cannot get current project version. This plugin should be executed from the parent project.");
                }

                return model.getVersion();
            } finally {
                if (fileReader != null) {
                    fileReader.close();
                }
            }
        } catch (Exception e) {
            throw new MojoFailureException("", e);
        }
    }
    
    /**
     * Access to the project itself.
     */
    protected MavenProject getProject() {
        return project;
    }

    /**
     * Checks uncommitted changes.
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void checkUncommittedChanges() throws MojoFailureException,
            CommandLineException {
        getLog().info("Checking for uncommitted changes.");
        if (executeGitHasUncommitted()) {
            throw new MojoFailureException(
                    "You have some uncommitted files. Commit or discard local changes in order to proceed.");
        }
    }

    @SuppressWarnings("unchecked")
    protected void checkSnapshotDependencies() throws MojoFailureException {
        getLog().info("Checking for SNAPSHOT versions in dependencies.");
        List<Dependency> list = project.getDependencies();
        for (Dependency d : list) {
            if (ArtifactUtils.isSnapshot(d.getVersion())) {
                throw new MojoFailureException(
                        "There is some SNAPSHOT dependencies in the project. Change them or ignore with `allowSnapshots` property.");
            }
        }
    }

    /**
     * Executes git commands to check for uncommitted changes.
     * 
     * @return <code>true</code> when there are uncommitted changes,
     *         <code>false</code> otherwise.
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private boolean executeGitHasUncommitted() throws MojoFailureException,
            CommandLineException {
        boolean uncommited = false;

        if (statusBeforeCheck) {
            // issue a git status first which reconciles any pseudo-changes (CRLF)
            executeGitCommand("status");
        }
        // 1 if there were differences and 0 means no differences

        // git diff --no-ext-diff --ignore-submodules --quiet --exit-code
        final CommandResult diffCommandResult = executeGitCommandExitCode(
                "diff", "--no-ext-diff", "--ignore-submodules", "--quiet",
                "--exit-code");

        String error = null;

        if (diffCommandResult.getExitCode() == SUCCESS_EXIT_CODE) {
            // git diff-index --cached --quiet --ignore-submodules HEAD --
            final CommandResult diffIndexCommandResult = executeGitCommandExitCode(
                    "diff-index", "--cached", "--quiet", "--ignore-submodules",
                    "HEAD", "--");
            if (diffIndexCommandResult.getExitCode() != SUCCESS_EXIT_CODE) {
                error = diffIndexCommandResult.getError();
                uncommited = true;
            }
        } else {
            error = diffCommandResult.getError();
            uncommited = true;
        }

        if (StringUtils.isNotBlank(error)) {
            throw new MojoFailureException(error);
        }

        return uncommited;
    }

    /**
     * Executes git config commands to set Git Flow configuration.
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void initGitFlowConfig() throws MojoFailureException,
            CommandLineException {
        gitSetConfig("gitflow.branch.master",
                gitFlowConfig.getProductionBranch());
        gitSetConfig("gitflow.branch.develop",
                gitFlowConfig.getDevelopmentBranch());

        gitSetConfig("gitflow.prefix.feature",
                gitFlowConfig.getFeatureBranchPrefix());
        gitSetConfig("gitflow.prefix.release",
                gitFlowConfig.getReleaseBranchPrefix());
        gitSetConfig("gitflow.prefix.hotfix",
                gitFlowConfig.getHotfixBranchPrefix());
        gitSetConfig("gitflow.prefix.support",
                gitFlowConfig.getSupportBranchPrefix());
        gitSetConfig("gitflow.prefix.versiontag",
                gitFlowConfig.getVersionTagPrefix());

        gitSetConfig("gitflow.origin", gitFlowConfig.getOrigin());
    }

    /**
     * Executes git config command.
     * 
     * @param name
     *            Option name.
     * @param value
     *            Option value.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    private void gitSetConfig(final String name, String value)
            throws MojoFailureException, CommandLineException {
        if (value == null || value.isEmpty()) {
            value = "\"\"";
        }

        // ignore error exit codes
        executeGitCommandExitCode("config", name, value);
    }

    /**
     * Executes git for-each-ref with <code>refname:short</code> format.
     * 
     * @param branchName
     *            Branch name to find.
     * @param firstMatch
     *            Return first match.
     * @return Branch names which matches <code>refs/heads/{branchName}*</code>.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitFindBranches(final String branchName,
            final boolean firstMatch) throws MojoFailureException,
            CommandLineException {
        String branches;
        if (firstMatch) {
            branches = executeGitCommandReturn("for-each-ref", "--count=1",
                    "--format=\"%(refname:short)\"", "refs/heads/" + branchName
                            + "*");
        } else {
            branches = executeGitCommandReturn("for-each-ref",
                    "--format=\"%(refname:short)\"", "refs/heads/" + branchName
                            + "*");
        }

        // on *nix systems return values from git for-each-ref are wrapped in
        // quotes
        // https://github.com/aleksandr-m/gitflow-maven-plugin/issues/3
        if (branches != null && !branches.isEmpty()) {
            branches = branches.replaceAll("\"", "");
        }

        return branches;
    }

    protected String gitFindBranch(final String branchName)
            throws MojoFailureException, CommandLineException {
        return executeGitCommandReturn("for-each-ref", "refs/heads/"
                + branchName);
    }

    /**
     * Executes git checkout.
     * 
     * @param branchName
     *            Branch name to checkout.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCheckout(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Checking out '" + branchName + "' branch.");

        executeGitCommand("checkout", branchName);
    }

    /**
     * Executes git checkout -b.
     * 
     * @param newBranchName
     *            Create branch with this name.
     * @param fromBranchName
     *            Create branch from this branch.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCreateAndCheckout(final String newBranchName,
            final String fromBranchName) throws MojoFailureException,
            CommandLineException {
        getLog().info(
                "Creating a new branch '" + newBranchName + "' from '"
                        + fromBranchName + "' and checking it out.");

        executeGitCommand("checkout", "-b", newBranchName, fromBranchName);
    }

    /**
     * Executes git commit -a -m.
     * 
     * @param message
     *            Commit message.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCommit(final String message) throws MojoFailureException,
            CommandLineException {
        getLog().info("Committing changes.");

        executeGitCommand("commit", "-a", "-m", message);
    }

    /**
     * Executes git commit to complete a merge.
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCommitMerge() throws MojoFailureException,
    CommandLineException {
        getLog().info("Committing changes.");
        
        executeGitCommand("commit", "--no-edit");
    }

    /**
     * Executes git rebase or git merge --no-ff or git merge.
     * 
     * @param branchName
     *            Branch name to merge.
     * @param rebase
     *            Do rebase.
     * @param noff
     *            Merge with --no-ff.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitMerge(final String branchName, boolean rebase,
            boolean noff) throws MojoFailureException, CommandLineException {
        if (rebase) {
            getLog().info("Rebasing '" + branchName + "' branch.");
            executeGitCommand("rebase", branchName);
        } else {
            String tempMergeCommitMessage = getMergeMessageFor(branchName, gitCurrentBranch());

            if (noff) {
                getLog().info("Merging (--no-ff) '" + branchName + "' branch.");
                executeGitCommand("merge", "--no-ff", "-m", tempMergeCommitMessage, branchName);
            } else {
                getLog().info("Merging '" + branchName + "' branch.");
                executeGitCommand("merge", "-m", tempMergeCommitMessage, branchName);
            }
        }
    }

    /**
     * @param aBranchName
     * @param aCurrentBranchName
     * @return
     * @throws MojoFailureException
     */
    private String getMergeMessageFor(String aBranchName, String aCurrentBranchName) throws MojoFailureException {
        String tempCommitMessage = getDefaultMergeMessageFor(aBranchName, aCurrentBranchName);

        if (commitMessages.getMergeMessagePattern() != null) {
            Map<String,String> tempReplacements = new HashMap<String, String>();
            tempReplacements.put("message", tempCommitMessage);
            String tempNewMessage = substituteStrings(commitMessages.getMergeMessagePattern(), tempReplacements);
            if (tempNewMessage != null) {
                tempCommitMessage = tempNewMessage;
            }
        }
        return tempCommitMessage;
    }

    /**
     * Returns the default commit message to use for merging a branch into another one.
     * @param aBranchToMerge the branch to merge
     * @param aCurrentBranchName the branch to merge into
     */
    private String getDefaultMergeMessageFor(String aBranchToMerge, String aCurrentBranchName) {
        if ("master".equals(aCurrentBranchName)) {
            return MessageFormat.format("Merge branch \"{0}\"", aBranchToMerge);
        } else {
            return MessageFormat.format("Merge branch \"{0}\" into {1}", aBranchToMerge, aCurrentBranchName);
        }
    }

    /**
     * Returns the commit hash of the top-most commit in the current branch.
     * If there is no commit, an exception is thrown.
     * @return the commit hash of the top-most commit in the current branch
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private String getHeadCommitHash() throws MojoFailureException, CommandLineException {
        return executeGitCommandReturn("log", "--format=%H", "-n", "1").trim();
    }

    /**
     * Returns the commit message of the top-most commit in the current branch.
     * If there is no commit, an exception is thrown.
     * @return the commit message of the top-most commit in the current branch
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private String getHeadCommitMessage() throws MojoFailureException, CommandLineException {
        return executeGitCommandReturn("log", "--format=%s", "-n", "1").trim();
    }

    /**
     * Executes git merge --no-ff.
     * 
     * @param branchName
     *            Branch name to merge.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitMergeNoff(final String branchName)
            throws MojoFailureException, CommandLineException {
        gitMerge(branchName, false, true);
    }

    /**
     * Executes git merge --squash.
     * 
     * @param branchName
     *            Branch name to merge.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitMergeSquash(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Squashing '" + branchName + "' branch.");
        executeGitCommand("merge", "--squash", branchName);
    }

    /**
     * Executes git tag -a -m.
     * 
     * @param tagName
     *            Name of the tag.
     * @param message
     *            Tag message.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitTag(final String tagName, final String message)
            throws MojoFailureException, CommandLineException {
        getLog().info("Creating '" + tagName + "' tag.");

        executeGitCommand("tag", "-a", tagName, "-m", message);
    }
    
    /**
     * Executes git symbolic-ref --short HEAD to get the current branch.
     * Throws an exception when in detached HEAD state.
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitCurrentBranch() throws MojoFailureException, CommandLineException {
        getLog().info("Retrieving current branch name.");

        return executeGitCommandReturn("symbolic-ref", "--short", "HEAD").trim();
    }

    /**
     * Executes git describe --match "[tagPrefix]*" --abbrev=0 to get the latest relase tag.
     * 
     * @param tagPrefix
     *            Prefix of release tags.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitLastReleaseTag(final String tagPrefix) throws MojoFailureException, CommandLineException {
        getLog().info("Looking for last release tag.");

        return executeGitCommandReturn("describe", "--match", tagPrefix + "*", "--abbrev=0").trim();
    }
    
    /**
     * Executes git tag --sort=-v:refname -l [tagPrefix]* to get all tags in reverse order
     * 
     * @param tagPrefix
     *            Prefix of release tags.
     * @param pattern 
     *            filter by shell pattern. Can be <code>null</code>.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String[] gitListReleaseTags(final String tagPrefix, final String pattern) throws MojoFailureException, CommandLineException {
        getLog().info("Looking for release tags.");
        
        return executeGitCommandReturn("tag", "--sort=-v:refname", "-l", 
                tagPrefix + (pattern != null ? pattern : "*")).split("\\r?\\n");
    }

    /**
     * Merges the first commit on the given branch ignoring any changes. This first commit is the commit that changed
     * the versions. 
     * 
     * @param featureBranch
     *            The feature branch name.
     * @return true if the version has been premerged and does not need to be turned back
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitTryRebaseWithoutVersionChange(String featureBranch) throws MojoFailureException, CommandLineException {
        getLog().info("Looking for branch base of " + featureBranch + ".");
        final String branchPoint = executeGitCommandReturn("merge-base", "HEAD", featureBranch).trim();
        if (branchPoint.isEmpty()) {
            throw new MojoFailureException("Failed to determine branch base of feature branch '" + featureBranch + "'.");
        }
        final String gitOutput = executeGitCommandReturn("rev-list", branchPoint + ".." + featureBranch, "--reverse");
        // get the first line only
        final int firstLineEnd = gitOutput.indexOf('\n');
        final String firstCommitOnBranch = (firstLineEnd == -1 ? gitOutput : gitOutput.substring(0, firstLineEnd).trim());
        if (firstCommitOnBranch.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("There seems to be no commit at all on the feature branch:" + gitOutput);
            }
            return false;
        }
        final String firstCommitMessage = executeGitCommandReturn("log", "-1", "--pretty=%s", firstCommitOnBranch);
        final String featureStartMessage = substituteInMessage(commitMessages.getFeatureStartMessage(), featureBranch);
        if (!firstCommitMessage.contains(featureStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit, cannot premerge.");
            }
            return false;
        }

        if (!gitHasNoMergeCommits(featureBranch, firstCommitOnBranch)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Cannot rebase due to merge commits.");
            }
            return false;
        }

        getLog().info("Removing version change commit.");
        executeGitCommand("rebase", "--no-ff", "--onto", branchPoint, firstCommitOnBranch, featureBranch);

        if (pushRemote) {
            gitBranchDeleteRemote(featureBranch);
        }
        return true;
    }

    /**
     * Returns <code>true</code> if the given branch exists on the configured origin remote.
	 * @param aFeatureBranch
	 * @return
     * @throws CommandLineException
     * @throws MojoFailureException
	 */
	protected boolean hasRemoteBranch(String aBranch) throws MojoFailureException, CommandLineException {
		String tempResult = executeGitCommandReturn("ls-remote", "--heads", gitFlowConfig.getOrigin(), aBranch);
		if (tempResult != null && tempResult.trim().endsWith(aBranch)) {
			return true;
		}
		return false;
	}

	/**
     * Execute git rev-list [branchPoint]..[branchName] --merges to check whether there are merge commits in the given feature branch 
     * from the given branch point. This is useful to determine if a rebase can be done. 
     * 
     * @param branchName
     *            The feature branch name.
     * @param branchPoint
     *            commit id of the branching point of the feature branch from develop.
     * @return true if no merge commits were found in the given range.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitHasNoMergeCommits(String branchName, String branchPoint) throws MojoFailureException, CommandLineException {
        final String mergeCommits = executeGitCommandReturn("rev-list", branchPoint + ".." + branchName, "--merges");
        return mergeCommits.trim().isEmpty();
    }

    /**
     * Executes git branch -d.
     * 
     * @param branchName
     *            Branch name to delete.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchDelete(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Deleting '" + branchName + "' branch.");

        executeGitCommand("branch", "-d", branchName);
    }

    /**
     * Executes git branch -D.
     * 
     * @param branchName
     *            Branch name to delete.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchDeleteForce(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Deleting (-D) '" + branchName + "' branch.");

        executeGitCommand("branch", "-D", branchName);
    }

    /**
     * Checks if the given branch exists on the configure origin remote,
     * and if so, executes git push [origin] --delete <branch_name>.
     * 
     * @param branchName
     *            Branch name to delete.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchDeleteRemote(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info("Deleting '" + branchName + "' branch on remote.");

        if (hasRemoteBranch(branchName)) {
            executeGitCommand("push", gitFlowConfig.getOrigin(), "--delete", branchName);
        }
    }

    /**
     * Executes git fetch and compares local branch with the remote.
     * 
     * @param branchName
     *            Branch name to fetch and compare.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitFetchRemoteAndCompare(final String branchName)
            throws MojoFailureException, CommandLineException {
        getLog().info(
                "Fetching remote branch '" + gitFlowConfig.getOrigin() + " "
                        + branchName + "'.");

        CommandResult result = executeGitCommandExitCode("fetch", "--quiet",
                gitFlowConfig.getOrigin(), branchName);

        if (result.getExitCode() == SUCCESS_EXIT_CODE) {
            getLog().info(
                    "Comparing local branch '" + branchName + "' with remote '"
                            + gitFlowConfig.getOrigin() + "/" + branchName
                            + "'.");
            String revlistout = executeGitCommandReturn("rev-list",
                    "--left-right", "--count", branchName + "..."
                            + gitFlowConfig.getOrigin() + "/" + branchName);

            String[] counts = org.apache.commons.lang3.StringUtils.split(
                    revlistout, '\t');
            if (counts != null && counts.length > 1) {
                if (!"0".equals(org.apache.commons.lang3.StringUtils
                        .deleteWhitespace(counts[1]))) {
                    throw new MojoFailureException(
                            "Remote branch is ahead of the local branch. Execute git pull.");
                }
            }
        } else {
            getLog().warn(
                    "There were some problems fetching remote branch '"
                            + gitFlowConfig.getOrigin()
                            + " "
                            + branchName
                            + "'. You can turn off remote branch fetching by setting the 'fetchRemote' parameter to false.");
        }
    }

    /**
     * Executes git push, optionally with the <code>--follow-tags</code>
     * argument.
     * 
     * @param branchName
     *            Branch name to push.
     * @param pushTags
     *            If <code>true</code> adds <code>--follow-tags</code> argument
     *            to the git <code>push</code> command.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitPush(final String branchName, boolean pushTags)
            throws MojoFailureException, CommandLineException {
        getLog().info(
                "Pushing '" + branchName + "' branch" + " to '"
                        + gitFlowConfig.getOrigin() + "'.");

        if (pushTags) {
            executeGitCommand("push", "--quiet", "--follow-tags",
                    gitFlowConfig.getOrigin(), branchName);
        } else {
            executeGitCommand("push", "--quiet", gitFlowConfig.getOrigin(),
                    branchName);
        }
    }

    /**
     * Executes <code>git for-each-ref refs/heads/[branch name]</code> to find an existing branch.
     * 
     * @param branchName
     *            name of the branch to check for.
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitBranchExists(final String branchName)
            throws MojoFailureException, CommandLineException {
        // git for-each-ref refs/heads/support/...
        final String branchResult = executeGitCommandReturn("for-each-ref",
                "refs/heads/" + branchName);
        return (StringUtils.isNotBlank(branchResult));
    }

    /**
     * Executes <code>git for-each-ref refs/tags/[tag name]</code> to find an existing tag.
     * 
     * @param tagName
     *            name of the tag to check for.
     * @return true if a tag with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitTagExists(final String tagName)
            throws MojoFailureException, CommandLineException {
        // git for-each-ref refs/tags/...
        final String tagResult = executeGitCommandReturn("for-each-ref",
                "refs/tags/" + tagName);
        return (StringUtils.isNotBlank(tagResult));
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     * 
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitRebaseBranchInProcess()
            throws MojoFailureException, CommandLineException {
        final String gitDir = executeGitCommandReturn("rev-parse", "--git-dir").trim();
        final File headNameFile = FileUtils.getFile(gitDir, "rebase-apply/head-name");
        if (!headNameFile.exists()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(headNameFile + " not found in " + gitDir);
            }
            return null;
        }
        String headName;
        try {
            headName = FileUtils.readFileToString(headNameFile);
        } catch (IOException e) {
            throw new MojoFailureException("Failed to check for currently rebasing branch.", e);
        }
        final String branchRef = headName.trim();
        if (!branchRef.startsWith("refs/heads/")) {
            throw new MojoFailureException("Illegal rebasing branch reference: " + branchRef);
        }
        final String tempBranchName = branchRef.substring("refs/heads/".length());
        if (!tempBranchName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
            throw new MojoFailureException("Rebasing branch is not a feature branch: " + branchRef);
        }
        return tempBranchName;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     * 
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeBranchInProcess()
            throws MojoFailureException, CommandLineException {
        final String gitDir = executeGitCommandReturn("rev-parse", "--git-dir").trim();
        final File mergeHeadNameFile = FileUtils.getFile(gitDir, "MERGE_HEAD");
        if (!mergeHeadNameFile.exists()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(mergeHeadNameFile + " not found in " + gitDir);
            }
            return null;
        }
        final String currentBranchName = executeGitCommandReturn("rev-parse", "--abbrev-ref", "HEAD").trim();
        if (StringUtils.isBlank(currentBranchName)) {
            throw new MojoFailureException("Failed to obtain current branch name.");
        }
        if (!currentBranchName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
            throw new MojoFailureException("Merge target branch is not a feature branch: " + currentBranchName);
        }
        return currentBranchName;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     * 
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitRebaseContinue()
            throws MojoFailureException, CommandLineException {
        executeGitCommand("rebase", "--continue");
    }

    /**
     * Executes 'set' goal of versions-maven-plugin or 'set-version' of
     * tycho-versions-plugin in case it is tycho build.
     * 
     * @param version
     *            New version to set.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnSetVersions(final String version)
            throws MojoFailureException, CommandLineException {
        getLog().info("Updating version(s) to '" + version + "'.");

        if (tychoBuild) {
            executeMvnCommand(false, TYCHO_VERSIONS_PLUGIN_SET_GOAL, "-DnewVersion="
                    + version, "-Dtycho.mode=maven");
        } else {
            executeMvnCommand(false, VERSIONS_MAVEN_PLUGIN_SET_GOAL, "-DnewVersion="
                    + version, "-DgenerateBackupPoms=false");
        }
        for (String command : getCommandsAfterVersion()) {
            try {
                executeMvnCommand(false, CommandLineUtils.translateCommandline(
                        command.replaceAll("\\@\\{version\\}", version)));
            } catch (Exception e) {
                throw new MojoFailureException("Failed to execute " + command, e);
            }
        }
    }

    /**
     * Get the command specific additional commands to execute when a version
     * changes.
     * 
     * @return a new unmodifiable list with the command.
     */
    protected List<String> getCommandsAfterVersion() throws MojoFailureException {
        if (commandsAfterVersion.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(commandsAfterVersion);
    }

    /**
     * Executes mvn clean test.
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnCleanTest() throws MojoFailureException,
            CommandLineException {
        getLog().info("Cleaning and testing the project.");
        if (tychoBuild) {
            executeMvnCommand(printTestOutput, "clean", "verify");
        } else {
            executeMvnCommand(printTestOutput, "clean", "test");
        }
    }

    /**
     * Executes mvn clean install.
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnCleanInstall() throws MojoFailureException,
            CommandLineException {
        getLog().info("Cleaning and installing the project.");

        executeMvnCommand(printInstallOutput, "clean", "install");
    }

    /**
     * Executes mvn [goals].
     * 
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnGoals(String goals) throws MojoFailureException,
            CommandLineException {
        getLog().info("Executing mvn " + goals + ".");
        try {
            executeMvnCommand(printReleaseOutput, CommandLineUtils.translateCommandline(goals));
        } catch (Exception e) {
            throw new MojoFailureException("Failed to execute mvn " + goals, e);
        }
    }

    /**
     * Executes Git command and returns output.
     * 
     * @param args
     *            Git command line arguments.
     * @return Command output.
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private String executeGitCommandReturn(final String... args)
            throws CommandLineException, MojoFailureException {
        return executeCommand(cmdGit, true, args).getOut();
    }

    /**
     * Executes Git command without failing on non successful exit code.
     * 
     * @param args
     *            Git command line arguments.
     * @return Command result.
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private CommandResult executeGitCommandExitCode(final String... args)
            throws CommandLineException, MojoFailureException {
        return executeCommand(cmdGit, false, args);
    }

    /**
     * Executes Git command.
     * 
     * @param args
     *            Git command line arguments.
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private void executeGitCommand(final String... args)
            throws CommandLineException, MojoFailureException {
        executeCommand(cmdGit, true, args);
    }

    /**
     * Executes Maven command.
     * 
     * @param copyOutput
     *            Copy output to console.
     * @param args
     *            Maven command line arguments.
     * @throws CommandLineException
     * @throws MojoFailureException
     */
    private void executeMvnCommand(boolean copyOutput, final String... args)
            throws CommandLineException, MojoFailureException {
        String[] effectiveArgs = args;
        if (session.getRequest().getUserSettingsFile() != null) {
            effectiveArgs = new String[args.length + 2];
            effectiveArgs[0] = "-s";
            effectiveArgs[1] = session.getRequest().getUserSettingsFile().getAbsolutePath();
            System.arraycopy(args, 0, effectiveArgs, 2, args.length);
        } else {
            effectiveArgs = args;
        }

        if (copyOutput) {
            executeCommandCopyOut(cmdMvn, true, effectiveArgs);
        } else {
            executeCommand(cmdMvn, true, effectiveArgs);
        }
    }

    protected String substituteInMessage(final String message) throws MojoFailureException {
        return substituteInMessage(message, null);
    }

    /**
     * Substitute keys of the form <code>@{name}</code> in the messages. By
     * default knows about <code>key</code>, which is extracted from the feature
     * branch name and all project properties.
     * 
     * @param message
     *            the message to process.
     * @param featureBranchName
     *            the branch name without prefix used to extract the key from.
     * @return the message with applied substitutions
     * @see #lookupKey(String)
     */
    protected String substituteInMessage(final String message, final String featureBranchName) throws MojoFailureException {
        StrSubstitutor s = new StrSubstitutor(new StrLookup() {
            @Override
            public String lookup(String key) {
                if ("key".equals(key)) {
                    if (featureBranchName == null) {
                        throw new IllegalStateException("@{key} is used, but not a feature branch.");
                    }
                    if (featureNamePattern == null) {
                        throw new IllegalStateException("@{key} is used, but no <featureNamePattern> specified.");
                    }
                    Matcher m = Pattern.compile(featureNamePattern).matcher(featureBranchName);
                    if (!m.matches()) {
                        // retry with prefixe removed
                        if (featureBranchName.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
                            m = Pattern.compile(featureNamePattern).matcher(
                                    featureBranchName.substring(gitFlowConfig.getFeatureBranchPrefix().length()));
                        }
                        if (!m.matches()) {
                            throw new IllegalStateException(
                                    "@{key} is used, but feature branch does not conform to <featureNamePattern> specified.");
                        }
                    }
                    if (m.groupCount() == 0){
                        throw new IllegalStateException(
                                "@{key} is used, but <featureNamePattern> does not contain a matching group.");
                    }
                    return m.group(1);
                }
                return lookupKey(key);
            }
        }, "@{", "}", '@');
        try {
            return s.replace(message);
        } catch (IllegalStateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    /**
     * Performs strings replacements in the given message, specified with the
     * given replacement map. Placeholders in the message must be declared as
     * @{someName} while the key in the map shall be just someName. If the map
     * does not contain a found placeholder, it will also look in the project properties.
     * @param aMessage
     * @param someReplacements
     * @return
     * @throws MojoFailureException
     */
    protected String substituteStrings(final String aMessage, final Map<String,String> someReplacements) throws MojoFailureException {
        StrSubstitutor s = new StrSubstitutor(new StrLookup() {
            @Override
            public String lookup(String key) {
                String tempReplacement = someReplacements.get(key);
                if (tempReplacement == null) {
                    tempReplacement = lookupKey(key);
                    if (tempReplacement == null) {
                        throw new IllegalStateException("@{" + key + "} is used, but no replacement provided.");
                    }
                }
                return tempReplacement;
            }
        }, "@{", "}", '@');
        try {
            return s.replace(aMessage);
        } catch (IllegalStateException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    /**
     * Lookup keys from the project properties.
     * @return <code>null</code> if not found.
     */
    protected String lookupKey(String key) {
        return project.getProperties().getProperty(key);
    }

    /**
     * Executes command line.
     * 
     * @param cmd
     *            Command line.
     * @param failOnError
     *            Whether to throw exception on NOT success exit code.
     * @param args
     *            Command line arguments.
     * @return {@link CommandResult} instance holding command exit code, output
     *         and error if any.
     * @throws CommandLineException
     * @throws MojoFailureException
     *             If <code>failOnError</code> is <code>true</code> and command
     *             exit code is NOT equals to 0.
     */
    private CommandResult executeCommandCopyOut(final Commandline cmd,
            final boolean failOnError, final String... args)
            throws CommandLineException, MojoFailureException {
        // initialize executables
        initExecutables();

        if (getLog().isDebugEnabled()) {
            getLog().debug(
                    cmd.getExecutable() + " " + StringUtils.join(args, " "));
        }

        cmd.clearArgs();
        cmd.addArguments(args);

        final StringBufferStreamConsumer out = new StringBufferStreamConsumer(
                verbose);

        final CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

        // execute
        final int exitCode = CommandLineUtils.executeCommandLine(cmd, new StreamConsumer() {
            @Override
            public void consumeLine(String line) {
                out.consumeLine(line);
                System.out.println(line);
            }
        }, new StreamConsumer() {
            @Override
            public void consumeLine(String line) {
                err.consumeLine(line);
                System.err.println(line);
            }
        });

        String errorStr = err.getOutput();
        String outStr = out.getOutput();

        if (failOnError && exitCode != SUCCESS_EXIT_CODE) {
            // not all commands print errors to error stream
            if (StringUtils.isBlank(errorStr) && StringUtils.isNotBlank(outStr)) {
                errorStr = outStr;
            }

            throw new MojoFailureException(errorStr);
        }

        return new CommandResult(exitCode, null, errorStr);
    }

    /**
     * Executes command line.
     * 
     * @param cmd
     *            Command line.
     * @param failOnError
     *            Whether to throw exception on NOT success exit code.
     * @param args
     *            Command line arguments.
     * @return {@link CommandResult} instance holding command exit code, output
     *         and error if any.
     * @throws CommandLineException
     * @throws MojoFailureException
     *             If <code>failOnError</code> is <code>true</code> and command
     *             exit code is NOT equals to 0.
     */
    private CommandResult executeCommand(final Commandline cmd,
            final boolean failOnError, final String... args)
            throws CommandLineException, MojoFailureException {
        // initialize executables
        initExecutables();

        if (getLog().isDebugEnabled()) {
            getLog().debug(
                    cmd.getExecutable() + " " + StringUtils.join(args, " "));
        }

        cmd.clearArgs();
        cmd.addArguments(args);

        final StringBufferStreamConsumer out = new StringBufferStreamConsumer(
                verbose);

        final CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

        // execute
        final int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

        String errorStr = err.getOutput();
        String outStr = out.getOutput();

        if (failOnError && exitCode != SUCCESS_EXIT_CODE) {
            // not all commands print errors to error stream
            if (StringUtils.isBlank(errorStr) && StringUtils.isNotBlank(outStr)) {
                errorStr = outStr;
            }

            throw new MojoFailureException(errorStr);
        }

        return new CommandResult(exitCode, outStr, errorStr);
    }

    private static class CommandResult {
        private final int exitCode;
        private final String out;
        private final String error;

        private CommandResult(final int exitCode, final String out,
                final String error) {
            this.exitCode = exitCode;
            this.out = out;
            this.error = error;
        }

        /**
         * @return the exitCode
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * @return the out
         */
        public String getOut() {
            return out;
        }

        /**
         * @return the error
         */
        public String getError() {
            return error;
        }
    }
}
