//
// AbstractGitFlowMojoTestCase.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.Maven;
import org.apache.maven.classrealm.ClassRealmManagerDelegate;
import org.apache.maven.classrealm.ClassRealmRequest;
import org.apache.maven.cli.ExtCliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * The abstract test case for all GitFlowMojo test cases.
 *
 * @author VMedvid
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractGitFlowMojoTestCase {

    protected static final String LS = System.getProperty("line.separator");

    protected static final boolean WITHOUT_DEFAULTS = false;

    protected static final boolean WITH_DEFAULTS = true;

    private static final String PLEXUS_CORE_REALM_ID = "plexus.core";

    private static final String GITFLOW_PLUGIN_VERSION_PROPERTY = "version.gitflow-maven-plugin";

    private static final String GOAL_PREFIX = "flow";

    public static final String GIT_BASEDIR = "target/git";

    private static final String NOT_EXISTING_DIR = "notExistingDir";

    public static final String MASTER_BRANCH = "master";

    public static final String CONFIG_BRANCH = "branch-config";

    public static final String COMMAND_LINE_EXCEPTION_MESSAGE_PATTERN = "Working directory \"{0}\" does not exist!";

    private PlexusContainer container;

    private ControllablePrompter prompter;

    private ClassWorld classWorld;

    /**
     * Geit execution provides methods to execute different git operation using
     * jGit.
     */
    protected GitExecution git;

    @Mock
    protected Prompter promptControllerMock;

    @Before
    public void setUpAbstractGitFlowMojoTestCase() throws Exception {
        startPlexusContainer();
        git = new GitExecution(GIT_BASEDIR);
        git.cleanupGitBasedir();
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

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, String... activeProfiles) throws Exception {
        executeMojo(basedir, goal, WITH_DEFAULTS, null, null, activeProfiles);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir. If a
     * command line for external execution of git or maven commands will be used
     * than {@link org.codehaus.plexus.util.cli.CommandLineException} will be
     * thrown.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithCommandLineException(File basedir, String goal) throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, null, null, true);
    }

    /**
     * Executes mojo in inetractive mode for passed goal for a project in passed
     * basedir. If a command line for external execution of git or maven
     * commands will be used than
     * {@link org.codehaus.plexus.util.cli.CommandLineException} will be thrown.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithCommandLineExceptionInInteractiveMode(File basedir, String goal)
            throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, null, promptControllerMock, true);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, String... activeProfiles)
            throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, null, null, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Properties properties, String... activeProfiles)
            throws Exception {
        executeMojo(basedir, goal, WITH_DEFAULTS, properties, null, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, Properties properties,
            String... activeProfiles) throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, properties, null, activeProfiles);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param useProfileWithDefaults
     *            the flag determines if profile with plugin default
     *            configurations should be active
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, boolean useProfileWithDefaults, Properties properties,
            String... activeProfiles) throws Exception {
        executeMojo(basedir, goal, useProfileWithDefaults, properties, null, activeProfiles);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param useProfileWithDefaults
     *            the flag determines if profile with plugin default
     *            configurations should be active
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, boolean useProfileWithDefaults,
            Properties properties, String... activeProfiles) throws Exception {
        return executeMojoWithResult(basedir, goal, useProfileWithDefaults, properties, null, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Prompter promptController, String... activeProfiles)
            throws Exception {
        executeMojo(basedir, goal, WITH_DEFAULTS, null, promptController, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, Prompter promptController,
            String... activeProfiles) throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, null, promptController, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, Properties properties, Prompter promptController,
            String... activeProfiles) throws Exception {
        executeMojo(basedir, goal, WITH_DEFAULTS, properties, promptController, activeProfiles);
    }

    /**
     * Executes mojo (with default configurations) for passed goal for a project
     * in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, Properties properties,
            Prompter promptController, String... activeProfiles) throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, properties, promptController, activeProfiles);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param useProfileWithDefaults
     *            the flag determines if profile with plugin default
     *            configurations should be active
     * @param properties
     *            the user properties to be used while maven execution
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @param activeProfiles
     *            the optional profiles that should be activated
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected void executeMojo(File basedir, String goal, boolean useProfileWithDefaults, Properties properties,
            Prompter promptController, String... activeProfiles) throws Exception {
        handleMavenExecutionResult(executeMojoWithResult(basedir, goal, useProfileWithDefaults, properties,
                promptController, activeProfiles));
    }

    private void handleMavenExecutionResult(MavenExecutionResult result) {
        int errorCnt = 0;
        StringBuilder errorMessages = new StringBuilder();
        for (Throwable exc : result.getExceptions()) {
            System.err.println("Error " + (++errorCnt) + ":");
            exc.printStackTrace();
            if (errorCnt > 1) {
                errorMessages.append(", ");
            }
            errorMessages.append("[");
            if (exc instanceof LifecycleExecutionException) {
                exc = exc.getCause();
            }
            errorMessages.append(exc.getMessage());
            errorMessages.append("]");
        }
        if (errorCnt == 1 && errorMessages.toString().contains("offline-dummy.git")) {
            fail("Tried to access remote repository while offline mode. See log output.");
        } else {
            assertFalse("maven command executed with errors: " + errorMessages, result.hasExceptions());
        }
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param useProfileWithDefaults
     *            the flag determines if profile with plugin default
     *            configurations should be active
     * @param properties
     *            the user properties to be used while maven execution
     * @param promptController
     *            the prompt controller that answers to the maven prompts
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithResult(File basedir, String goal, boolean useProfileWithDefaults,
            Properties properties, Prompter promptController, String... activeProfiles) throws Exception {
        return executeMojoWithResult(basedir, goal, useProfileWithDefaults, properties, promptController, false,
                activeProfiles);
    }

    private MavenExecutionResult executeMojoWithResult(File basedir, String goal, boolean useProfileWithDefaults,
            Properties properties, Prompter promptController, boolean throwCommandLineExceptionOnCommandLineExecution,
            String... activeProfiles) throws Exception {
        String pluginVersion = readPom(new File(".")).getVersion();
        String fullGoal = GOAL_PREFIX + ":" + goal;
        MavenExecutionRequest request = createMavenExecutionRequest(basedir, fullGoal, useProfileWithDefaults,
                properties, pluginVersion, throwCommandLineExceptionOnCommandLineExecution, activeProfiles);
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
        return result;
    }

    private MavenExecutionRequest createMavenExecutionRequest(File basedir, String fullGoal,
            boolean useProfileWithDefaults, Properties properties, final String pluginVersion,
            boolean throwCommandLineExceptionOnCommandLineExecution, String... activeProfiles) throws Exception {
        Properties userProperties = properties != null ? properties : new Properties();
        File pom = new File(basedir, "pom.xml");
        MavenExecutionRequestPopulator executionRequestPopulator = container
                .lookup(MavenExecutionRequestPopulator.class);
        ExtCliRequest cliRequest = new ExtCliRequest(new String[] { fullGoal });
        cliRequest.setWorkingDirector(basedir.getAbsolutePath());
        addSpecialProperties(userProperties, pluginVersion, basedir);
        cliRequest.setUserProperties(userProperties);
        Map<String, ConfigurationProcessor> configurationProcessors = container.lookupMap(ConfigurationProcessor.class);
        configurationProcessors.get(SettingsXmlConfigurationProcessor.HINT).process(cliRequest);
        MavenExecutionRequest request = cliRequest.getRequest();
        request.setSystemProperties(System.getProperties());
        WorkspaceReader workspaceReader = new WorkspaceReader() {

            WorkspaceRepository workspaceRepo = new WorkspaceRepository("ide", getClass());

            @Override
            public File findArtifact(Artifact aArtifact) {
                if (aArtifact.getArtifactId().equals("gitflow-maven-plugin")) {
                    if (aArtifact.getExtension().equals("pom")) {
                        return new File("pom.xml");
                    } else {
                        return WorkspaceUtils.getWorkspaceClasspath();
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
        request.setBaseDirectory(
                throwCommandLineExceptionOnCommandLineExecution ? new File(basedir, NOT_EXISTING_DIR) : basedir);
        request.setGoals(Arrays.asList(fullGoal));
        request.setPom(pom);
        request.setUserProperties(userProperties);
        if (!useProfileWithDefaults) {
            request.addInactiveProfile("flowWithDefaults");
        }
        if (activeProfiles != null && activeProfiles.length > 0) {
            request.addInactiveProfile("flowWithDefaults");
            for (String activeProfile : activeProfiles) {
                request.addActiveProfile(activeProfile);
            }
        }
        return request;
    }

    private void addSpecialProperties(Properties userProperties, final String pluginVersion, File basedir) {
        File javaExecutable = WorkspaceUtils.getJavaExecutable();
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
        userProperties.put(AbstractGitFlowMojo.USER_PROPERTY_KEY_EXTERNAL_GIT_EDITOR_USED, "true");
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
     * Asserts that the project version and the value of the version.build
     * property in pom.xml file equals to passed expected version.
     *
     * @param projectPath
     *            the path to the maven project
     * @param expectedVersion
     *            the expected version
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertVersionsInPom(File projectPath, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model workingPom = readPom(projectPath);
        assertVersionsInPom(workingPom, expectedVersion);
    }

    /**
     * Asserts that the project version and the value of the version.build
     * property in pom.xml file equals to passed expected version.
     *
     * @param workingPom
     *            the model of the pom.xml file
     * @param expectedVersion
     *            the expected version
     */
    protected void assertVersionsInPom(Model workingPom, String expectedVersion) {
        assertProjectVersionInPom(workingPom, expectedVersion);
        assertVersionBuildPropertyInPom(workingPom, expectedVersion);
    }

    /**
     * Asserts that the project version in pom.xml file equals to passed
     * expected version.
     *
     * @param projectPath
     *            the path to the maven project
     * @param expectedVersion
     *            the expected project version
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertProjectVersionInPom(File projectPath, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model workingPom = readPom(projectPath);
        assertProjectVersionInPom(workingPom, expectedVersion);
    }

    /**
     * Asserts that the project version in pom.xml file equals to passed
     * expected version.
     *
     * @param workingPom
     *            the model of the pom.xml file
     * @param expectedVersion
     *            the expected project version
     */
    protected void assertProjectVersionInPom(Model workingPom, String expectedVersion) {
        assertEquals("project version in local pom.xml file is wrong", expectedVersion, workingPom.getVersion());
    }

    /**
     * Asserts that the project parent version in pom.xml file equals to passed
     * expected version.
     *
     * @param projectPath
     *            the path to the maven project
     * @param expectedVersion
     *            the expected project parent version
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertParentVersionsInPom(File projectPath, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model workingPom = readPom(projectPath);
        assertProjectVersionInPom(workingPom, expectedVersion);
    }

    /**
     * Asserts that the project parent version in pom.xml file equals to passed
     * expected version.
     *
     * @param workingPom
     *            the model of the pom.xml file
     * @param expectedVersion
     *            the expected project parent version
     */
    protected void assertParentVersionsInPom(Model workingPom, String expectedVersion) {
        assertEquals("project parent version in local pom.xml file is wrong", expectedVersion,
                workingPom.getParent().getVersion());
    }

    /**
     * Asserts that the value of the version.build property in pom.xml file
     * equals to passed expected version.
     *
     * @param projectPath
     *            the path to the maven project
     * @param expectedVersion
     *            the expected version value in version.build property
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertVersionBuildPropertyInPom(File projectPath, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        assertCustomVersionPropertyInPom(projectPath, "version.build", expectedVersion);
    }

    /**
     * Asserts that the value of the version.build property in pom.xml file
     * equals to passed expected version.
     *
     * @param workingPom
     *            the model of the pom.xml file
     * @param expectedVersion
     *            the expected version value in version.build property
     */
    protected void assertVersionBuildPropertyInPom(Model workingPom, String expectedVersion) {
        assertCustomVersionPropertyInPom(workingPom, "version.build", expectedVersion);
    }

    /**
     * Asserts that the value of the passed property in pom.xml file equals to
     * passed expected version.
     *
     * @param projectPath
     *            the path to the maven project
     * @param versionProperty
     *            the version property to be checked
     * @param expectedVersion
     *            the expected version value in version property
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertCustomVersionPropertyInPom(File projectPath, String versionProperty, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model workingPom = readPom(projectPath);
        assertCustomVersionPropertyInPom(workingPom, versionProperty, expectedVersion);
    }

    /**
     * Asserts that the value of the passed property in pom.xml file equals to
     * passed expected version.
     *
     * @param workingPom
     *            the model of the pom.xml file
     * @param versionProperty
     *            the version property to be checked
     * @param expectedVersion
     *            the expected version value in version property
     */
    protected void assertCustomVersionPropertyInPom(Model workingPom, String versionProperty, String expectedVersion) {
        assertEquals(
                "version property '" + versionProperty + "' in POM file [" + workingPom.getPomFile().getName()
                        + "] has wrong value",
                expectedVersion, workingPom.getProperties().getProperty(versionProperty));
    }

    /**
     * Asserts that the site for the project was deployed. The site deployment
     * and this test works only if the distribution management configuration for
     * site is copied from parent-pom.xml to the pom.xml file of the test
     * project.
     *
     * @param projectPath
     *            the path to the maven project
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertSiteDeployed(File projectPath)
            throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(projectPath);
        String artifactId = pom.getArtifactId();
        File siteIndexFile = new File(projectPath, "../mvnrepo/gebit-site/" + artifactId + "/index.html");
        assertTrue("project site was not deployed [" + siteIndexFile.getAbsolutePath() + "]", siteIndexFile.exists());
    }

    /**
     * Asserts that the project artifact with expected version was deployed.
     *
     * @param projectPath
     *            the path to the maven project
     * @param version
     *            the expected project version that was deployed
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertArtifactDeployed(File projectPath, String version)
            throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(projectPath);
        String groupId = pom.getGroupId();
        String artifactId = pom.getArtifactId();
        String relativePath = createRelativeRepoPath(groupId, artifactId, version);
        File artifact = new File(projectPath, "../mvnrepo/gebit-releases/" + relativePath);
        assertTrue("project artifact was not deployed [" + artifact.getAbsolutePath() + "]", artifact.exists());
    }

    /**
     * Asserts that the project artifact was installed.
     *
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertArtifactInstalled() throws IOException {
        assertMavenCommandExecuted("clean install");
    }

    /**
     * Asserts that the project artifact with expected version was installed.
     *
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertArtifactNotInstalled() throws IOException {
        assertMavenCommandNotExecuted("clean install");
    }

    protected void assertMavenCommandExecuted(String expectedMvnCommand) throws IOException {
        assertMavenCommandExecution(expectedMvnCommand, true);
    }

    protected void assertMavenCommandNotExecuted(String expectedMvnCommand) throws IOException {
        assertMavenCommandExecution(expectedMvnCommand, false);
    }

    protected void assertMavenCommandExecution(String expectedMvnCommand, boolean executed) throws IOException {
        List<String> executedMavenCommands = loadExecutedMavenCommands();
        assertEquals("expected maven command '" + expectedMvnCommand + "' was " + (executed ? "not " : "") + "executed",
                executed, executedMavenCommands.contains(expectedMvnCommand));
    }

    /**
     * @return
     */
    private List<String> loadExecutedMavenCommands() throws IOException {
        File mvnCommandsFile = new File(GIT_BASEDIR, ExtMavenCli.MVN_CMDS_LOG_FILENAME);
        if (!mvnCommandsFile.exists()) {
            return Collections.EMPTY_LIST;
        }
        return FileUtils.readLines(mvnCommandsFile, "UTF-8");
    }

    private String createRelativeRepoPath(String aGroupId, String aArtifactId, String aVersion) {
        StringBuilder path = new StringBuilder();
        path.append(aGroupId.replace(".", "/"));
        path.append("/");
        path.append(aArtifactId);
        path.append("/");
        path.append(aVersion);
        path.append("/");
        path.append(aArtifactId);
        path.append("-");
        path.append(aVersion);
        path.append(".jar");
        return path.toString();
    }

    /**
     * Asserts that the passed maven execution result consists of exception of
     * passed class and with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedExceptionClass
     *            the class of expected exception
     * @param expectedExceptionMessage
     *            the message of expected exception or <code>null</code> if
     *            exception message shouldn't be checked
     * @param regex
     *            <code>true</code> if <code>expectedExceptionMessage</code> is
     *            a regular exprssion that should be matched
     */
    protected void assertExceptionOnMavenExecution(MavenExecutionResult mavenExecutionResult,
            Class<? extends Throwable> expectedExceptionClass, String expectedExceptionMessage, boolean regex) {
        List<Throwable> exceptions = mavenExecutionResult.getExceptions();
        assertEquals("number of maven execution exceptions is different from expected", 1, exceptions.size());
        Throwable exception = exceptions.get(0);
        if (exception instanceof LifecycleExecutionException) {
            exception = exception.getCause();
        }
        assertException(exception, expectedExceptionClass, expectedExceptionMessage, regex);
    }

    private void assertException(Throwable exception, Class<? extends Throwable> expectedExceptionClass,
            String expectedExceptionMessage, boolean regex) {
        if (!exception.getClass().equals(expectedExceptionClass)) {
            assertEquals("unexpected maven execution exception",
                    expectedExceptionClass.getName() + "(" + expectedExceptionMessage + ")",
                    exception.getClass().getName() + "(" + exception.getMessage() + ")");
        }
        assertExceptionMessage(exception, expectedExceptionMessage, regex);
    }

    private void assertExceptionMessage(Throwable exception, String expectedExceptionMessage, boolean regex) {
        if (expectedExceptionMessage != null) {
            if (regex) {
                assertTrue(
                        "maven execution exception message doesn't matches expected pattern.\nPattern: "
                                + expectedExceptionMessage + "\nMessage: " + exception.getMessage(),
                        Pattern.compile(expectedExceptionMessage, Pattern.MULTILINE | Pattern.DOTALL)
                                .matcher(exception.getMessage()).matches());
            } else {
                assertEquals("unexpected maven execution exception message", expectedExceptionMessage,
                        exception.getMessage());
            }
        }
    }

    /**
     * Asserts that the passed maven execution result consists of failure
     * exception with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessage
     *            the expected message of failure exception or <code>null</code>
     *            if exception message shouldn't be checked
     */
    protected void assertMavenFailureException(MavenExecutionResult mavenExecutionResult, String expectedMessage) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoFailureException.class, expectedMessage, false);
    }

    /**
     * Asserts that the passed maven execution result consists of failure
     * exception with message that matches the passed pattern.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessagePattern
     *            the pattern of expected message of failure exception or
     *            <code>null</code> if exception message shouldn't be checked
     */
    protected void assertMavenFailureExceptionRegEx(MavenExecutionResult mavenExecutionResult,
            String expectedMessagePattern) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoFailureException.class, expectedMessagePattern, true);
    }

    /**
     * Asserts that the passed maven execution result consists of failure
     * exception with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessage
     *            the expected message of failure exception or <code>null</code>
     *            if exception message shouldn't be checked
     */
    protected void assertMavenExecutionException(MavenExecutionResult mavenExecutionResult, String expectedMessage) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoExecutionException.class, expectedMessage, false);
    }

    /**
     * Asserts that the passed maven execution result consists of failure
     * exception with message that matches the passed pattern.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessage
     *            the pattern of expected message of failure exception or
     *            <code>null</code> if exception message shouldn't be checked
     */
    protected void assertMavenExecutionExceptionRegEx(MavenExecutionResult mavenExecutionResult,
            String expectedMessage) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoExecutionException.class, expectedMessage, true);
    }

    protected void assertGitFlowFailureException(MavenExecutionResult mavenExecutionResult, String expectedProblem,
            String expectedSolutionProposal, String... expectedSteps) {
        String expectedMessage = createGitFlowMessage(expectedProblem, expectedSolutionProposal, expectedSteps);
        assertExceptionOnMavenExecution(mavenExecutionResult, GitFlowFailureException.class, expectedMessage, false);
    }

    private String createGitFlowMessage(String problem, String solutionProposal, String... stepsToContinue) {
        StringBuilder message = new StringBuilder();
        if (problem != null) {
            message.append(problem);
        }
        if (solutionProposal != null) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(solutionProposal);
        }
        if (stepsToContinue != null && stepsToContinue.length > 0) {
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append("How to continue:");
            message.append("\n");
            for (int i = 0; i < stepsToContinue.length; i++) {
                if (i > 0) {
                    message.append("\n");
                }
                message.append(stepsToContinue[i]);
            }
        }
        message.insert(0, "\n\n############################ Gitflow problem ###########################\n");
        message.append("\n########################################################################\n");
        return message.toString();
    }

    private String createGitFlowMessage(GitFlowFailureInfo expectedFailureInfoPattern) {
        return createGitFlowMessage(expectedFailureInfoPattern.getProblem(),
                expectedFailureInfoPattern.getSolutionProposal(), expectedFailureInfoPattern.getStepsToContinue());
    }

    protected void assertGitFlowFailureExceptionRegEx(MavenExecutionResult mavenExecutionResult,
            GitFlowFailureInfo expectedFailureInfoPattern) {
        String expectedMessage = createGitFlowMessage(expectedFailureInfoPattern);
        assertExceptionOnMavenExecution(mavenExecutionResult, GitFlowFailureException.class, expectedMessage, true);
    }

    protected void assertGitflowFailureOnCommandLineException(RepositorySet repositorySet,
            MavenExecutionResult mavenExecutionResult) {
        String expectedProblem = "External command execution failed with error:\n"
                + MessageFormat.format(COMMAND_LINE_EXCEPTION_MESSAGE_PATTERN,
                        new File(repositorySet.getWorkingDirectory(), NOT_EXISTING_DIR).getAbsolutePath());
        String expectedSolutionProposal = "Please report the error in the GBLD JIRA.";
        String expectedMessage = createGitFlowMessage(expectedProblem, expectedSolutionProposal);
        assertMavenExecutionException(mavenExecutionResult, expectedMessage);
    }

    protected void verifyNoMoreInteractionsAndReset(Object... mocks) {
        verifyNoMoreInteractions(mocks);
        reset(mocks);
    }
}
