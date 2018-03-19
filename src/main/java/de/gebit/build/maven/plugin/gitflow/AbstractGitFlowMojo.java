/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package de.gebit.build.maven.plugin.gitflow;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.SingleResponseValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Abstract git flow mojo.
 *
 * @author Aleksandr Mashchenko
 */
public abstract class AbstractGitFlowMojo extends AbstractMojo {

    /**
     * A property key for an alternative maven cmd executable that can be
     * defined in request user properties.
     */
    public static final String USER_PROPERTY_KEY_CMD_MVN_EXECUTABLE = "cmd.mvn.executable";

    /**
     * A property key for argumetns (array of Strings) for the alternative maven
     * cmd executable that will be prepended to other arguments.
     */
    public static final String USER_PROPERTY_KEY_CMD_MVN_ARGS_PREPEND = "cmd.mvn.args.prepend";

    /**
     * A property key for argumetns (array of Strings) for the alternative maven
     * cmd executable that will be appended to other arguments.
     */
    public static final String USER_PROPERTY_KEY_CMD_MVN_ARGS_APPEND = "cmd.mvn.args.append";

    /**
     * A property key for a flag that determines if an external git editor is
     * used.
     */
    public static final String USER_PROPERTY_KEY_EXTERNAL_GIT_EDITOR_USED = "external.git.editor.used";

    /** A full name of the versions-maven-plugin set goal. */
    private static final String VERSIONS_MAVEN_PLUGIN_SET_GOAL = "org.codehaus.mojo:versions-maven-plugin:2.1:set";

    /** Name of the tycho-versions-plugin set-version goal. */
    private static final String TYCHO_VERSIONS_PLUGIN_SET_GOAL = "org.eclipse.tycho:tycho-versions-plugin:set-version";

    /** System line separator. */
    protected static final String LS = System.getProperty("line.separator");

    /** Success exit code. */
    private static final int SUCCESS_EXIT_CODE = 0;

    /** Command line for Git executable. */
    private final Commandline cmdGit = new ShellCommandLine();

    /** Command line for Maven executable. */
    private final Commandline cmdMvn = new ShellCommandLine();

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
     * Additional maven commands/goals after the version has been updated. Will
     * be committed together with the version change. Can contain an
     * {@literal @}{version} placeholder which will be replaced with the new
     * version before execution.
     *
     * @since 1.3.0
     */
    @Parameter(property = "commandsAfterVersion", defaultValue = "")
    protected String commandsAfterVersion;

    /**
     * A regex pattern that a new feature name must match. It is also used to
     * extract a "key" from a branch name which can be referred to as
     * <code>@key</code> in commit messages. The extraction will be performed
     * using the first matching group (if present). You will need this if your
     * commit messages need to refer to e.g. an issue tracker key.
     *
     * @since 1.3.0
     */
    @Parameter(property = "featureNamePattern", required = false)
    protected String featureNamePattern;

    /**
     * When set to <code>true</code> the output generated from executing the
     * tests is written to the console.
     *
     * @since 1.3.0
     */
    @Parameter(property = "printTestOutput", required = false, defaultValue = "false")
    private boolean printTestOutput;

    /**
     * When set to <code>true</code> the output generated from executing the
     * install is written to the console.
     *
     * @since 1.3.0
     */
    @Parameter(property = "printInstallOutput", required = false, defaultValue = "false")
    private boolean printInstallOutput;

    /**
     * When set to <code>true</code> the output generated from executing the
     * release goals is written to the console.
     *
     * @since 1.3.0
     */
    @Parameter(property = "printReleaseOutput", required = false, defaultValue = "true")
    private boolean printReleaseOutput;

    /**
     * When set to <code>true</code> before checking for local changes a `git
     * status` will be performed. This way any non-real changes (CRLF) will be
     * reconciled.
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
     * Optional list of properties that if present on the invocation will be
     * passed to forked mvn invocations.
     *
     * @since 1.3.9
     */
    @Parameter(property = "copyProperties", required = false)
    private String[] copyProperties;

    /**
     * Additional version commands that can prompt for user input or be
     * conditionally enabled.
     *
     * @since 1.5.2
     */
    @Parameter
    protected GitFlowParameter[] additionalVersionCommands;

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

    private ExtendedPrompter extendedPrompter;

    /**
     * Initializes command line executables.
     */
    private void initExecutables() {
        if (StringUtils.isBlank(cmdMvn.getExecutable())) {
            String basedir = session.getRequest().getBaseDirectory();
            cmdMvn.setWorkingDirectory(basedir);
            if (StringUtils.isBlank(mvnExecutable)) {
                mvnExecutable = "mvn";
            }
            cmdMvn.setExecutable(mvnExecutable);
        }
        if (StringUtils.isBlank(cmdGit.getExecutable())) {
            String basedir = session.getRequest().getBaseDirectory();
            cmdGit.setWorkingDirectory(basedir);
            if (StringUtils.isBlank(gitExecutable)) {
                gitExecutable = "git";
            }
            cmdGit.setExecutable(gitExecutable);
            cmdGit.addEnvironment("LANG", "en");
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeGoal();
        } catch (CommandLineException e) {
            String message = "External command execution failed with error:\n" + e.getMessage()
                    + "\n\nPlease report the error in the GBLD JIRA.";
            throw new MojoExecutionException(getExceptionMessagePrefix() + message + getExceptionMessageSuffix(), e);
        } catch (MojoExecutionException | MojoFailureException e) {
            decorateExceptionMessage(e);
            throw e;
        }
    }

    /**
     * Perform whatever build-process behavior this <code>Mojo</code>
     * implements.
     *
     * @throws CommandLineException
     *             if an unexpected problem on execution of external commands
     *             occurs
     * @throws MojoExecutionException
     *             if an unexpected problem occurs. Throwing this exception
     *             causes a "BUILD ERROR" message to be displayed
     * @throws MojoFailureException
     *             if an expected problem (such as a compilation failure)
     *             occurs. Throwing this exception causes a "BUILD FAILURE"
     *             message to be displayed
     */
    protected abstract void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException;

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
            final FileReader fileReader = new FileReader(project.getFile().getAbsoluteFile());
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
    protected void checkUncommittedChanges() throws MojoFailureException, CommandLineException {
        getLog().info("Checking for uncommitted changes.");
        if (executeGitHasUncommitted()) {
            throw new GitFlowFailureException("You have some uncommitted files.",
                    "Commit or discard local changes in order to proceed.",
                    "'git add' and 'git commit' to commit your changes",
                    "'git reset --hard' to throw away your changes");
        }
    }

    private boolean isTerminalColorEnabled() {
        try {
            Class<?> messageUtilsClass = Class.forName("org.fusesource.jansi.Ansi");
            Method isColorEnabledMethod = messageUtilsClass.getMethod("isEnabled");
            Object result = isColorEnabledMethod.invoke(null);
            if (result instanceof Boolean) {
                return (boolean) result;
            }
        } catch (Exception exc) {
            // NOP
        }
        return false;
    }

    protected void decorateExceptionMessage(AbstractMojoExecutionException e) {
        String message = e.getMessage();
        String longMessage = e.getLongMessage();
        if (message != null) {
            message = getExceptionMessagePrefix() + message;
        } else if (longMessage != null) {
            longMessage = getExceptionMessagePrefix() + longMessage;
        }
        if (longMessage != null) {
            longMessage += getExceptionMessageSuffix();
        } else if (message != null) {
            message += getExceptionMessageSuffix();
        }
        try {
            if (message != null) {
                Field field = Throwable.class.getDeclaredField("detailMessage");
                field.setAccessible(true);
                field.set(e, message);
            } else if (longMessage != null) {
                Field field = AbstractMojoExecutionException.class.getDeclaredField("longMessage");
                field.setAccessible(true);
                field.set(e, longMessage);
            }
        } catch (Exception exc) {
            // NOP
        }
    }

    private String getExceptionMessagePrefix() {
        if (isTerminalColorEnabled()) {
            return "\n\n\u001B[33m############################ Gitflow problem ###########################\u001B\u005Bm\n";
        } else {
            return "\n\n############################ Gitflow problem ###########################\n";
        }
    }

    private String getExceptionMessageSuffix() {
        if (isTerminalColorEnabled()) {
            return "\n\u001B[33m########################################################################\u001B\u005Bm\n";
        } else {
            return "\n########################################################################\n";
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
    protected boolean executeGitHasUncommitted() throws MojoFailureException, CommandLineException {
        boolean uncommited = false;

        if (statusBeforeCheck) {
            // issue a git status first which reconciles any pseudo-changes
            // (CRLF)
            executeGitCommand("status");
        }
        // 1 if there were differences and 0 means no differences

        // git diff --no-ext-diff --ignore-submodules --quiet --exit-code
        final CommandResult diffCommandResult = executeGitCommandExitCode("diff", "--no-ext-diff",
                "--ignore-submodules", "--quiet", "--exit-code");

        String error = null;

        if (diffCommandResult.getExitCode() == SUCCESS_EXIT_CODE) {
            // git diff-index --cached --quiet --ignore-submodules HEAD --
            final CommandResult diffIndexCommandResult = executeGitCommandExitCode("diff-index", "--cached", "--quiet",
                    "--ignore-submodules", "HEAD", "--");
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
    protected void initGitFlowConfig() throws MojoFailureException, CommandLineException {
        gitSetConfig("gitflow.branch.master", gitFlowConfig.getProductionBranch());
        gitSetConfig("gitflow.branch.develop", gitFlowConfig.getDevelopmentBranch());

        gitSetConfig("gitflow.prefix.feature", gitFlowConfig.getFeatureBranchPrefix());
        gitSetConfig("gitflow.prefix.release", gitFlowConfig.getReleaseBranchPrefix());
        gitSetConfig("gitflow.prefix.hotfix", gitFlowConfig.getHotfixBranchPrefix());
        gitSetConfig("gitflow.prefix.support", gitFlowConfig.getMaintenanceBranchPrefix());
        gitSetConfig("gitflow.prefix.versiontag", gitFlowConfig.getVersionTagPrefix());

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
    protected void gitSetConfig(final String name, String value) throws MojoFailureException, CommandLineException {
        if (value == null || value.isEmpty()) {
            value = "\"\"";
        }

        // ignore error exit codes
        executeGitCommandExitCode("config", name, value);
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
    protected String gitGetConfig(final String name) throws MojoFailureException, CommandLineException {

        // ignore error exit codes
        CommandResult result = executeGitCommandExitCode("config", "--get", name);
        if (result.exitCode == 0) {
            return result.getOut().trim();
        }
        return null;
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
    protected String gitFindBranches(final String branchName, final boolean firstMatch)
            throws MojoFailureException, CommandLineException {
        String branches;
        if (firstMatch) {
            branches = executeGitCommandReturn("for-each-ref", "--count=1", "--format=\"%(refname:short)\"",
                    "refs/heads/" + branchName + "*");
        } else {
            branches = executeGitCommandReturn("for-each-ref", "--format=\"%(refname:short)\"",
                    "refs/heads/" + branchName + "*");
        }

        // on *nix systems return values from git for-each-ref are wrapped in
        // quotes
        // https://github.com/aleksandr-m/gitflow-maven-plugin/issues/3
        if (branches != null && !branches.isEmpty()) {
            branches = branches.replaceAll("\"", "");
        }

        return branches;
    }

    protected List<String> gitAllFeatureBranches() throws MojoFailureException, CommandLineException {
        return gitAllBranches(gitFlowConfig.getFeatureBranchPrefix());
    }

    protected List<String> gitAllBranches(String branchNamePrefix) throws MojoFailureException, CommandLineException {
        List<String> branches = new ArrayList<String>();
        branches.addAll(gitRemoteBranches(branchNamePrefix));
        List<String> localBranches = gitLocalBranches(branchNamePrefix);
        for (String localBranche : localBranches) {
            if (!branches.contains(localBranche)) {
                branches.add(localBranche);
            }
        }
        return branches;
    }

    protected List<String> gitLocalBranches(String branchNamePrefix) throws MojoFailureException, CommandLineException {
        String tempCmdResult = executeGitCommandReturn("for-each-ref", "--format=%(refname)", "refs/heads/").trim();
        if (!StringUtils.isBlank(tempCmdResult)) {
            String[] lines = tempCmdResult.split("\r?\n");
            List<String> result = new ArrayList<>();
            String prefix = "refs/heads/" + branchNamePrefix;
            for (int i = 0; i < lines.length; ++i) {
                if (lines[i].startsWith(prefix)) {
                    result.add(lines[i].substring("refs/heads/".length()));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    protected List<String> gitRemoteBranches(String branchNamePrefix)
            throws MojoFailureException, CommandLineException {
        if (fetchRemote) {
            String tempCmdResult = executeGitCommandReturn("ls-remote", "--heads", gitFlowConfig.getOrigin());
            String pattern = "[a-zA-Z0-9]+\\s*refs/heads/(\\Q" + branchNamePrefix + "\\E.*)";
            if (tempCmdResult != null) {
                String[] lines = tempCmdResult.split("\r?\n");
                Pattern p = Pattern.compile(pattern);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < lines.length; ++i) {
                    Matcher m = p.matcher(lines[i]);
                    if (m.matches()) {
                        result.add(m.group(1));
                    }
                }
                return result;
            }
        } else {
            return gitFetchedRemoteBranches(branchNamePrefix);
        }
        return Collections.emptyList();
    }

    protected List<String> gitFetchedRemoteBranches(String branchNamePrefix)
            throws MojoFailureException, CommandLineException {
        String originPrefix = "refs/remotes/" + gitFlowConfig.getOrigin() + "/";
        String tempCmdResult = executeGitCommandReturn("for-each-ref", "--format=%(refname)", originPrefix).trim();
        if (!StringUtils.isBlank(tempCmdResult)) {
            String[] lines = tempCmdResult.split("\r?\n");
            List<String> result = new ArrayList<>();
            String prefix = originPrefix + branchNamePrefix;
            for (int i = 0; i < lines.length; ++i) {
                if (lines[i].startsWith(prefix)) {
                    result.add(lines[i].substring(originPrefix.length()));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    protected String gitFindBranch(final String branchName) throws MojoFailureException, CommandLineException {
        return executeGitCommandReturn("for-each-ref", "refs/heads/" + branchName);
    }

    protected String gitFindRemoteBranch(String branchName) throws MojoFailureException, CommandLineException {
        if (fetchRemote) {
            getLog().info("Fetching remote branch '" + branchName + "' from '" + gitFlowConfig.getOrigin() + "'.");
            executeGitCommandExitCode("fetch", "--quiet", gitFlowConfig.getOrigin(), branchName);
        }
        return executeGitCommandReturn("for-each-ref", "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + branchName);
    }

    /**
     * Executes git checkout.
     *
     * @param branchName
     *            Branch name to checkout. You can give a commit id, too. In
     *            this case you will end up with a detached HEAD.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCheckout(final String branchName) throws MojoFailureException, CommandLineException {
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
    protected void gitCreateAndCheckout(final String newBranchName, final String fromBranchName)
            throws MojoFailureException, CommandLineException {
        getLog().info(
                "Creating a new branch '" + newBranchName + "' from '" + fromBranchName + "' and checking it out.");

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
    protected void gitCommit(final String message) throws MojoFailureException, CommandLineException {
        getLog().info("Committing changes.");

        executeGitCommand("commit", "-a", "-m", message);
    }

    /**
     * Executes git commit to complete a merge.
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitCommitMerge() throws MojoFailureException, CommandLineException {
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
    protected void gitMerge(final String branchName, boolean rebase, boolean noff)
            throws MojoFailureException, CommandLineException {
        if (rebase) {
            getLog().info("Rebasing '" + branchName + "' branch.");
            CommandResult tempResult = executeGitCommandExitCode("rebase", branchName);
            if (tempResult.getExitCode() != SUCCESS_EXIT_CODE) {
                // not all commands print errors to error stream
                String errorStr = tempResult.getError();
                if (StringUtils.isBlank(errorStr) && StringUtils.isNotBlank(tempResult.getOut())) {
                    errorStr = tempResult.getOut();
                } else {
                    getLog().debug("Command output: " + tempResult.getOut());
                }

                Pattern p = Pattern.compile("Patch failed at \\d+ .*");
                Matcher m = p.matcher(tempResult.getOut());
                if (m.find()) {
                    errorStr += "\n" + m.group();
                }
                throw new MojoFailureException(errorStr);
            }

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
     * Update initial version commit to new version on development branch
     *
     * @param featureBranchVersion
     *            the desired version on the feature branch
     */
    protected void gitRebaseFeatureCommit(final String featureIssue) throws MojoFailureException, CommandLineException {
        // use OURS = theirs in rebase
        executeGitCommand("checkout", "--ours", ".");

        // read the version
        String tempDevelopmentVersion = getCurrentProjectVersion();

        try {
            final DefaultVersionInfo versionInfo = new DefaultVersionInfo(tempDevelopmentVersion);
            String tempFeatureVersion = versionInfo.getReleaseVersionString() + "-" + featureIssue + "-"
                    + Artifact.SNAPSHOT_VERSION;

            // set desired version
            mvnSetVersions(tempFeatureVersion);

            // add changes to initial commit
            executeGitCommand("add", "-u");
        } catch (VersionParseException ex) {
            throw new MojoFailureException("Failed to create update version for feature branch.", ex);
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
            Map<String, String> tempReplacements = new HashMap<String, String>();
            tempReplacements.put("message", tempCommitMessage);
            String tempNewMessage = substituteStrings(commitMessages.getMergeMessagePattern(), tempReplacements);
            if (tempNewMessage != null) {
                tempCommitMessage = tempNewMessage;
            }
        }
        return tempCommitMessage;
    }

    /**
     * Returns the default commit message to use for merging a branch into
     * another one.
     *
     * @param aBranchToMerge
     *            the branch to merge
     * @param aCurrentBranchName
     *            the branch to merge into
     */
    private String getDefaultMergeMessageFor(String aBranchToMerge, String aCurrentBranchName) {
        if ("master".equals(aCurrentBranchName)) {
            return MessageFormat.format("Merge branch {0}", aBranchToMerge);
        } else {
            return MessageFormat.format("Merge branch {0} into {1}", aBranchToMerge, aCurrentBranchName);
        }
    }

    /**
     * Returns the commit hash of the top-most commit in the current branch. If
     * there is no commit, an exception is thrown.
     *
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
     *
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
    protected void gitMergeNoff(final String branchName) throws MojoFailureException, CommandLineException {
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
    protected void gitMergeSquash(final String branchName) throws MojoFailureException, CommandLineException {
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
     * Executes git symbolic-ref --short HEAD to get the current branch. Throws
     * an exception when in detached HEAD state.
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitCurrentBranch() throws MojoFailureException, CommandLineException {
        getLog().info("Retrieving current branch name.");

        return executeGitCommandReturn("symbolic-ref", "--short", "HEAD").trim();
    }

    /**
     * Executes git describe --match "[tagPrefix]*" --abbrev=0 to get the latest
     * relase tag.
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
     * Executes git tag --sort=-v:refname -l [tagPrefix]* to get all tags in
     * reverse order
     *
     * @param tagPrefix
     *            Prefix of release tags.
     * @param pattern
     *            filter by shell pattern. Can be <code>null</code>.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected List<String> gitListReleaseTags(final String tagPrefix, final String pattern)
            throws MojoFailureException, CommandLineException {
        getLog().info("Looking for release tags.");

        String gitResult = executeGitCommandReturn("tag", "--sort=-v:refname", "-l",
                tagPrefix + (pattern != null ? pattern : "*"));
        if (gitResult.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(gitResult.split("\\r?\\n")));
    }

    /**
     * Merges the first commit on the given branch ignoring any changes. This
     * first commit is the commit that changed the versions.
     *
     * @param featureBranch
     *            The feature branch name.
     * @param branchPoint
     *            the branch point on both feature and development branch
     * @param versionChangeCommitId
     *            commit ID of the version change commit. Must be first commit
     *            on featuereBranch after branchPoint
     * @return true if the version has been premerged and does not need to be
     *         turned back
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitTryRebaseWithoutVersionChange(String featureBranch, String branchPoint,
            String versionChangeCommitId) throws MojoFailureException, CommandLineException {
        if (!gitHasNoMergeCommits(featureBranch, versionChangeCommitId)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Cannot rebase due to merge commits.");
            }
            return false;
        }

        getLog().info("Removing version change commit.");
        try {
            executeGitCommand("rebase", "--no-ff", "--onto", branchPoint, versionChangeCommitId, featureBranch);
        } catch (MojoFailureException ex) {
            throw new GitFlowFailureException(ex,
                    "Automatic rebase failed.\nGit error message:\n" + StringUtils.trim(ex.getMessage()),
                    "Fix the rebase conflicts and mark them as resolved. "
                            + "After that, run 'mvn flow:feature-finish' again. Do NOT run 'git rebase --continue'.",
                    "'git status' to check the conflicts, resolve the conflicts and 'git add' to mark conflicts as resolved",
                    "'mvn flow:feature-finish' to continue feature finish process");
        }
        return true;
    }

    /**
     * Get the branch point of a feature branch.
     *
     * @param featureBranch
     *            feature branch name
     * @return commit ID of the branch point (common ancestor with the
     *         development branch)
     * @throws MojoFailureException
     *             if no branch point can be determined
     */
    protected String gitFeatureBranchBaseBranch(String featureBranch)
            throws MojoFailureException, CommandLineException {
        getLog().info("Looking for branch base of " + featureBranch + ".");

        // try all development branches
        Map<String, String> branchPointCandidates = new HashMap<>();
        String developmentBranch = gitFlowConfig.getDevelopmentBranch();
        gitFetchBranches(developmentBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), developmentBranch)) {
            branchPointCandidates.put(developmentBranch,
                    gitBranchPoint(gitFlowConfig.getOrigin() + "/" + developmentBranch, featureBranch));
        } else if (gitBranchExists(developmentBranch)) {
            branchPointCandidates.put(developmentBranch, gitBranchPoint(developmentBranch, featureBranch));
        }
        List<String> remoteMaintenanceBranches = gitRemoteMaintenanceBranches();
        if (remoteMaintenanceBranches.size() > 0) {
            gitFetchBranches(remoteMaintenanceBranches);
            for (String maintenanceBranch : remoteMaintenanceBranches) {
                branchPointCandidates.put(maintenanceBranch,
                        gitBranchPoint(gitFlowConfig.getOrigin() + "/" + maintenanceBranch, featureBranch));
            }
        }
        List<String> localMaintenanceBranches = gitLocalMaintenanceBranches();
        if (localMaintenanceBranches.size() > 0) {
            for (String maintenanceBranch : localMaintenanceBranches) {
                if (!branchPointCandidates.containsKey(maintenanceBranch)) {
                    branchPointCandidates.put(maintenanceBranch, gitBranchPoint(maintenanceBranch, featureBranch));
                }
            }
        }
        int minDistance = -1;
        String devBranch = null;
        for (Map.Entry<String, String> entry : branchPointCandidates.entrySet()) {
            String branch = entry.getKey();
            String commit = entry.getValue();
            String revlistout = executeGitCommandReturn("rev-list", "--left-right", "--count",
                    commit + "..." + featureBranch);
            String[] counts = org.apache.commons.lang3.StringUtils.split(revlistout, '\t');
            if (counts != null && counts.length > 1) {
                if (!"0".equals(org.apache.commons.lang3.StringUtils.deleteWhitespace(counts[1]))) {
                    int branchDistance = Integer
                            .parseInt(org.apache.commons.lang3.StringUtils.deleteWhitespace(counts[1]));
                    if (minDistance == -1 || branchDistance < minDistance) {
                        minDistance = branchDistance;
                        devBranch = branch;
                    }
                }
            }
        }

        if (devBranch == null) {
            if (fetchRemote) {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch
                                + "'. This indicates a severe error condition on your branches.",
                        "Please consult a gitflow expert on how to fix this!");
            } else {
                throw new GitFlowFailureException(
                        "Failed to find base branch for feature branch '" + featureBranch + "'.",
                        "Set 'fetchRemote' parameter to true in order to search for base branch also in remote repository.");
            }
        }
        getLog().debug("Feature branch is based on " + devBranch + ".");
        return devBranch;
    }

    protected void gitFetchBranches(List<String> remoteBranches) throws CommandLineException, MojoFailureException {
        gitFetchBranches(remoteBranches.toArray(new String[remoteBranches.size()]));
    }

    protected void gitFetchBranches(String... remoteBranches) throws CommandLineException, MojoFailureException {
        if (fetchRemote && remoteBranches != null && remoteBranches.length > 0) {
            List<String> remoteBranchesToBeFetched = new ArrayList<>();
            List<String> foundRemoteBranches = gitRemoteBranches("");
            for (String remoteBranch : remoteBranches) {
                if (foundRemoteBranches.contains(remoteBranch)) {
                    remoteBranchesToBeFetched.add(remoteBranch);
                }
            }
            if (remoteBranchesToBeFetched.size() > 0) {
                String[] args = ArrayUtils.addAll(new String[] { "fetch", "--quiet", gitFlowConfig.getOrigin() },
                        remoteBranchesToBeFetched.toArray(new String[remoteBranchesToBeFetched.size()]));
                executeGitCommand(args);
            }
        }
    }

    /**
     * Get the base commit (branch point) for passed feature branch.
     *
     * @param featureBranch
     *            the name of the feature branch
     * @return the base commit for feature branch
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitFeatureBranchBaseCommit(String featureBranch)
            throws MojoFailureException, CommandLineException {
        String baseBranch = gitFeatureBranchBaseBranch(featureBranch);
        gitFetchBranches(baseBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), baseBranch)) {
            baseBranch = gitFlowConfig.getOrigin() + "/" + baseBranch;
        }
        return gitBranchPoint(baseBranch, featureBranch);
    }

    /**
     * Get the first commit on the branch, which is the version change commit
     *
     * @param featureBranch
     *            feature branch name
     * @param branchPoint
     *            commit ID of the common ancestor with the development branch
     */
    protected String gitVersionChangeCommitOnFeatureBranch(String featureBranch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        final String firstCommitOnBranch = gitFirstCommitOnBranch(featureBranch, branchPoint);
        final String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
        final String featureStartMessage = substituteInMessage(commitMessages.getFeatureStartMessage(), featureBranch);
        if (!firstCommitMessage.contains(featureStartMessage)) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("First commit is not a version change commit.");
            }
            return null;
        }
        return firstCommitOnBranch;
    }

    protected String gitFirstCommitOnBranch(String branch, String branchPoint)
            throws MojoFailureException, CommandLineException {
        String gitOutput = executeGitCommandReturn("rev-list", branchPoint + ".." + branch, "--reverse");
        // get the first line only
        int firstLineEnd = gitOutput.indexOf('\n');
        String firstCommitOnBranch = (firstLineEnd == -1 ? gitOutput : gitOutput.substring(0, firstLineEnd).trim());
        if (firstCommitOnBranch.isEmpty()) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("There seems to be no commit at all on the branch '" + branch + "': " + gitOutput);
            }
            return null;
        }
        return firstCommitOnBranch;
    }

    protected String gitCommitMessage(String commit) throws MojoFailureException, CommandLineException {
        return executeGitCommandReturn("log", "-1", "--pretty=%s", commit);
    }

    protected boolean hasCommitsExceptVersionChangeCommitOnFeatureBranch(String featureBranch, String baseBranch)
            throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint(featureBranch, baseBranch);
        String gitOutput = executeGitCommandReturn("rev-list", branchPoint + ".." + featureBranch, "--count");
        if (!StringUtils.isBlank(gitOutput)) {
            int commits = Integer.parseInt(StringUtils.trim(gitOutput));
            if (commits == 0) {
                return false;
            } else if (commits == 1) {
                return StringUtils.isBlank(gitVersionChangeCommitOnFeatureBranch(featureBranch, branchPoint));
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the given branch exists on the configured
     * origin remote.
     *
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
     * List all local maintenance branches
     */
    protected List<String> gitLocalMaintenanceBranches() throws MojoFailureException, CommandLineException {
        return gitLocalBranches(gitFlowConfig.getMaintenanceBranchPrefix());
    }

    /**
     * List all remote maintenance branches
     */
    protected List<String> gitRemoteMaintenanceBranches() throws MojoFailureException, CommandLineException {
        return gitRemoteBranches(gitFlowConfig.getMaintenanceBranchPrefix());
    }

    /**
     * Execute git rev-list [branchPoint]..[branchName] --merges to check
     * whether there are merge commits in the given feature branch from the
     * given branch point. This is useful to determine if a rebase can be done.
     *
     * @param branchName
     *            The feature branch name.
     * @param branchPoint
     *            commit id of the branching point of the feature branch from
     *            develop.
     * @return true if no merge commits were found in the given range.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitHasNoMergeCommits(String branchName, String branchPoint)
            throws MojoFailureException, CommandLineException {
        final String mergeCommits = executeGitCommandReturn("rev-list", branchPoint + ".." + branchName, "--merges");
        return mergeCommits.trim().isEmpty();
    }

    /**
     * Call update-ref to explicitly set a reference for a branch.
     *
     * @param branchName
     *            Branch name to set a specific reference for
     * @param newRef
     *            Value to set the reference to
     * @throws MojoFailureException
     * @throws CommandLineException
     * @since 1.5.9
     */
    protected void gitUpdateRef(final String branchName, final String newRef)
            throws MojoFailureException, CommandLineException {
        getLog().info("Updating reference for branch '" + branchName + "'.");

        executeGitCommand("update-ref", "refs/heads/" + branchName, newRef);
    }

    /**
     * Executes git branch -d.
     *
     * @param branchName
     *            Branch name to delete.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchDelete(final String branchName) throws MojoFailureException, CommandLineException {
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
    protected void gitBranchDeleteForce(final String branchName) throws MojoFailureException, CommandLineException {
        getLog().info("Deleting (-D) '" + branchName + "' branch.");

        executeGitCommand("branch", "-D", branchName);
    }

    /**
     * Checks if the given branch exists on the configure origin remote, and if
     * so, executes git push [origin] --delete <branch_name>.
     *
     * @param branchName
     *            Branch name to delete.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchDeleteRemote(final String branchName) throws MojoFailureException, CommandLineException {
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
    protected void gitFetchRemoteAndCompare(final String branchName) throws MojoFailureException, CommandLineException {
        if (!gitFetchRemoteAndCompare(branchName, new Callable<Void>() {

            @Override
            public Void call() throws MojoFailureException {
                throw new MojoFailureException(
                        "Remote branch is ahead of the local branch " + branchName + ". Execute git pull.");
            }
        })) {
            getLog().warn("There were some problems fetching remote branch '" + gitFlowConfig.getOrigin() + " "
                    + branchName
                    + "'. You can turn off remote branch fetching by setting the 'fetchRemote' parameter to false.");
        }
    }

    /**
     * @param branchName
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitFetchRemoteAndMergeIfNecessary(final String branchName, final boolean rebase)
            throws MojoFailureException, CommandLineException {
        if (!gitFetchRemoteAndCompare(branchName, new Callable<Void>() {

            @Override
            public Void call() throws MojoFailureException, CommandLineException {
                getLog().info("Remote branch is ahead of the local branch " + branchName + ", trying to merge:");
                gitMerge(gitFlowConfig.getOrigin() + "/" + branchName, rebase, false);
                return null;
            }
        })) {
            getLog().warn("There were some problems fetching remote branch '" + gitFlowConfig.getOrigin() + " "
                    + branchName
                    + "'. You can turn off remote branch fetching by setting the 'fetchRemote' parameter to false.");
        }
    }

    /**
     * @param branchName
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitFetchRemoteAndResetIfNecessary(final String branchName)
            throws MojoFailureException, CommandLineException {
        if (!gitFetchRemoteAndCompare(branchName, new Callable<Void>() {

            @Override
            public Void call() throws MojoFailureException, CommandLineException {
                // is the remote a descendant of the local reference?
                CommandResult result = executeGitCommandExitCode("merge-base", "--is-ancestor", branchName,
                        gitFlowConfig.getOrigin() + "/" + branchName);
                if (result.getExitCode() == SUCCESS_EXIT_CODE) {
                    // then we can simply update the local branch
                    executeGitCommand("update-ref", "refs/heads/" + branchName,
                            "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + branchName);
                    return null;
                } else {
                    throw new MojoFailureException("Remote branch is ahead of the local branch " + branchName
                            + ", but cannot reset as local is not an ancestor of remote.");
                }
            }
        })) {
            getLog().warn("There were some problems fetching remote branch '" + gitFlowConfig.getOrigin() + " "
                    + branchName
                    + "'. You can turn off remote branch fetching by setting the 'fetchRemote' parameter to false.");
        }
    }

    /**
     * Executes git fetch and compares local branch with the remote.
     *
     * @param branchName
     *            Branch name to fetch and compare.
     * @return <code>true</code> if the branch exists, <code>false</code> if not
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitFetchRemoteAndCompare(final String branchName, Callable<Void> diffFunctor)
            throws MojoFailureException, CommandLineException {
        getLog().info("Fetching remote branch '" + branchName + "' from '" + gitFlowConfig.getOrigin() + "'.");

        CommandResult result = executeGitCommandExitCode("fetch", "--quiet", gitFlowConfig.getOrigin(), branchName);

        if (result.getExitCode() == SUCCESS_EXIT_CODE) {
            // if there is no local branch create it now and return
            if (!gitBranchExists(branchName)) {
                // no such local branch, create it now (then it's up to date)
                executeGitCommand("branch", branchName, gitFlowConfig.getOrigin() + "/" + branchName);
                return true;
            }

            getLog().debug("Comparing local branch '" + branchName + "' with remote '" + gitFlowConfig.getOrigin() + "/"
                    + branchName + "'.");
            String revlistout = executeGitCommandReturn("rev-list", "--left-right", "--count",
                    branchName + "..." + gitFlowConfig.getOrigin() + "/" + branchName);

            String[] counts = org.apache.commons.lang3.StringUtils.split(revlistout, '\t');
            if (counts != null && counts.length > 1) {
                if (!"0".equals(org.apache.commons.lang3.StringUtils.deleteWhitespace(counts[1]))) {
                    try {
                        diffFunctor.call();
                    } catch (MojoFailureException | CommandLineException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new MojoFailureException("Failed to perform task on remote difference.", ex);
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Asserts that the local and remote branches with passed name are on the
     * same state. If local or remote branch is ahead or branches diverge a
     * {@link MojoFailureException} will be thrown. If remote branch doesn't
     * exist no exception will be thrown. If local branch doesn't exist it will
     * be created from remote.
     *
     * @param branchName
     *            the name of the branch to be compared
     * @throws MojoFailureException
     *             if local or remote branch is ahead or both branches have some
     *             commits
     * @throws CommandLineException
     *             if a git command can't be executed
     */
    protected void gitAssertLocalAndRemoteBranchesOnSameState(String branchName)
            throws MojoFailureException, CommandLineException {
        gitAssertLocalAndRemoteBranchesOnSameState(branchName, null, null, null);
    }

    /**
     * Asserts that the local and remote branches with passed name are on the
     * same state. If local or remote branch is ahead or branches diverge a
     * {@link MojoFailureException} will be thrown. If remote branch doesn't
     * exist no exception will be thrown. If local branch doesn't exist it will
     * be created from remote.
     *
     * @param branchName
     *            the name of the branch to be compared
     * @param localAheadErrorMessage
     *            the message to be used in exception if local branch is ahead
     *            of remote (if <code>null</code> a default message will be
     *            used)
     * @param remoteAheadErrorMessage
     *            the message to be used in exception if remote branch is ahead
     *            of local (if <code>null</code> a default message will be used)
     * @param divergeErrorMessage
     *            the message to be used in exception if local and remote
     *            branches diverge (if <code>null</code> a default message will
     *            be used)
     * @throws MojoFailureException
     *             if local or remote branch is ahead or both branches have some
     *             commits
     * @throws CommandLineException
     *             if a git command can't be executed
     */
    protected void gitAssertLocalAndRemoteBranchesOnSameState(String branchName,
            GitFlowFailureInfo localAheadErrorMessage, GitFlowFailureInfo remoteAheadErrorMessage,
            GitFlowFailureInfo divergeErrorMessage) throws MojoFailureException, CommandLineException {
        gitCompareLocalAndRemoteBranches(branchName, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (localAheadErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(localAheadErrorMessage, branchName));
                }
                throw new GitFlowFailureException("Local branch is ahead of the remote branch '" + branchName + "'.",
                        "Push commits made on local branch to the remote branch in order to proceed.",
                        "'git push " + branchName + "'");
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (remoteAheadErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(remoteAheadErrorMessage, branchName));
                }
                throw new GitFlowFailureException("Remote branch is ahead of the local branch '" + branchName + "'.",
                        "Pull changes on remote branch to the local branch in order to proceed.", "'git pull'");
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (divergeErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(divergeErrorMessage, branchName));
                }
                throw new GitFlowFailureException("Local and remote branches '" + branchName + "' diverge.",
                        "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
            }
        });
    }

    protected void gitEnsureLocalBranchIsUpToDateIfExists(String branchName, GitFlowFailureInfo divergeErrorInfo)
            throws MojoFailureException, CommandLineException {
        gitCompareLocalAndRemoteBranches(branchName, null, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                gitUpdateRef(branchName, "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + branchName);
                return null;
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (divergeErrorInfo != null) {
                    throw new GitFlowFailureException(replacePlaceholders(divergeErrorInfo, branchName));
                }
                throw new GitFlowFailureException("Local and remote branches '" + branchName + "' diverge.",
                        "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
            }
        });
    }

    protected void gitEnsureLocalBranchExists(String branchName) throws MojoFailureException, CommandLineException {
        gitEnsureLocalBranchExists(branchName, null);
    }

    protected void gitEnsureLocalBranchExists(String branchName, GitFlowFailureInfo branchNotExistingErrorMessage)
            throws MojoFailureException, CommandLineException {
        if (!gitBranchExists(branchName)) {
            if (!gitFetchRemoteBranch(branchName)) {
                if (branchNotExistingErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(branchNotExistingErrorMessage, branchName));
                }
                if (fetchRemote) {
                    throw new GitFlowFailureException(
                            "Local branch '" + branchName + "' doesn't exist and can't be fetched.", null);
                } else {
                    throw new GitFlowFailureException("Local branch '" + branchName + "' doesn't exist.",
                            "Set 'fetchRemote' parameter to true in order to try to fetch branch from "
                                    + "remote repository.");
                }
            }
        }
    }

    protected void gitEnsureLocalAndRemoteBranchesAreSynchronized(String branchName,
            GitFlowFailureInfo localAheadErrorMessage, GitFlowFailureInfo divergeErrorMessage,
            GitFlowFailureInfo remoteNotExistingErrorMessage) throws MojoFailureException, CommandLineException {
        boolean remoteBranchExists = gitCompareLocalAndRemoteBranches(branchName, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (localAheadErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(localAheadErrorMessage, branchName));
                }
                throw new GitFlowFailureException("Local branch '" + branchName + "' is ahead of remote.",
                        "Push the changes in local branch '" + branchName + "' to remote in order to proceed.",
                        "'git push'");
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                gitUpdateRef(branchName, "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + branchName);
                return null;
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (divergeErrorMessage != null) {
                    throw new GitFlowFailureException(replacePlaceholders(divergeErrorMessage, branchName));
                }
                throw new GitFlowFailureException("Local and remote branches '" + branchName + "' diverge.",
                        "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
            }
        });
        if (!remoteBranchExists) {
            if (remoteNotExistingErrorMessage != null) {
                throw new GitFlowFailureException(replacePlaceholders(remoteNotExistingErrorMessage, branchName));
            }
            throw new GitFlowFailureException("Branch '" + branchName + "' doesn't exist remotely.",
                    "Push the local branch '" + branchName + "' to remote in order to proceed.", "'git push'");
        }
    }

    protected void gitEnsureCurrentLocalBranchIsUpToDate(GitFlowFailureInfo divergeErrorInfo)
            throws MojoFailureException, CommandLineException {
        String branchName = gitCurrentBranch();
        gitCompareLocalAndRemoteBranches(branchName, null, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                gitUpdateRef(branchName, "refs/remotes/" + gitFlowConfig.getOrigin() + "/" + branchName);
                executeGitCommand("reset", "--hard", "HEAD");
                return null;
            }
        }, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (divergeErrorInfo != null) {
                    throw new GitFlowFailureException(replacePlaceholders(divergeErrorInfo, branchName));
                }
                throw new GitFlowFailureException("Local and remote branches '" + branchName + "' diverge.",
                        "Rebase or merge the changes in local branch in order to proceed.", "'git pull'");
            }
        });
    }

    private GitFlowFailureInfo replacePlaceholders(GitFlowFailureInfo divergeErrorInfo, Object... replacements) {
        if (divergeErrorInfo != null) {
            String problem = divergeErrorInfo.getProblem();
            if (problem != null) {
                problem = problem.replace("'", "''");
                problem = MessageFormat.format(problem, replacements);
            }
            String solutionProposal = divergeErrorInfo.getSolutionProposal();
            if (solutionProposal != null) {
                solutionProposal = solutionProposal.replace("'", "''");
                solutionProposal = MessageFormat.format(solutionProposal, replacements);
            }
            String[] stepsToContinue = divergeErrorInfo.getStepsToContinue();
            if (stepsToContinue != null && stepsToContinue.length > 0) {
                for (int i = 0; i < stepsToContinue.length; i++) {
                    if (stepsToContinue[i] != null) {
                        stepsToContinue[i] = stepsToContinue[i].replace("'", "''");
                        stepsToContinue[i] = MessageFormat.format(stepsToContinue[i], replacements);
                    }
                }
            }
            return new GitFlowFailureInfo(problem, solutionProposal, stepsToContinue);
        }
        return null;
    }

    /**
     * Executes git fetch if parameter <code>fetchRemote</code> is enabled and
     * compares local branch with the remote. If local branch doesn't exist it
     * will be created from remote. If remote branch doesn't exist no callback
     * will be called.
     *
     * @param branchName
     *            the name of the branch to be compared
     * @param localAheadCallback
     *            the callback to be executed if local branch is ahead of remote
     * @param remoteAheadCallback
     *            the callback to be executed if remote branch is ahead of local
     * @param bothHaveChangesCallback
     *            the callback to be executed if local and remote branches have
     *            some commits
     * @return <code>true</code> if remote branch exists, <code>false</code> if
     *         not
     * @throws MojoFailureException
     * @throws CommandLineException
     *             if a git command can't be executed
     */
    protected boolean gitCompareLocalAndRemoteBranches(String branchName, Callable<Void> localAheadCallback,
            Callable<Void> remoteAheadCallback, Callable<Void> bothHaveChangesCallback)
            throws MojoFailureException, CommandLineException {
        if (!gitFetchRemoteBranch(branchName)) {
            return false;
        }
        // if there is no local branch create it now and return
        if (!gitBranchExists(branchName)) {
            // no such local branch, create it now (then it's up to date)
            executeGitCommand("branch", branchName, gitFlowConfig.getOrigin() + "/" + branchName);
            return true;
        }
        getLog().debug("Comparing local branch '" + branchName + "' with remote '" + gitFlowConfig.getOrigin() + "/"
                + branchName + "'.");
        String revlistout = executeGitCommandReturn("rev-list", "--left-right", "--count",
                branchName + "..." + gitFlowConfig.getOrigin() + "/" + branchName);
        String[] counts = org.apache.commons.lang3.StringUtils.split(revlistout, '\t');
        if (counts != null && counts.length > 1) {
            String localCommitsCount = org.apache.commons.lang3.StringUtils.deleteWhitespace(counts[0]);
            String remoteCommitsCount = org.apache.commons.lang3.StringUtils.deleteWhitespace(counts[1]);
            try {
                if (!"0".equals(localCommitsCount) && !"0".equals(remoteCommitsCount)) {
                    if (bothHaveChangesCallback != null) {
                        bothHaveChangesCallback.call();
                    }
                } else if (!"0".equals(localCommitsCount)) {
                    if (localAheadCallback != null) {
                        localAheadCallback.call();
                    }
                } else if (!"0".equals(remoteCommitsCount)) {
                    if (remoteAheadCallback != null) {
                        remoteAheadCallback.call();
                    }
                }
            } catch (MojoFailureException | CommandLineException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new MojoFailureException(
                        "Failed to perform task on differences between local and remote branches.", ex);
            }
        }
        return true;
    }

    private boolean gitFetchRemoteBranch(String branchName) throws MojoFailureException, CommandLineException {
        if (fetchRemote) {
            getLog().info("Fetching remote branch '" + branchName + "' from '" + gitFlowConfig.getOrigin() + "'.");
            CommandResult result = executeGitCommandExitCode("fetch", "--quiet", gitFlowConfig.getOrigin(), branchName);
            if (result.getExitCode() != SUCCESS_EXIT_CODE) {
                // remote branch doesn't exists
                getLog().warn("There were some problems fetching remote branch '" + gitFlowConfig.getOrigin() + "/"
                        + branchName
                        + "'. You can turn off remote branch fetching by setting the 'fetchRemote' parameter to false.");
                return false;
            }
        } else if (!gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), branchName)) {
            return false;
        }
        return true;
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
     * @param force
     *            <code>true</code> to force non-FF (e.g. for feature branches)
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitPush(final String branchName, boolean pushTags, boolean force)
            throws MojoFailureException, CommandLineException {
        getLog().info("Pushing '" + branchName + "' branch" + " to '" + gitFlowConfig.getOrigin() + "'.");

        List<String> cmd = new ArrayList<String>();
        cmd.add("push");
        cmd.add("--quiet");
        if (force) {
            cmd.add("-f");
        }
        if (pushTags) {
            cmd.add("--follow-tags");
        }
        cmd.add(gitFlowConfig.getOrigin());
        cmd.add(branchName);
        executeGitCommand(cmd.toArray(new String[cmd.size()]));
    }

    /**
     * Executes <code>git for-each-ref refs/heads/[branch name]</code> to find
     * an existing branch.
     *
     * @param branchName
     *            name of the branch to check for.
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitBranchExists(final String branchName) throws MojoFailureException, CommandLineException {
        // git for-each-ref refs/heads/support/...
        final String branchResult = executeGitCommandReturn("for-each-ref", "refs/heads/" + branchName);
        return (StringUtils.isNotBlank(branchResult));
    }

    /**
     * Executes
     * <code>git for-each-ref refs/remotes/[remote]/[branch name]</code> to find
     * an existing remote branch locally.
     *
     * @param remote
     *            the name of the remote (e.g. origin)
     * @param branchName
     *            name of the branch to check for
     * @return <code>true</code> if a remote branch with the passed name exists
     *         locally
     * @throws MojoFailureException
     *             if git command exit code is NOT equals to 0
     * @throws CommandLineException
     *             if git command can't be executed
     */
    protected boolean gitIsRemoteBranchFetched(String remote, String branchName)
            throws MojoFailureException, CommandLineException {
        // git for-each-ref refs/remotes/orign/branch
        final String branchResult = executeGitCommandReturn("for-each-ref",
                "refs/remotes/" + remote + "/" + branchName);
        return (StringUtils.isNotBlank(branchResult));
    }

    /**
     * Find the merge point between two branches
     *
     * @param branchName
     *            the branch to check for. Use HEAD for current branch.
     * @param otherBranchName
     *            the branch to compare to. Use HEAD for current branch.
     * @return the commit id of the branch point or <code>null</code> if not
     *         found
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitBranchPoint(final String branchName, final String otherBranchName)
            throws MojoFailureException, CommandLineException {
        final String branchPoint = executeGitCommandReturn("merge-base", branchName, otherBranchName).trim();
        if (branchPoint.isEmpty()) {
            return null;
        }
        return branchPoint;
    }

    protected String gitNearestAncestorCommit(String branch, Collection<String> ancestors)
            throws MojoFailureException, CommandLineException {
        int minDistance = -1;
        String nearestAncestor = null;
        for (String ancestor : ancestors) {
            int branchDistance = gitGetDistanceToAncestor(branch, ancestor);
            if (minDistance == -1 || branchDistance < minDistance) {
                minDistance = branchDistance;
                nearestAncestor = ancestor;
            }
        }
        return nearestAncestor;
    }

    protected int gitGetDistanceToAncestor(String branch, String ancestor)
            throws CommandLineException, MojoFailureException {
        String revlistout = executeGitCommandReturn("rev-list", "--first-parent", "--count", ancestor + ".." + branch);
        String count = revlistout.trim();
        return Integer.parseInt(count);
    }

    /**
     * Check if the first branch is an ancestor of the second branch.
     *
     * @param ancestorBranchName
     *            the branch to check for. Use HEAD for current branch.
     * @param childBranchName
     *            the branch to compare to. Use HEAD for current branch.
     * @return <code>true</code> if the first branch is an ancestor of the
     *         second branch
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitIsAncestorBranch(final String ancestorBranchName, final String childBranchName)
            throws MojoFailureException, CommandLineException {
        CommandResult result = executeGitCommandExitCode("merge-base", "--is-ancestor", ancestorBranchName,
                childBranchName);
        return (result.getExitCode() == SUCCESS_EXIT_CODE);
    }

    /**
     * Executes <code>git for-each-ref refs/tags/[tag name]</code> to find an
     * existing tag.
     *
     * @param tagName
     *            name of the tag to check for.
     * @return true if a tag with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected boolean gitTagExists(final String tagName) throws MojoFailureException, CommandLineException {
        // git for-each-ref refs/tags/...
        final String tagResult = executeGitCommandReturn("for-each-ref", "refs/tags/" + tagName);
        return (StringUtils.isNotBlank(tagResult));
    }

    protected boolean gitRebaseInProcess() throws MojoFailureException, CommandLineException {
        return gitGetRebaseHeadNameFileIfExists() != null;
    }

    private File gitGetRebaseHeadNameFileIfExists() throws MojoFailureException, CommandLineException {
        return gitGetRebaseFileIfExists("head-name");
    }

    private File gitGetRebaseFileIfExists(String fileName) throws MojoFailureException, CommandLineException {
        final String gitDir = executeGitCommandReturn("rev-parse", "--git-dir").trim();
        String relativePath = "rebase-apply/" + fileName;
        File headNameFile = FileUtils.getFile(gitDir, relativePath);
        String basedir = this.session.getRequest().getBaseDirectory();
        if (!headNameFile.isAbsolute()) {
            headNameFile = new File(basedir, headNameFile.getPath());
        }
        if (headNameFile.exists()) {
            return headNameFile;
        }
        // try with rebase-merge instead
        relativePath = "rebase-merge/" + fileName;
        headNameFile = FileUtils.getFile(gitDir, relativePath);
        if (!headNameFile.isAbsolute()) {
            headNameFile = new File(basedir, headNameFile.getPath());
        }
        if (headNameFile.exists()) {
            return headNameFile;
        }
        if (getLog().isDebugEnabled()) {
            getLog().debug(relativePath + " not found in " + gitDir);
        }
        return null;
    }

    protected boolean gitInteractiveRebaseInProcess() throws MojoFailureException, CommandLineException {
        return gitGetRebaseFileIfExists("interactive") != null;
    }

    protected String gitInteractiveRebaseFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        if (gitInteractiveRebaseInProcess()) {
            return gitRebaseFeatureBranchInProcess();
        }
        return null;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     *
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitRebaseFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        File headNameFile = gitGetRebaseHeadNameFileIfExists();
        if (headNameFile == null) {
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
     * Get the current commit ID.
     *
     * @return commit id of the current commit.
     */
    protected String getCurrentCommit() throws MojoFailureException {
        return getCurrentCommit("HEAD");
    }

    /**
     * Get the current commit ID on the given reference (branch name)
     *
     * @return commit id of the current commit.
     */
    protected String getCurrentCommit(String ref) throws MojoFailureException {
        try {
            return executeGitCommandReturn("rev-parse", ref);
        } catch (MojoFailureException exc) {
            throw exc;
        } catch (CommandLineException exc) {
            throw new MojoFailureException("Failed to get the current commit ID.", exc);
        }
    }

    protected boolean gitMergeInProcess() throws MojoFailureException, CommandLineException {
        return gitGetMergeHeadFileIfExists() != null;
    }

    protected File gitGetMergeHeadFileIfExists() throws MojoFailureException, CommandLineException {
        return gitGetMergeFileIfExists("MERGE_HEAD");
    }

    protected File gitGetMergeFileIfExists(String fileName) throws MojoFailureException, CommandLineException {
        final String gitDir = executeGitCommandReturn("rev-parse", "--git-dir").trim();
        File mergeHeadNameFile = FileUtils.getFile(gitDir, fileName);
        if (!mergeHeadNameFile.isAbsolute()) {
            String basedir = this.session.getRequest().getBaseDirectory();
            mergeHeadNameFile = new File(basedir, mergeHeadNameFile.getPath());
        }
        return mergeHeadNameFile.exists() ? mergeHeadNameFile : null;
    }

    protected String gitGetMergeHeadIfExists() throws MojoFailureException, CommandLineException {
        File mergeHeadNameFile = gitGetMergeHeadFileIfExists();
        if (mergeHeadNameFile == null) {
            return null;
        }
        try {
            return FileUtils.readFileToString(mergeHeadNameFile, "UTF-8").trim();
        } catch (IOException e) {
            throw new MojoFailureException("Failed to check for currently merging branch.", e);
        }
    }

    protected String gitGetBranchNameFromMergeHeadIfStartsWith(String mergeHeadName, String branchPrefix)
            throws CommandLineException, MojoFailureException {
        String featureBranch = null;
        if (!mergeHeadName.startsWith("refs/heads/")) {
            String barnchesStr = executeGitCommandReturn("branch", "--contains", mergeHeadName).trim();
            String[] barnches = barnchesStr.split("\r?\n");
            for (String branch : barnches) {
                if (branch.startsWith(branchPrefix)) {
                    featureBranch = branch;
                }
            }
        } else {
            String branch = mergeHeadName.substring("refs/heads/".length());
            if (branch.startsWith(branchPrefix)) {
                featureBranch = branch;
            }
        }
        return featureBranch;
    }

    /**
     * Checks whether a merge is in progress by checking MERGE_HEAD file and the
     * current branch is a feature branch.
     *
     * @return the name of the current (feature) branch or <code>null</code> if
     *         no merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeIntoFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        if (!gitMergeInProcess()) {
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
     * Checks whether a merge is in process by checking MERGE_HEAD file and that
     * the MERGE_HEAD points a feature branch.
     *
     * @return the name of the feature branch that is being merged into current
     *         branch or <code>null</code> if no merge is in process
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected String gitMergeFromFeatureBranchInProcess() throws MojoFailureException, CommandLineException {
        File mergeHeadNameFile = gitGetMergeHeadFileIfExists();
        if (mergeHeadNameFile == null) {
            return null;
        }
        String headName;
        try {
            headName = FileUtils.readFileToString(mergeHeadNameFile, "UTF-8").trim();
        } catch (IOException e) {
            throw new MojoFailureException("Failed to check for currently merging branch.", e);
        }
        String featureBranch = null;
        if (!headName.startsWith("refs/heads/")) {
            String barnchesStr = executeGitCommandReturn("branch", "--contains", headName).trim();
            String[] barnches = barnchesStr.split("\r?\n");
            for (String branch : barnches) {
                if (branch.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
                    featureBranch = branch;
                }
            }
        } else {
            String branch = headName.substring("refs/heads/".length());
            if (branch.startsWith(gitFlowConfig.getFeatureBranchPrefix())) {
                featureBranch = branch;
            }
        }
        if (featureBranch == null) {
            throw new MojoFailureException("Merging branch is not a feature branch: " + headName);
        }
        return featureBranch;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     *
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitRebaseContinue() throws MojoFailureException, CommandLineException {
        executeGitCommand("rebase", "--continue");
    }

    protected InteractiveRebaseResult gitInteractiveRebaseContinue() throws MojoFailureException, CommandLineException {
        Integer commitNumber = gitGetInteractiveRebaseCommitNumber();
        CommandResult commandResult = executeGitCommandExitCode("rebase", "--continue");
        String gitMessage = commandResult.getError();
        if (StringUtils.isBlank(gitMessage) && StringUtils.isNotBlank(commandResult.getOut())) {
            gitMessage = commandResult.getOut();
        }
        if (commandResult.getExitCode() != SUCCESS_EXIT_CODE) {
            Integer newCommitNumber = gitGetInteractiveRebaseCommitNumber();
            if (gitRebaseInProcess() && commitNumber != null && newCommitNumber != null
                    && commitNumber != newCommitNumber) {
                return new InteractiveRebaseResult(InteractiveRebaseStatus.CONFLICT, gitMessage);
            } else {
                if (gitHasUnmargedFiles()) {
                    return new InteractiveRebaseResult(InteractiveRebaseStatus.UNRESOLVED_CONFLICT, gitMessage);
                }
                throw new GitFlowFailureException(
                        "Continuation of interactive rebase failed.\nGit error message:\n" + gitMessage,
                        "Fix the problem described in git error message or consult a gitflow expert on how to fix this!");
            }
        }
        if (gitRebaseInProcess()) {
            return new InteractiveRebaseResult(InteractiveRebaseStatus.PAUSED, gitMessage);
        }
        return InteractiveRebaseResult.SUCCESS;
    }

    protected boolean gitHasUnmargedFiles() throws MojoFailureException, CommandLineException {
        String result = executeGitCommandReturn("diff", "--name-only", "--diff-filter=U");
        return StringUtils.isNotBlank(result);
    }

    private Integer gitGetInteractiveRebaseCommitNumber() throws MojoFailureException, CommandLineException {
        Integer commitNumber = null;
        File msgnumFile = gitGetRebaseFileIfExists("msgnum");
        if (msgnumFile != null) {
            try {
                String commitNumberStr = FileUtils.readFileToString(msgnumFile, "UTF-8").trim();
                commitNumber = Integer.parseInt(commitNumberStr);
            } catch (IOException e) {
                getLog().warn("Failed to check for current commit number in interactive rebase. Error on reading file '"
                        + msgnumFile.getPath() + "'.");
            } catch (NumberFormatException exc) {
                getLog().warn("Failed to check for current commit number in interactive rebase. "
                        + "Error on parsing file content. " + exc.getMessage());
            }
        }
        return commitNumber;
    }

    /**
     * Checks whether a rebase is in progress by looking at .git/rebase-apply.
     *
     * @return true if a branch with the passed name exists.
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitRebaseAbort() throws MojoFailureException, CommandLineException {
        executeGitCommand("rebase", "--abort");
    }

    /**
     * Start a rebase --interactive in the current branch on the given commit.
     *
     * @param commitId
     *            the commit to start the interactive rebase. Must be a
     *            predecessor of the current branch tip
     * @return rebase result status
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected InteractiveRebaseStatus gitRebaseInteractive(String commitId)
            throws MojoFailureException, CommandLineException {
        getLog().info("Rebasing interactively on " + commitId);
        initExecutables();
        cmdGit.clearArgs();
        cmdGit.addArguments(new String[] { "rebase", "--interactive", commitId });

        ProcessBuilder processBuilder = new ProcessBuilder(cmdGit.getShellCommandline())
                .directory(cmdGit.getWorkingDirectory());
        if (!isExternalGitEditorUsedconfiguredConfiguredByUserProperties()) {
            processBuilder.inheritIO();
        }

        processBuilder.environment().clear();
        for (String envstring : cmdGit.getEnvironmentVariables()) {
            // Silently discard any trailing junk.
            if (envstring.indexOf((int) '\u0000') != -1)
                envstring = envstring.replaceFirst("\u0000.*", "");

            int eqlsign = envstring.indexOf('=', 1);
            // Silently ignore envstrings lacking the required `='.
            if (eqlsign != -1) {
                processBuilder.environment().put(envstring.substring(0, eqlsign), envstring.substring(eqlsign + 1));
            }
        }
        try {
            Process process = processBuilder.start();
            if (process.waitFor() != SUCCESS_EXIT_CODE) {
                if (gitRebaseInProcess()) {
                    return InteractiveRebaseStatus.CONFLICT;
                } else {
                    throw new GitFlowFailureException("Interactive rebase failed.",
                            "Check the output above for the reason. "
                                    + "Fix the problem or consult a gitflow expert on how to fix this!");
                }
            }
            if (gitRebaseInProcess()) {
                return InteractiveRebaseStatus.PAUSED;
            }

            return InteractiveRebaseStatus.SUCCESS;
        } catch (IOException | InterruptedException ex) {
            throw new MojoFailureException("Starting rebase interactive failed", ex);
        }
    }

    private boolean isExternalGitEditorUsedconfiguredConfiguredByUserProperties() {
        return Boolean
                .parseBoolean(session.getUserProperties().getProperty(USER_PROPERTY_KEY_EXTERNAL_GIT_EDITOR_USED));
    }

    /**
     * Checkout the branch configuration as a separate worktree. Needs git 2.5+
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void gitBranchConfigWorktree(final String branchName, final String configBranchName,
            final String configBranchDir, final String propertyName, final String propertyValue)
            throws MojoFailureException, CommandLineException {
        getLog().info("Creating new worktree for branch configuration.");

        // clean worktree target directory
        String basedir = this.session.getRequest().getBaseDirectory();
        File branchConfigWorktree = new File(basedir, configBranchDir);
        if (branchConfigWorktree.exists()) {
            try {
                FileUtils.deleteDirectory(branchConfigWorktree);
            } catch (IOException ex) {
                throw new MojoFailureException("Failed to cleanup worktree '" + branchConfigWorktree + "'", ex);
            }
        }

        // get rid of stale worktrees
        executeGitCommand("worktree", "prune");

        initExecutables();
        Commandline worktreeCmd = new ShellCommandLine();
        worktreeCmd.setExecutable(gitExecutable);
        worktreeCmd.setWorkingDirectory(branchConfigWorktree);

        // fetch remote reference first to be up-to-date
        executeGitCommand("fetch", "--quiet", gitFlowConfig.getOrigin(), configBranchName);

        if (hasRemoteBranch(configBranchName)) {
            // configuration branch already exists, just create the worktree
            executeGitCommand("worktree", "add", "-B", configBranchName, configBranchDir,
                    gitFlowConfig.getOrigin() + "/" + configBranchName);
        } else {
            // need to create the branch correctly
            executeGitCommand("worktree", "add", "--no-checkout", "--detach", configBranchDir);

            executeCommand(worktreeCmd, true, "checkout", "--orphan", configBranchName);
            executeCommand(worktreeCmd, true, "reset", "--hard");
            executeCommand(worktreeCmd, true, "commit", "--allow-empty", "-m", commitMessages.getBranchConfigMessage());

            executeCommand(worktreeCmd, true, "push", "--set-upstream", gitFlowConfig.getOrigin(), configBranchName);
        }

        // now we're ready to actually set a property.
        File branchPropertyFile = new File(branchConfigWorktree, branchName);

        Parameters params = new Parameters();
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class).configure(params.properties().setThrowExceptionOnMissing(false));
        if (branchPropertyFile.exists()) {
            // only set if existing at this point
            builder.getFileHandler().setFile(branchPropertyFile);
        }
        try {
            Configuration config = builder.getConfiguration();
            if (StringUtils.isEmpty(propertyValue)) {
                config.clearProperty(propertyName);
            } else {
                config.setProperty(propertyName, propertyValue);
            }
            builder.getFileHandler().setFile(branchPropertyFile);
            builder.save();
        } catch (ConfigurationException cex) {
            throw new MojoFailureException("Failed to change branch property '" + propertyName + "'", cex);
        }

        // now commit the change and push it
        executeCommand(worktreeCmd, true, "add", branchName);
        CommandResult result = executeCommand(worktreeCmd, false, "commit", "-m",
                commitMessages.getBranchConfigMessage());
        if (result.exitCode == SUCCESS_EXIT_CODE) {
            // push the change
            executeCommand(worktreeCmd, true, "push");
        } else {
            getLog().info("No changes detected.");
        }

        // clean up
        try {
            FileUtils.deleteDirectory(branchConfigWorktree);
        } catch (IOException ex) {
            getLog().debug("Failed to cleanup worktree", ex);
        }

        // get rid of stale worktrees
        executeGitCommand("worktree", "prune");
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
    protected void mvnSetVersions(final String version) throws MojoFailureException, CommandLineException {
        mvnSetVersions(version, null);
    }

    /**
     * Executes 'set' goal of versions-maven-plugin or 'set-version' of
     * tycho-versions-plugin in case it is tycho build.
     *
     * @param version
     *            New version to set.
     * @param promptPrefix
     *            Specify a prompt prefix. A value <code>!= null</code> triggers
     *            processing of {@link #additionalVersionCommands}
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnSetVersions(final String version, String promptPrefix)
            throws MojoFailureException, CommandLineException {

        boolean processAdditionalCommands = (promptPrefix != null);
        if (processAdditionalCommands && additionalVersionCommands != null) {
            StringSearchInterpolator interpolator = new StringSearchInterpolator("@{", "}");
            interpolator.addValueSource(new PropertiesBasedValueSource(getProject().getProperties()));
            Properties properties = new Properties();
            properties.setProperty("version", version);
            interpolator.addValueSource(new PropertiesBasedValueSource(properties));

            // process additional commands/parameters
            if (promptPrefix != null) {
                for (GitFlowParameter parameter : additionalVersionCommands) {
                    if (!parameter.isEnabled()) {
                        continue;
                    }
                    if (!parameter.isEnabledByPrompt() && parameter.getProperty() != null
                            && session.getRequest().getUserProperties().getProperty(parameter.getProperty()) != null) {
                        parameter.setValue(
                                session.getRequest().getUserProperties().getProperty(parameter.getProperty()));
                    } else if (settings.isInteractiveMode()) {
                        if (parameter.getPrompt() != null) {
                            try {
                                String value = null;

                                String prompt = interpolator.interpolate(parameter.getPrompt());
                                if (promptPrefix != null) {
                                    prompt = promptPrefix + prompt;
                                }
                                String defaultValue = parameter.getDefaultValue() != null
                                        ? interpolator.interpolate(parameter.getDefaultValue()) : null;

                                while (value == null) {
                                    if (defaultValue != null) {
                                        value = prompter.prompt(prompt, defaultValue);
                                    } else {
                                        value = prompter.prompt(prompt);
                                    }
                                }

                                parameter.setValue(value);
                            } catch (InterpolationException e) {
                                throw new MojoFailureException("Failed to interpolate values", e);
                            } catch (PrompterException e) {
                                throw new MojoFailureException("Failed to prompt for parameter", e);
                            }
                        }
                    } else {
                        try {
                            String defaultValue = parameter.getDefaultValue() != null
                                    ? interpolator.interpolate(parameter.getDefaultValue()) : null;
                            parameter.setValue(defaultValue);
                        } catch (InterpolationException e) {
                            throw new MojoFailureException("Failed to interpolate values", e);
                        }
                    }
                    getLog().info("Parameter set to '" + parameter.getValue() + "'");
                }
            }
        }

        getLog().info("Updating version(s) to '" + version + "'.");

        if (tychoBuild) {
            executeMvnCommand(false, TYCHO_VERSIONS_PLUGIN_SET_GOAL, "-DnewVersion=" + version, "-Dtycho.mode=maven");
        } else {
            executeMvnCommand(false, VERSIONS_MAVEN_PLUGIN_SET_GOAL, "-DnewVersion=" + version,
                    "-DgenerateBackupPoms=false");
        }
        for (String command : getCommandsAfterVersion(processAdditionalCommands)) {
            try {
                executeMvnCommand(false,
                        CommandLineUtils.translateCommandline(command.replaceAll("\\@\\{version\\}", version)));
            } catch (Exception e) {
                throw new MojoFailureException("Failed to execute " + command, e);
            }
        }
    }

    protected List<String> getAdditionalVersionCommands() throws MojoFailureException {
        if (additionalVersionCommands == null || additionalVersionCommands.length == 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (GitFlowParameter parameter : additionalVersionCommands) {
            if (!parameter.isEnabled()) {
                continue;
            }
            if (parameter.isEnabledByPrompt() && !"true".equals(parameter.getValue())
                    && !"yes".equals(parameter.getValue())) {
                continue;
            }

            StringSearchInterpolator interpolator = new StringSearchInterpolator("@{", "}");
            interpolator.addValueSource(new PropertiesBasedValueSource(getProject().getProperties()));
            interpolator.addValueSource(new SingleResponseValueSource("value", parameter.getValue()));

            try {
                result.add(interpolator.interpolate(parameter.getCommand()));
            } catch (InterpolationException e) {
                throw new MojoFailureException("Failed to interpolate command", e);
            }
        }
        return result;
    }

    /**
     * Get the command specific additional commands to execute when a version
     * changes.
     *
     * @return a new unmodifiable list with the command.
     */
    protected List<String> getCommandsAfterVersion(boolean processAdditionalCommands) throws MojoFailureException {
        List<String> result = new ArrayList<String>();
        if (!StringUtils.isEmpty(commandsAfterVersion)) {
            result.add(commandsAfterVersion);
        }
        if (processAdditionalCommands) {
            result.addAll(getAdditionalVersionCommands());
        }
        return result;
    }

    /**
     * Executes mvn clean test.
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnCleanTest() throws MojoFailureException, CommandLineException {
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
    protected void mvnCleanInstall() throws MojoFailureException, CommandLineException {
        getLog().info("Cleaning and installing the project.");

        executeMvnCommand(printInstallOutput, "clean", "install");
    }

    /**
     * Executes mvn [goals].
     *
     * @throws MojoFailureException
     * @throws CommandLineException
     */
    protected void mvnGoals(String goals) throws MojoFailureException, CommandLineException {
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
    private String executeGitCommandReturn(final String... args) throws CommandLineException, MojoFailureException {
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
    private void executeGitCommand(final String... args) throws CommandLineException, MojoFailureException {
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
        String[] effectiveArgs = addArgs(args, "-f", session.getRequest().getPom().getAbsolutePath());
        if (session.getRequest().getUserSettingsFile() != null) {
            effectiveArgs = addArgs(effectiveArgs, "-s", session.getRequest().getUserSettingsFile().getAbsolutePath());
        }
        if (session.getRequest().isOffline()) {
            effectiveArgs = addArgs(effectiveArgs, "-o");
        }
        if (session.getRequest().isNoSnapshotUpdates()) {
            effectiveArgs = addArgs(effectiveArgs, "-nsu");
        }
        StringBuilder profileActivation = new StringBuilder();
        if (session.getRequest().getActiveProfiles() != null) {
            for (String profile : session.getRequest().getActiveProfiles()) {
                if (profileActivation.length() > 0) {
                    profileActivation.append(",");
                }
                profileActivation.append(profile);
            }
        }
        if (session.getRequest().getInactiveProfiles() != null) {
            for (String profile : session.getRequest().getInactiveProfiles()) {
                if (profileActivation.length() > 0) {
                    profileActivation.append(",");
                }
                profileActivation.append("-").append(profile);
            }
        }
        if (profileActivation.length() > 0) {
            effectiveArgs = addArgs(effectiveArgs, "-P", profileActivation.toString());
        }
        if (session.getRequest().isShowErrors()) {
            effectiveArgs = addArgs(effectiveArgs, "-e");
        }
        if (session.getRequest().getLoggingLevel() == MavenExecutionRequest.LOGGING_LEVEL_DEBUG) {
            effectiveArgs = addArgs(effectiveArgs, "-X");
        }
        if (copyProperties != null) {
            for (String userProperty : copyProperties) {
                if (session.getRequest().getUserProperties().containsKey(userProperty)) {
                    String[] newEffectiveArgs = new String[effectiveArgs.length + 1];
                    System.arraycopy(effectiveArgs, 0, newEffectiveArgs, 0, effectiveArgs.length);
                    newEffectiveArgs[effectiveArgs.length] = "-D" + userProperty + "=" + CommandLineUtils
                            .quote((String) session.getRequest().getUserProperties().get(userProperty));
                    effectiveArgs = newEffectiveArgs;
                }
            }
        }
        Commandline cmd = getCmdMvnConfiguredByUserProperties();
        if (cmd != null) {
            effectiveArgs = mergeCmdMvnArgsConfiguredByUserProperties(effectiveArgs);
        } else {
            cmd = cmdMvn;
        }
        if (copyOutput) {
            executeCommandCopyOut(cmd, true, effectiveArgs);
        } else {
            executeCommand(cmd, true, effectiveArgs);
        }
    }

    private Commandline getCmdMvnConfiguredByUserProperties() {
        String cmdMvnExecutable = session.getUserProperties().getProperty(USER_PROPERTY_KEY_CMD_MVN_EXECUTABLE);
        if (cmdMvnExecutable != null) {
            ShellCommandLine cmd = new ShellCommandLine();
            String basedir = session.getRequest().getBaseDirectory();
            cmd.setExecutable(cmdMvnExecutable);
            cmd.setWorkingDirectory(basedir);
            return cmd;
        }
        return null;
    }

    private String[] mergeCmdMvnArgsConfiguredByUserProperties(String[] effectiveArgs) {
        String[] cmdMvnArgs = (String[]) session.getUserProperties().get(USER_PROPERTY_KEY_CMD_MVN_ARGS_PREPEND);
        String[] cmdMvnArgsAfter = (String[]) session.getUserProperties().get(USER_PROPERTY_KEY_CMD_MVN_ARGS_APPEND);
        String[] newEffectiveArgs = effectiveArgs;
        if (cmdMvnArgs != null && cmdMvnArgs.length > 0) {
            String[] tmpEffectiveArgs = new String[cmdMvnArgs.length + newEffectiveArgs.length];
            System.arraycopy(cmdMvnArgs, 0, tmpEffectiveArgs, 0, cmdMvnArgs.length);
            System.arraycopy(newEffectiveArgs, 0, tmpEffectiveArgs, cmdMvnArgs.length, newEffectiveArgs.length);
            newEffectiveArgs = tmpEffectiveArgs;
        }
        if (cmdMvnArgsAfter != null && cmdMvnArgsAfter.length > 0) {
            String[] tmpEffectiveArgs = new String[newEffectiveArgs.length + cmdMvnArgsAfter.length];
            System.arraycopy(newEffectiveArgs, 0, tmpEffectiveArgs, 0, newEffectiveArgs.length);
            System.arraycopy(cmdMvnArgsAfter, 0, tmpEffectiveArgs, newEffectiveArgs.length, cmdMvnArgsAfter.length);
            newEffectiveArgs = tmpEffectiveArgs;
        }
        return newEffectiveArgs;
    }

    /**
     * Add (prepend) additional arguments to given arguments
     */
    private String[] addArgs(String[] args, String... additionalArgs) {
        String[] result = new String[args.length + additionalArgs.length];
        System.arraycopy(additionalArgs, 0, result, 0, additionalArgs.length);
        System.arraycopy(args, 0, result, additionalArgs.length, args.length);
        return result;
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
    protected String substituteInMessage(final String message, final String featureBranchName)
            throws MojoFailureException {
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
                    if (m.groupCount() == 0) {
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
     *
     * @{someName} while the key in the map shall be just someName. If the map
     *             does not contain a found placeholder, it will also look in
     *             the project properties.
     * @param aMessage
     * @param someReplacements
     * @return
     * @throws MojoFailureException
     */
    protected String substituteStrings(final String aMessage, final Map<String, String> someReplacements)
            throws MojoFailureException {
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
     *
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
    private CommandResult executeCommandCopyOut(final Commandline cmd, final boolean failOnError, final String... args)
            throws CommandLineException, MojoFailureException {
        // initialize executables
        initExecutables();

        if (getLog().isDebugEnabled()) {
            getLog().debug(cmd.getExecutable() + " " + StringUtils.join(args, " "));
        }

        cmd.clearArgs();
        cmd.addArguments(args);

        final StringBufferStreamConsumer out = new StringBufferStreamConsumer(verbose);

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
     * Check whether commandline git is available at all.
     */
    protected boolean isGitAvailable() throws MojoFailureException, CommandLineException {
        CommandResult gitResult = executeGitCommandExitCode("--version");
        return (gitResult.exitCode == 0);
    }

    /**
     * Check whether we can write to the repository. We're doing this by
     * deleting a nonexisting remote tag
     */
    protected void checkGitWriteable() throws MojoFailureException, CommandLineException {
        String tempBranchName = "temp/" + System.getProperty("user.name").toLowerCase().replace(' ', '-')
                + "_access_check";

        // clear any stale branch
        try {
            executeGitCommand("push", gitFlowConfig.getOrigin(), "--delete", tempBranchName);
        } catch (MojoFailureException ex) {
            // we ignore any error here, as it will fail if it has been properly
            // cleared up before
        }

        // now create branch and remove it
        executeGitCommand("push", gitFlowConfig.getOrigin(), "HEAD:" + tempBranchName);
        executeGitCommand("push", gitFlowConfig.getOrigin(), "--delete", tempBranchName);
    }

    /**
     * Create a valid Tycho (OSGi) version from the passed version: It must have
     * only 4 version components and the first 3 must be numeric.
     */
    protected String makeValidTychoVersion(String version) throws VersionParseException {
        String result = version;
        DefaultVersionInfo versionInfo = new DefaultVersionInfo(version);
        if (versionInfo.getDigits().size() <= 4) {
            result = StringUtils.join(versionInfo.getDigits().iterator(), ".");
        } else {
            // version from first 3 components and join remaining in qualifier
            result = StringUtils.join(versionInfo.getDigits().subList(0, 3).iterator(), ".");
            // add remaining to qualifier
            result += "-" + StringUtils
                    .join(versionInfo.getDigits().subList(4, versionInfo.getDigits().size() - 1).iterator(), "_");
        }
        return result;
    }

    protected ExtendedPrompter getPrompter() {
        if (extendedPrompter == null) {
            extendedPrompter = new ExtendedPrompter(prompter, settings.isInteractiveMode());
        }
        return extendedPrompter;
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
    private CommandResult executeCommand(final Commandline cmd, final boolean failOnError, final String... args)
            throws CommandLineException, MojoFailureException {
        // initialize executables
        initExecutables();

        if (getLog().isDebugEnabled()) {
            getLog().debug(cmd.getExecutable() + " " + StringUtils.join(args, " "));
        }

        cmd.clearArgs();
        cmd.addArguments(args);

        final StringBufferStreamConsumer out = new StringBufferStreamConsumer(verbose);

        final CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

        // execute
        final int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

        String errorStr = err.getOutput();
        String outStr = out.getOutput();

        if (failOnError && exitCode != SUCCESS_EXIT_CODE) {
            // not all commands print errors to error stream
            if (StringUtils.isBlank(errorStr) && StringUtils.isNotBlank(outStr)) {
                errorStr = outStr;
            } else {
                getLog().debug("Command output: " + outStr);
            }

            throw new MojoFailureException(errorStr);
        }

        return new CommandResult(exitCode, StringUtils.trim(outStr), errorStr);
    }

    /**
     * Extracts the feature issue number from feature name using feature name
     * pattern. E.g. extracts issue number "GBLD-42" from feature name
     * "GBLD-42-someDescription" if default feature name pattern is used.
     * Returns feature name if issue number can't be extracted.
     *
     * @param aFeatureName
     *            the feature name
     * @return the extracted feature issue number or feature name if issue
     *         number can't be extracted
     */
    protected String extractIssueNumberFromFeatureName(String aFeatureName) {
        String issueNumber = aFeatureName;
        if (featureNamePattern != null) {
            // extract the issue number only
            Matcher m = Pattern.compile(featureNamePattern).matcher(aFeatureName);
            if (m.matches()) {
                if (m.groupCount() == 0) {
                    getLog().warn("Feature branch conforms to <featureNamePattern>, but ther is no matching"
                            + " group to extract the issue number.");
                } else {
                    issueNumber = m.group(1);
                }
            } else {
                getLog().warn("Feature branch does not conform to <featureNamePattern> specified, cannot "
                        + "extract issue number.");
            }
        }
        return issueNumber;
    }

    protected void gitResetHard() throws MojoFailureException, CommandLineException {
        executeGitCommand("reset", "--hard", "HEAD");
    }

    private static class CommandResult {

        private final int exitCode;

        private final String out;

        private final String error;

        private CommandResult(final int exitCode, final String out, final String error) {
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
