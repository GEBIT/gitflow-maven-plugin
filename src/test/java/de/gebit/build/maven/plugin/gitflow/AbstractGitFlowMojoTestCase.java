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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;

/**
 * The abstract test case for all GitFlowMojo test cases.
 *
 * @author VMedvid
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractGitFlowMojoTestCase {

    protected static final String LS = System.getProperty("line.separator");

    private static final String PLEXUS_CORE_REALM_ID = "plexus.core";

    private static final String GITFLOW_PLUGIN_VERSION_PROPERTY = "version.gitflow-maven-plugin";

    private static final String GOAL_PREFIX = "flow";

    private static final String GIT_BASEDIR = "target/git";

    public static final String MASTER_BRANCH = "master";

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
        request.setBaseDirectory(basedir);
        request.setGoals(Arrays.asList(fullGoal));
        request.setPom(pom);
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
     *            the pass to the maven project
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertVersionInPom(File projectPath, String expectedVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model workingPom = readPom(projectPath);
        assertEquals("project version in local pom.xml file is wrong", expectedVersion, workingPom.getVersion());
        assertEquals("version.build property in local pom.xml file is wrong", expectedVersion,
                workingPom.getProperties().getProperty("version.build"));
    }

    /**
     * Asserts that the site for the project was deployed. The site deployment
     * and this test works only if the distribution management configuration for
     * site is copied from parent-pom.xml to the pom.xml file of the test
     * project.
     *
     * @param projectPath
     *            the pass to the maven project
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertReleaseSiteDeployed(File projectPath)
            throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(projectPath);
        String artifactId = pom.getArtifactId();
        File siteIndexFile = new File(projectPath, "../mvnrepo/gebit-site/" + artifactId + "/index.html");
        assertTrue("release site was not deployed [" + siteIndexFile.getAbsolutePath() + "]", siteIndexFile.exists());
    }

    /**
     * Asserts that the project artifact with expected version was deployed.
     *
     * @param projectPath
     *            the pass to the maven project
     * @param releaseVersion
     *            the expected project version that was deployed
     * @throws ComponentLookupException
     *             if an error occurs while preparing maven processor
     * @throws ModelParseException
     *             if an error occurs while parsing pom.xml file
     * @throws IOException
     *             if an error occurs while reading pom.xml file
     */
    protected void assertReleaseArtifactDeployed(File workingDir, String releaseVersion)
            throws ComponentLookupException, ModelParseException, IOException {
        Model pom = readPom(workingDir);
        String groupId = pom.getGroupId();
        String artifactId = pom.getArtifactId();
        String relativePath = createRelativeRepoPath(groupId, artifactId, releaseVersion);
        File artifact = new File(workingDir, "../mvnrepo/gebit-releases/" + relativePath);
        assertTrue("release artifact was not deployed [" + artifact.getAbsolutePath() + "]", artifact.exists());
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
}
