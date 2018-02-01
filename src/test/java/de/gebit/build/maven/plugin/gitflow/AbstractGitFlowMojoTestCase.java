//
// AbstractGitFlowMojoTestCase.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.Maven;
import org.apache.maven.classrealm.ClassRealmManagerDelegate;
import org.apache.maven.classrealm.ClassRealmRequest;
import org.apache.maven.cli.ExtCliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelParseException;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import de.gebit.build.maven.plugin.gitflow.jgit.GitDummyEditor;
import de.gebit.build.maven.plugin.gitflow.jgit.GitRebaseTodo;
import de.gebit.build.maven.plugin.gitflow.jgit.GitRebaseTodo.GitRebaseTodoEntry;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * The abstract test case for all GitFlowMojo test cases.
 *
 * @author VMedvid
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractGitFlowMojoTestCase {

    protected static final String LS = System.getProperty("line.separator");

    protected static final String MASTER_BRANCH = "master";

    protected static final String COMMIT_MESSAGE_FOR_TESTFILE = "Unit test dummy file commit";

    private static final String COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP = "Unit test set-up initial commit";

    private static final String REFS_HEADS_PATH = "refs/heads/";

    private static final String PLEXUS_CORE_REALM_ID = "plexus.core";

    private static final String GITFLOW_PLUGIN_VERSION_PROPERTY = "version.gitflow-maven-plugin";

    private static final String GOAL_PREFIX = "flow";

    private static final String GIT_BASEDIR = "target/git";

    private static final String GIT_BASEDIR_ORIGIN_SUFFIX = "origin.git";

    private static final String GIT_BASEDIR_WORKING_SUFFIX = "working";

    private PlexusContainer container;

    private ControllablePrompter prompter;

    private ClassWorld classWorld;

    @Mock
    protected Prompter promptControllerMock;

    @Before
    public void setUpAbstractGitFlowMojoTestCase() throws Exception {
        startPlexusContainer();
        cleanupGitBasedir();
    }

    @After
    public void tearDownAbstractGitFlowMojoTestCase() throws Exception {
        stopPlexusContainer();
    }

    private void startPlexusContainer() throws Exception {
        classWorld = new ClassWorld(PLEXUS_CORE_REALM_ID, Thread.currentThread().getContextClassLoader());
        ContainerConfiguration cc = new DefaultContainerConfiguration().setClassWorld(classWorld)
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true).setName("maven");
        prompter = new ControllablePrompter();
        Module module = new AbstractModule() {

            @Override
            protected void configure() {
                bind(Prompter.class).toInstance(prompter);
                bind(ClassRealmManagerDelegate.class).toInstance(new ClassRealmManagerDelegate() {

                    @Override
                    public void setupRealm(ClassRealm classRealm, ClassRealmRequest request) {
                        if (classRealm.getId().contains("gitflow-maven-plugin")) {
                            // remove all artifacts, load all from parent (=app)
                            // loader
                            request.getConstituents().clear();
                        }
                    }
                });
            }
        };
        container = new DefaultPlexusContainer(cc, module);
    }

    private void stopPlexusContainer() throws Exception {
        if (container != null) {
            container.dispose();
        }
        if (classWorld != null) {
            for (ClassRealm realm : new ArrayList<>(classWorld.getRealms())) {
                classWorld.disposeRealm(realm.getId());
            }
        }
    }

    private void cleanupGitBasedir() throws IOException {
        File gitBasedir = new File(GIT_BASEDIR);
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
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal) throws Exception {
        executeMojo(basedir, goal, null, null);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Properties properties) throws Exception {
        executeMojo(basedir, goal, properties, null);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Prompter promptController) throws Exception {
        executeMojo(basedir, goal, null, promptController);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Properties properties, Prompter promptController)
            throws Exception {
        String pluginVersion = readPom(new File(".")).getVersion();
        String fullGoal = GOAL_PREFIX + ":" + goal;
        MavenExecutionRequest request = createMavenExecutionRequest(basedir, fullGoal, properties, pluginVersion);
        request.setInteractiveMode(promptController != null);
        MavenExecutionResult result;
        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            prompter.setController(promptController);
            Maven maven = container.lookup(Maven.class);
            result = maven.execute(request);
        } finally {
            ClassLoader newClassLoader = Thread.currentThread().getContextClassLoader();
            if (newClassLoader != originClassLoader) {
                if (newClassLoader instanceof Closeable) {
                    try {
                        ((Closeable) newClassLoader).close();
                    } catch (IOException exc) {
                        exc.printStackTrace();
                    }
                }
                Thread.currentThread().setContextClassLoader(originClassLoader);
            }
            prompter.setController(null);
        }
        int errorCnt = 0;
        for (Throwable exc : result.getExceptions()) {
            System.err.println("Error " + (++errorCnt) + ":");
            exc.printStackTrace();
        }
        assertFalse("maven command executed with errors", result.hasExceptions());
    }

    private MavenExecutionRequest createMavenExecutionRequest(File basedir, String fullGoal, Properties properties,
            final String pluginVersion) throws Exception {
        Properties userProperties = properties != null ? properties : new Properties();
        File pom = new File(basedir, "pom.xml");
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        MavenExecutionRequestPopulator executionRequestPopulator = container
                .lookup(MavenExecutionRequestPopulator.class);
        ExtCliRequest cliRequest = new ExtCliRequest(new String[] { fullGoal });
        cliRequest.setWorkingDirector(basedir.getAbsolutePath());
        addSpecialProperties(userProperties, pluginVersion, basedir);
        cliRequest.setUserProperties(userProperties);
        Map<String, ConfigurationProcessor> configurationProcessors = container.lookupMap(ConfigurationProcessor.class);
        configurationProcessors.get(SettingsXmlConfigurationProcessor.HINT).process(cliRequest);
        request = cliRequest.getRequest();
        WorkspaceReader workspaceReader = new WorkspaceReader() {

            WorkspaceRepository workspaceRepo = new WorkspaceRepository("ide", getClass());

            @Override
            public File findArtifact(Artifact aArtifact) {
                if (aArtifact.getArtifactId().equals("gitflow-maven-plugin")) {
                    if (aArtifact.getExtension().equals("pom")) {
                        return new File("pom.xml");
                    } else {
                        return getWorkspaceClasspath();
                    }
                }
                return null;
            }

            @Override
            public List<String> findVersions(Artifact aArtifact) {
                ArrayList<String> versions = new ArrayList<String>();
                if (aArtifact.getArtifactId().equals("gitflow-maven-plugin")) {
                    versions.add(pluginVersion);
                }
                return versions;
            }

            @Override
            public WorkspaceRepository getRepository() {
                return workspaceRepo;
            }
        };
        request.setWorkspaceReader(workspaceReader);
        request = executionRequestPopulator.populateDefaults(request);
        request.setBaseDirectory(basedir);
        request.setGoals(Arrays.asList(fullGoal));
        request.setPom(pom);
        request.setUserProperties(userProperties);
        return request;
    }

    private void addSpecialProperties(Properties userProperties, final String pluginVersion, File basedir) {
        File javaExecutable = getJavaExecutable();
        userProperties.setProperty(AbstractGitFlowMojo.USER_PROPERTY_KEY_CMD_MVN_EXECUTABLE,
                javaExecutable.getAbsolutePath());
        String classPath = System.getProperty("java.class.path");
        String mainClass = ExtMavenCli.class.getName();
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add("-D" + ExtMavenCli.PROPERTY_KEY_PLUGIN_BASEDIR + "=" + new File("").getAbsolutePath());
        javaArgs.add("-D" + ExtMavenCli.PROPERTY_KEY_PLUGIN_VERSION + "=" + pluginVersion);
        javaArgs.add("-D" + MavenCli.MULTIMODULE_PROJECT_DIRECTORY + "=" + basedir.getAbsolutePath());
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome == null) {
            mavenHome = System.getenv("MAVEN_HOME");
        }
        if (mavenHome != null) {
            javaArgs.add("-Dmaven.home=" + mavenHome);
        }
        javaArgs.add("-cp");
        javaArgs.add(classPath);
        javaArgs.add(mainClass);
        userProperties.put(AbstractGitFlowMojo.USER_PROPERTY_KEY_CMD_MVN_ARGS_PREPEND,
                javaArgs.toArray(new String[javaArgs.size()]));
        List<String> mvnArgs = new ArrayList<>();
        mvnArgs.add("-D" + GITFLOW_PLUGIN_VERSION_PROPERTY + "=" + pluginVersion);
        userProperties.put(AbstractGitFlowMojo.USER_PROPERTY_KEY_CMD_MVN_ARGS_APPEND,
                mvnArgs.toArray(new String[mvnArgs.size()]));
        userProperties.put(GITFLOW_PLUGIN_VERSION_PROPERTY, pluginVersion);
    }

    /**
     * @param repositorySet
     * @throws PrompterException
     * @throws Exception
     */
    protected void executeFeatureStart(RepositorySet repositorySet, String featureNumber) throws Exception {
        when(promptControllerMock.prompt("What is a name of feature branch? feature/")).thenReturn(featureNumber);
        executeMojo(repositorySet.getWorkingBasedir(), "feature-start", promptControllerMock);
        reset(promptControllerMock);
    }

    private File getJavaExecutable() {
        return new File(new File(new File(System.getProperty("java.home")), "bin"), "java");
    }

    /**
     * Reads and parses a pom.xml file from passed base directory.
     *
     * @param basedir
     *            the base directory where the pom.xml file is located
     * @return the parsed pom model
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected Model readPom(File basedir) throws ComponentLookupException, ModelParseException, IOException {
        ModelProcessor modelProcessor = container.lookup(ModelProcessor.class);
        return modelProcessor.read(new File(basedir, "pom.xml"), null);
    }

    /**
     * Copies files from the passed sourceBasedir to the target directory.
     * Creates an origin git repository there. Commits all files to it. Clones
     * the origin git repository to a working git repository.
     *
     * @param sourceBasedir
     *            the source directory of the project that will be used in
     *            origin and working repositories
     * @return a repository set that contains references to origin and working
     *         repositories
     * @throws IOException
     *             if the source directory can't be copied to the target
     *             directory
     * @throws GitAPIException
     *             if an error uccurs while executiong if git commands
     */
    protected RepositorySet prepareGitRepo(File sourceBasedir) throws IOException, GitAPIException {
        String basedirName = sourceBasedir.getName();
        File repoBasedir = new File(GIT_BASEDIR, basedirName);
        File originRepoBasedir = new File(repoBasedir, GIT_BASEDIR_ORIGIN_SUFFIX);
        File workingRepoBasedir = new File(repoBasedir, GIT_BASEDIR_WORKING_SUFFIX);
        Git originGit = Git.init().setDirectory(originRepoBasedir).setBare(true).call();
        Git workingGit = null;
        try {
            workingGit = Git.cloneRepository().setURI(originGit.getRepository().getDirectory().getAbsolutePath())
                    .setDirectory(workingRepoBasedir).call();
            setGitDummyEditorToConfig(workingGit);
            FileUtils.copyDirectory(sourceBasedir, workingRepoBasedir);
            workingGit.add().addFilepattern(".").call();
            workingGit.commit().setMessage(COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP).call();
            workingGit.push().call();
            return new RepositorySet(originGit, workingGit);
        } catch (GitAPIException | IOException tempExc) {
            originGit.close();
            if (workingGit != null) {
                workingGit.close();
            }
            throw tempExc;
        }
    }

    protected Status gitStatus(RepositorySet aRepositorySet) throws GitAPIException {
        return aRepositorySet.getWorkingRepoGit().status().call();
    }

    /**
     * @param repositorySet
     * @param workingRepoGit
     */
    protected void gitCreateAndCommitTestfile(RepositorySet repositorySet) throws Exception {
        File testFile = new File(repositorySet.getWorkingBasedir(), "testfile.txt");
        FileUtils.write(testFile, "dummy content", "UTF-8");
        repositorySet.getWorkingRepoGit().add().addFilepattern(".").call();
        repositorySet.getWorkingRepoGit().commit().setMessage(COMMIT_MESSAGE_FOR_TESTFILE).call();
    }

    protected String gitCurrentBranch(RepositorySet aRepositorySet) throws IOException {
        return aRepositorySet.getWorkingRepoGit().getRepository().getBranch();
    }

    protected List<String> gitLocalBranches(RepositorySet aRepositorySet) throws GitAPIException {
        return gitBranches(aRepositorySet.getWorkingRepoGit());
    }

    protected List<String> gitRemoteBranches(RepositorySet aRepositorySet) throws GitAPIException {
        return gitBranches(aRepositorySet.getOriginRepoGit());
    }

    private List<String> gitBranches(Git git) throws GitAPIException {
        List<String> branches = new ArrayList<String>();
        List<Ref> branchRefs = git.branchList().call();
        for (Ref branchRef : branchRefs) {
            branches.add(gitBranchNameByRef(branchRef));
        }
        return branches;
    }

    protected String gitBranchNameByRef(Ref aBranchRef) {
        return StringUtils.substringAfter(aBranchRef.getName(), REFS_HEADS_PATH);
    }

    protected void assertLocalBranches(RepositorySet aRepositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = gitLocalBranches(aRepositorySet);
        assertBranches(expectedBranches, branches, "working");
    }

    protected void assertRemoteBranches(RepositorySet aRepositorySet, String... expectedBranches)
            throws GitAPIException {
        List<String> branches = gitRemoteBranches(aRepositorySet);
        assertBranches(expectedBranches, branches, "origin");
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

    protected void assertHasBranch(String branchName, List<Ref> branches) {
        assertHasBranch(null, branchName, branches);
    }

    protected void assertHasBranch(String msgPrefix, String branchName, List<Ref> branches) {
        for (Ref branch : branches) {
            if (branch.getName().equals(branchName)) {
                return;
            }
        }
        fail((msgPrefix != null ? msgPrefix + " " : "") + "Branch '" + branchName + "' is not in the list of branches");
    }

    protected void assertCommitsInLocalBranch(RepositorySet repositorySet, String branch,
            String... expectedCommitMessages) throws GitAPIException, IOException {
        List<String> commitMessages = gitCommitMessagesInBranch(repositorySet.getWorkingRepoGit(), branch);
        assertCommitMessages(expectedCommitMessages, commitMessages, branch, "origin");
    }

    protected void assertCommitsInRemoteBranch(RepositorySet repositorySet, String branch,
            String... expectedCommitMessages) throws GitAPIException, IOException {
        List<String> commitMessages = gitCommitMessagesInBranch(repositorySet.getOriginRepoGit(), branch);
        assertCommitMessages(expectedCommitMessages, commitMessages, branch, "origin");
    }

    private List<String> gitCommitMessagesInBranch(Git git, String branch) throws GitAPIException, IOException {
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

    protected void assertLocalAndRemoteBranchesReferenceSameCommit(RepositorySet repositorySet, String localBranch,
            String remoteBranch) throws IOException {
        ObjectId localBranchObjectId = gitObjectIdOfBranch(repositorySet.getWorkingRepoGit(), localBranch);
        ObjectId remoteBranchObjectId = gitObjectIdOfBranch(repositorySet.getOriginRepoGit(), remoteBranch);
        assertEquals("remote branch reference different commit then local branch", localBranchObjectId.getName(),
                remoteBranchObjectId.getName());
    }

    protected List<String> readCommitMessages(Git aGit, String... commitMessagesToBeExcluded)
            throws NoHeadException, GitAPIException {
        List<String> commitMessages = new ArrayList<String>();
        Iterable<RevCommit> commitsIterator = aGit.log()
                .setRevFilter(createUnitTestRevFilter(commitMessagesToBeExcluded)).call();
        for (RevCommit tempRevCommit : commitsIterator) {
            commitMessages.add(tempRevCommit.getFullMessage().trim());
        }
        return commitMessages;
    }

    private List<RevCommit> readCommits(Git git, String branch, String... commitMessagesToBeExcluded)
            throws GitAPIException, IOException {
        List<RevCommit> commits = new ArrayList<RevCommit>();
        ObjectId branchObjectId = gitObjectIdOfBranch(git, branch);
        Iterable<RevCommit> commitsIterator = git.log().add(branchObjectId)
                .setRevFilter(createUnitTestRevFilter(commitMessagesToBeExcluded)).call();
        for (RevCommit tempRevCommit : commitsIterator) {
            commits.add(tempRevCommit);
        }
        return commits;
    }

    private ObjectId gitObjectIdOfBranch(Git git, String branch) throws IOException {
        return git.getRepository().resolve(REFS_HEADS_PATH + branch);
    }

    protected RevFilter createUnitTestRevFilter(String... commitMessagesToBeExcluded) {
        List<String> excludedMessages = new ArrayList<String>();
        excludedMessages.add(COMMIT_MESSAGE_FOR_UNIT_TEST_SETUP);
        if (commitMessagesToBeExcluded != null) {
            for (String messageToBeExcluded : commitMessagesToBeExcluded) {
                excludedMessages.add(messageToBeExcluded);
            }
        }
        return new CommitRevFilter(excludedMessages);
    }

    class CommitRevFilter extends RevFilter {

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

    protected void assertCommitMesaagesInGitEditorForInteractiveRebase(String... expectedCommitMessages)
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
     * @return
     */
    protected GitRebaseTodo loadGitRebaseTodoUsedInGitDummyEditor() throws FileNotFoundException, IOException {
        return GitRebaseTodo.load(new File(GIT_BASEDIR, GitDummyEditor.EDIT_FILE_RELATIVE_PATH));
    }

    /**
     * @param workingRepoGit
     */
    protected void setGitDummyEditorToConfig(Git git) throws IOException {
        StoredConfig tempConfig = git.getRepository().getConfig();
        tempConfig.setString("core", null, "editor", getGitDummyEditorCMD());
        tempConfig.save();
    }

    private String getGitDummyEditorCMD() {
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("'");
        cmdBuilder.append(getJavaExecutable().getAbsolutePath());
        cmdBuilder.append("' -cp '");
        cmdBuilder.append(getWorkspaceTestClasspath().getAbsolutePath());
        cmdBuilder.append("' ");
        String editorTargetPropertyKey = GitDummyEditor.PROPERTY_KEY_TARGET_BASEDIR;
        String editorTargetPropertyValue = new File(GIT_BASEDIR).getAbsolutePath();
        cmdBuilder.append("-D");
        cmdBuilder.append(editorTargetPropertyKey);
        cmdBuilder.append("='");
        cmdBuilder.append(editorTargetPropertyValue);
        cmdBuilder.append("' ");
        cmdBuilder.append(GitDummyEditor.class.getName());
        return cmdBuilder.toString();
    }

    private File getWorkspaceTargetPath() {
        File eclipseTarget = new File("eclipse-target");
        File target = new File("target");
        File eclipseTargetClasses = new File(eclipseTarget, "classes");
        File targetClasses = new File(target, "classes");
        File eclipsePluginDescriptor = new File(eclipseTargetClasses, "META-INF/maven/plugin.xml");
        File pluginDescriptor = new File(targetClasses, "META-INF/maven/plugin.xml");
        if (eclipsePluginDescriptor.exists()) {
            if (pluginDescriptor.exists()) {
                if (eclipsePluginDescriptor.lastModified() > pluginDescriptor.lastModified()) {
                    return eclipseTarget;
                } else {
                    return target;
                }
            } else {
                return eclipseTarget;
            }
        }
        return target;
    }

    private File getWorkspaceClasspath() {
        return new File(getWorkspaceTargetPath(), "classes");
    }

    private File getWorkspaceTestClasspath() {
        return new File(getWorkspaceTargetPath(), "test-classes");
    }
}
