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
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.maven.Maven;
import org.apache.maven.classrealm.ClassRealmManagerDelegate;
import org.apache.maven.classrealm.ClassRealmRequest;
import org.apache.maven.cli.ExtCliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.cli.internal.BootstrapCoreExtensionManager;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.scope.internal.MojoExecutionScopeModule;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.session.scope.internal.SessionScopeModule;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;
import de.gebit.xmlxpath.XML;

/**
 * The abstract test case for all GitFlowMojo test cases.
 *
 * @author Volodymyr Medvid
 */
public abstract class AbstractGitFlowMojoTestCase {

    protected static final String LS = System.getProperty("line.separator");

    protected static final String MASTER_BRANCH = "master";

    protected static final String CONFIG_BRANCH = "branch-config";

    private static final boolean WITH_DEFAULTS = true;

    private static final String PLEXUS_CORE_REALM_ID = "plexus.core";

    private static final String GITFLOW_PLUGIN_VERSION_PROPERTY = "version.gitflow-maven-plugin";

    private static final String GOAL_PREFIX = "flow";

    private static final String GIT_ROOT_DIR = "target/git";

    private static final String GIT_REPO_BASEDIR = "target/git-repos";

    private static final String NOT_EXISTING_DIR = "notExistingDir";

    private static final String COMMAND_LINE_EXCEPTION_MESSAGE_PATTERN = "Working directory \"{0}\" does not exist!";

    private static final String GITFLOW_FAULURE_EXCEPTION_HEADER = "\n\n############################ Gitflow problem ###########################\n";

    private static final String GITFLOW_FAULURE_EXCEPTION_FOOTER = "\n########################################################################\n";

    private PlexusContainer container;

    private ControllablePrompter prompter;

    private ClassWorld classWorld;

    /**
     * Geit execution provides methods to execute different git operation using
     * jGit.
     */
    protected GitExecution git;

    private File testBasedir;

    @Mock
    protected Prompter promptControllerMock;

    @Before
    public void setUpAbstractGitFlowMojoTestCase() throws Exception {
        MockitoAnnotations.initMocks(this);
        startPlexusContainer();
        File gitBaseDir = new File(GIT_ROOT_DIR);
        if (!gitBaseDir.exists()) {
            gitBaseDir.mkdirs();
        }
        testBasedir = Files.createTempDirectory(gitBaseDir.toPath(), "tmp").toFile();
        git = new GitExecution(testBasedir, GIT_REPO_BASEDIR);
    }

    @After
    public void tearDownAbstractGitFlowMojoTestCase() throws Exception {
        if (git != null) {
            try {
                git.cleanupGitBasedir();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        stopPlexusContainer();
    }

    protected void startPlexusContainer() throws Exception {
        classWorld = new ClassWorld(PLEXUS_CORE_REALM_ID, Thread.currentThread().getContextClassLoader());
        ClassRealm coreRealm = classWorld.getRealm(PLEXUS_CORE_REALM_ID);

        CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom( coreRealm );
        CoreExtensionEntry extension =
            loadCoreExtension( coreRealm, coreEntry.getExportedArtifacts() );

        ClassRealm containerRealm = setupContainerRealm( classWorld, coreRealm, extension );

        ContainerConfiguration containerConf = new DefaultContainerConfiguration().setClassWorld(classWorld)
                .setRealm(containerRealm).setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true)
                .setJSR250Lifecycle(true).setName("maven");

        Set<String> exportedArtifacts = new HashSet<String>( coreEntry.getExportedArtifacts() );
        Set<String> exportedPackages = new HashSet<String>( coreEntry.getExportedPackages() );
        if ( extension != null )
        {
            exportedArtifacts.addAll( extension.getExportedArtifacts() );
            exportedPackages.addAll( extension.getExportedPackages() );
        }

        final CoreExports exports = new CoreExports( containerRealm, exportedArtifacts, exportedPackages );

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

        DefaultPlexusContainer plexusContainer = new DefaultPlexusContainer( containerConf, module, new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( ILoggerFactory.class ).toInstance( LoggerFactory.getILoggerFactory() );
                bind( CoreExports.class ).toInstance( exports );
            }
        } );


        // NOTE: To avoid inconsistencies, we'll use the TCCL exclusively for lookups
        plexusContainer.setLookupRealm( null );
        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader( plexusContainer.getContainerRealm() );
            if (extension != null)
            {
                plexusContainer.discoverComponents( extension.getClassRealm(), new SessionScopeModule( plexusContainer ),
                                              new MojoExecutionScopeModule( plexusContainer ) );
            }
        } finally {
            Thread.currentThread().setContextClassLoader( originClassLoader );
        }

        container = plexusContainer;
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo for passed goal for a project in passed basedir. If a command
     * line for external execution of git or maven commands will be used than
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
    protected MavenExecutionResult executeMojoWithCommandLineException(File basedir, String goal) throws Exception {
        return executeMojoWithCommandLineException(basedir, goal, null);
    }

    /**
     * Executes mojo for passed goal for a project in passed basedir. If a command
     * line for external execution of git or maven commands will be used than
     * {@link org.codehaus.plexus.util.cli.CommandLineException} will be thrown.
     *
     * @param basedir
     *            the basedir of the project which goal should be executed for
     * @param goal
     *            the flow goal to be executed (without 'flow' prefix)
     * @param properties
     *            the user properties to be used while maven execution
     * @return the maven execution result
     * @throws Exception
     *             if an error occurs while preparing maven for mojo execution
     */
    protected MavenExecutionResult executeMojoWithCommandLineException(File basedir, String goal, Properties properties)
            throws Exception {
        return executeMojoWithResult(basedir, goal, WITH_DEFAULTS, properties, null, true);
    }

    /**
     * Executes mojo in inetractive mode for passed goal for a project in passed
     * basedir. If a command line for external execution of git or maven commands
     * will be used than {@link org.codehaus.plexus.util.cli.CommandLineException}
     * will be thrown.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     *            the flag determines if profile with plugin default configurations
     *            should be active
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
     *            the flag determines if profile with plugin default configurations
     *            should be active
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     * Executes mojo (with default configurations) for passed goal for a project in
     * passed basedir.
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
     *            the flag determines if profile with plugin default configurations
     *            should be active
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
     *            the flag determines if profile with plugin default configurations
     *            should be active
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
        String fullGoal = (goal.startsWith("#") ? goal.substring(1) : GOAL_PREFIX + ":" + goal);

        MavenExecutionRequest request = createMavenExecutionRequest(basedir, fullGoal, useProfileWithDefaults,
                properties, pluginVersion, throwCommandLineExceptionOnCommandLineExecution, activeProfiles);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        request.setInteractiveMode(promptController != null);
        MavenExecutionResult result;
        Thread.currentThread().setContextClassLoader(container.getContainerRealm());
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

    private MavenExecutionRequest createMavenExecutionRequest(final File basedir, String fullGoal,
            boolean useProfileWithDefaults, Properties properties, final String pluginVersion,
            boolean throwCommandLineExceptionOnCommandLineExecution, String... activeProfiles) throws Exception {
        Properties userProperties = properties != null ? properties : new Properties();
        File pom = new File(basedir, "pom.xml");
        if (!pom.exists()) {
            // try different
            pom = new File(basedir, "parent-pom.xml");
        }
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
                } else if (aArtifact.getArtifactId().equals("upstream-pom")) {
                    if (aArtifact.getExtension().equals("pom")) {
                        return new File(basedir, "upstream-pom-" + aArtifact.getVersion() + ".xml").getAbsoluteFile();
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

    private MavenExecutionRequest createMavenExecutionRequest(PlexusContainer container, final String pluginVersion) throws Exception {
        Properties userProperties = new Properties();

        MavenExecutionRequestPopulator executionRequestPopulator = container
                .lookup(MavenExecutionRequestPopulator.class);
        ExtCliRequest cliRequest = new ExtCliRequest(new String[] { "" });
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
        request.setGoals(Arrays.asList(""));
        request.setUserProperties(userProperties);
        return request;
    }

    private void addSpecialProperties(Properties userProperties, final String pluginVersion, File basedir) {
        File javaExecutable = WorkspaceUtils.getJavaExecutable();
        userProperties.setProperty(AbstractGitFlowMojo.USER_PROPERTY_KEY_CMD_MVN_EXECUTABLE,
                javaExecutable.getAbsolutePath());
        String classPath = System.getProperty("java.class.path");
        String mainClass = ExtMavenCli.class.getName();
        List<String> javaArgs = new ArrayList<>();
        javaArgs.add("-D" + ExtMavenCli.PROPERTY_KEY_OUTPUT_DIR + "=" + testBasedir.getAbsolutePath());
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
        return readPom(container, basedir);
    }

    protected static Model readPom(PlexusContainer container, File basedir) throws ComponentLookupException, ModelParseException, IOException {
        ModelProcessor modelProcessor = container.lookup(ModelProcessor.class);
        return modelProcessor.read(new File(basedir, "pom.xml"), null);
    }

    protected void writePom(File basedir, Model pom) throws IOException {
        try (FileWriter writer = new FileWriter(new File(basedir, "pom.xml"))) {
            new MavenXpp3Writer().write(writer, pom);
        }
    }

    /**
     * Asserts that the project version and the value of the version.build property
     * in pom.xml file equals to passed expected version.
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
     * Asserts that the project version and the value of the version.build property
     * in pom.xml file equals to passed expected version.
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
     * Asserts that the project version in pom.xml file equals to passed expected
     * version.
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
     * Asserts that the project version in pom.xml file equals to passed expected
     * version.
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
        assertParentVersionsInPom(workingPom, expectedVersion);
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
     * Asserts that the value of the version.build property in pom.xml file equals
     * to passed expected version.
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
     * Asserts that the value of the version.build property in pom.xml file equals
     * to passed expected version.
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
     * Asserts that the site for the project was deployed. The site deployment and
     * this test works only if the distribution management configuration for site is
     * copied from parent-pom.xml to the pom.xml file of the test project.
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

    @SuppressWarnings("unchecked")
    private List<String> loadExecutedMavenCommands() throws IOException {
        File mvnCommandsFile = new File(testBasedir, ExtMavenCli.MVN_CMDS_LOG_FILENAME);
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
     *            <code>true</code> if <code>expectedExceptionMessage</code> is a
     *            regular exprssion that should be matched
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
            String exceptionMessage = trimGitFlowFailureExceptionMessage(exception.getMessage());
            if (regex) {
                assertTrue(
                        "maven execution exception message doesn't matches expected pattern.\nPattern: "
                                + expectedExceptionMessage + "\nMessage: " + exceptionMessage,
                        Pattern.compile(expectedExceptionMessage, Pattern.MULTILINE | Pattern.DOTALL)
                                .matcher(exceptionMessage).matches());
            } else {
                assertEquals("unexpected maven execution exception message", expectedExceptionMessage,
                        exceptionMessage);
            }
        }
    }

    private String trimGitFlowFailureExceptionMessage(String aMessage) {
        String message = aMessage;
        if (message != null) {
            if (message.startsWith(GITFLOW_FAULURE_EXCEPTION_HEADER)) {
                message = message.substring(GITFLOW_FAULURE_EXCEPTION_HEADER.length());
            }
            if (message.endsWith(GITFLOW_FAULURE_EXCEPTION_FOOTER)) {
                int pos = message.lastIndexOf("\n", message.length() - GITFLOW_FAULURE_EXCEPTION_FOOTER.length() - 1)
                        - 1;
                message = message.substring(0, pos);
            }
        }
        return message;
    }

    /**
     * Asserts that the passed maven execution result consists of failure exception
     * with passed message.
     *
     * @param mavenExecutionResult
     *            the maven execution result to be tested
     * @param expectedMessage
     *            the expected message of failure exception or <code>null</code> if
     *            exception message shouldn't be checked
     */
    protected void assertMavenExecutionException(MavenExecutionResult mavenExecutionResult, String expectedMessage) {
        assertExceptionOnMavenExecution(mavenExecutionResult, MojoExecutionException.class, expectedMessage, false);
    }

    protected void assertGitFlowFailureException(MavenExecutionResult mavenExecutionResult,
            GitFlowFailureInfo expectedFailureInfo) {
        assertGitFlowFailureException(mavenExecutionResult, expectedFailureInfo.getProblem(),
                expectedFailureInfo.getSolutionProposal(), expectedFailureInfo.getStepsToContinue());
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

    protected static String prepareSquashMessage(String squashCommitMessage) {
        return squashCommitMessage.replace("\\n", "\n");
    }

    protected void assertInstallProjectFailureException(MavenExecutionResult mavenExecutionResult, String goal,
            String branch, String process) {
        assertInstallProjectFailureException(mavenExecutionResult, goal, branch, process, null);
    }

    protected void assertInstallProjectFailureException(MavenExecutionResult mavenExecutionResult, String goal,
            String branch, String process, String reason) {
        String expectedProblem = "Failed to install the project on branch '" + branch + "' after " + process + "."
                + (reason != null ? "\nReason: " + reason : "");
        String expectedSolutionProposal = "Please solve the problems on project, add and commit your changes and run "
                + "'mvn flow:" + goal + "' again in order to continue.\nDo NOT push the branch!\n"
                + "Alternatively you can use property '-Dflow.installProject=false' while running 'mvn flow:" + goal
                + "' to skip the project installation.";
        String[] expectedSteps = new String[] { "'git add' and 'git commit' to commit your changes",
                "'mvn flow:" + goal + "' to continue " + process + " process after problem solving", "or 'mvn flow:"
                        + goal + " -Dflow.installProject=false' to continue by skipping the project installation" };
        assertGitFlowFailureException(mavenExecutionResult, expectedProblem, expectedSolutionProposal, expectedSteps);
    }

    protected void assertTestProjectFailureException(MavenExecutionResult mavenExecutionResult, String goal,
            String branch, String process) {
        assertTestProjectFailureException(mavenExecutionResult, goal, branch, process, null);
    }

    protected void assertTestProjectFailureException(MavenExecutionResult mavenExecutionResult, String goal,
            String branch, String process, String reason) {
        String expectedProblem = "Failed to test the project on branch '" + branch + "' before " + process + "."
                + (reason != null ? "\nReason: " + reason : "");
        String expectedSolutionProposal = "Please solve the problems on project, add and commit your changes and run "
                + "'mvn flow:" + goal + "' again in order to continue.\nDo NOT push the branch!\n"
                + "Alternatively you can use property '-Dflow.skipTestProject=true' while running 'mvn flow:" + goal
                + "' to skip the project test.";
        String[] expectedSteps = new String[] { "'git add' and 'git commit' to commit your changes",
                "'mvn flow:" + goal + "' to continue " + process + " process after problem solving", "or 'mvn flow:"
                        + goal + " -Dflow.skipTestProject=true' to continue by skipping the project test" };
        assertGitFlowFailureException(mavenExecutionResult, expectedProblem, expectedSolutionProposal, expectedSteps);
    }

    protected void removeModule(RepositorySet aRepositorySet, String module) throws IOException, GitAPIException {
        File workingDir = aRepositorySet.getWorkingDirectory();
        FileUtils.deleteDirectory(new File(workingDir, module));
        aRepositorySet.getLocalRepoGit().rm().addFilepattern(module).call();
        XML pom = XML.load(new File(workingDir, "pom.xml"));
        pom.removeFirst("/project/modules/module[text()='" + module + "']");
        pom.store();
    }

    protected void setProjectVersion(RepositorySet aRepositorySet, String newVersion) throws IOException {
        File workingDir = aRepositorySet.getWorkingDirectory();
        File pomFile = new File(workingDir, "pom.xml");
        XML pom = XML.load(pomFile);
        pom.setValue("/project/version", newVersion);
        pom.setValue("/project/properties/version.build", newVersion);
        pom.store();
        String[] modules = pom.getAllValues("/project/modules/module");
        for (String module : modules) {
            File modulePomFile = new File(new File(workingDir, module), "pom.xml");
            XML modulePom = XML.load(modulePomFile);
            modulePom.setValue("/project/version", newVersion);
            modulePom.setValue("/project/parent/version", newVersion);
            modulePom.store();
        }
    }

    private CoreExtensionEntry loadCoreExtension(ClassRealm containerRealm, Set<String> providedArtifacts) {
        try {
            CoreExtension extension = getCoreExtensionDescriptor();
            if (extension == null) {
                return null;
            }
            ContainerConfiguration cc = new DefaultContainerConfiguration() //
                    .setClassWorld(containerRealm.getWorld()) //
                    .setRealm(containerRealm) //
                    .setClassPathScanning(PlexusConstants.SCANNING_INDEX) //
                    .setAutoWiring(true) //
                    .setJSR250Lifecycle(true) //
                    .setName("maven");

            DefaultPlexusContainer container = new DefaultPlexusContainer(cc, new AbstractModule() {

                @Override
                protected void configure() {
                    bind(ILoggerFactory.class).toInstance(LoggerFactory.getILoggerFactory());
                }
            });

            try {
                container.setLookupRealm(containerRealm);

                BootstrapCoreExtensionManager resolver = container.lookup(BootstrapCoreExtensionManager.class);

                String pluginVersion = readPom(container, new File(".")).getVersion();
                MavenExecutionRequest mavenRequest = createMavenExecutionRequest(container, pluginVersion);
                return resolver.loadCoreExtensions(mavenRequest, providedArtifacts, Collections.singletonList(extension)).get(0);
            } finally {
                container.dispose();
            }
        } catch (RuntimeException e) {
            // runtime exceptions are most likely bugs in maven, let them bubble up to the
            // user
            throw e;
        } catch (Exception e) {
            // slf4jLogger.warn("Failed to read extensions descriptor " + extensionsFile +
            // ": " + e.getMessage());
        }
        return null;
    }

    protected CoreExtension getCoreExtensionDescriptor() {
        return null;
    }


    private static ClassRealm setupContainerRealm(ClassWorld classWorld, ClassRealm coreRealm, 
                    CoreExtensionEntry extension) throws DuplicateRealmException, MalformedURLException {
            if (extension != null) {
                    ClassRealm extRealm = classWorld.newRealm("maven.ext", null);

                    extRealm.setParentRealm(coreRealm);

                    // TODO slf4jLogger.debug("Populating class realm " + extRealm.getId());

                    Set<String> exportedPackages = extension.getExportedPackages();
                    ClassRealm realm = extension.getClassRealm();
                    for (String exportedPackage : exportedPackages) {
                            extRealm.importFrom(realm, exportedPackage);
                    }
                    if (exportedPackages.isEmpty()) {
                            // sisu uses realm imports to establish component visibility
                            extRealm.importFrom(realm, realm.getId());
                    }

                    return extRealm;
            }

            return coreRealm;
    }
    
    protected GitFlowFailureInfo format(GitFlowFailureInfo message, Object... replacements) {
        String problem = message.getProblem();
        String solutionProposal = message.getSolutionProposal();
        String[] stepsToContinue = message.getStepsToContinue();
        if (problem != null) {
            problem = MessageFormat.format(problem, replacements);
        }
        if (solutionProposal != null) {
            solutionProposal = MessageFormat.format(solutionProposal, replacements);
        }
        String[] formatedStepsToContinue = null;
        if (stepsToContinue != null) {
            formatedStepsToContinue = new String[stepsToContinue.length];
            for (int i = 0; i < stepsToContinue.length; i++) {
                if (stepsToContinue[i] != null) {
                    stepsToContinue[i] = MessageFormat.format(stepsToContinue[i], replacements);
                }
            }
        }
        return new GitFlowFailureInfo(problem, solutionProposal, formatedStepsToContinue);
    }
}
