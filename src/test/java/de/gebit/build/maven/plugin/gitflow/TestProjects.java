//
// Projects.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.RmCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gebit.build.maven.plugin.gitflow.jgit.GitExecution;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * Class with constants for test project directories.
 *
 * @author Volodymyr Medvid
 */
public class TestProjects {

    private static final Logger LOG = LoggerFactory.getLogger(TestProjects.class);

    protected static final String LS = AbstractGitFlowMojoTestCase.LS;

    private static final String INITIALIZED_FILENAME = "initialized";

    /**
     * The base directory for all projects.
     */
    private static final File PROJECTS_BASEDIR = new File("src/test/resources/projects");

    /**
     * The directory of the basic test project.
     */
    public static final TestProjectData BASIC = new TestProjectData("basic-project", "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with invalid project version.
     */
    public static final TestProjectData INVALID_VERSION = new TestProjectData("invalid-version-project",
            "invalid-version", "GFTST");

    /**
     * The directory of the test project with snapshot dependencies.
     */
    public static final TestProjectData SNAPSHOT_DEPENDENCIES = new TestProjectData("with-snapshot-dependencies",
            "1.2.3-SNAPSHOT", "GFTST");
    /**
     * The directory of the test project with non-snapshot dependencies.
     */
    public static final TestProjectData NON_SNAPSHOT_DEPENDENCIES = new TestProjectData("with-dependencies",
            "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with modules.
     */
    public static final TestProjectData WITH_MODULES = new TestProjectData("with-modules", "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with modules and upstream project
     * configured.
     */
    public static final TestProjectData WITH_UPSTREAM = new TestProjectData("with-upstream", "1.2.3-SNAPSHOT", "GFTST");

    /**
     * The directory of the test project with tycho version.
     */
    public static final TestProjectData TYCHO_PROJECT = new TestProjectData("tycho-project", "1.2.3.4-SNAPSHOT",
            "GFTST");

    /**
     * The directory of the versionless test project.
     */
    public static final TestProjectData VERSIONLESS_PROJECT = new TestProjectData("versionless-project",
            "1.2.3-SNAPSHOT", "GFTST", "gitflow-tests", "de.gebit.build.maven", "gebit-build-extension", "1.1.0-I20");
    
    /**
     * The directory of the versionless test project with tags.
     */
    public static final TestProjectData VERSIONLESS_TAG_PROJECT = new TestProjectData("versionless-tag-project",
            "2.0.0-SNAPSHOT", "GFTST", "gitflow-tests", "de.gebit.build.maven", "gebit-build-extension", "1.1.0-I20");

    public static final String PROFILE_SET_VERSION_WITHOUT_ADDITIONAL_VERSION_COMMANDS = "setVersionWithoutAdditionalVersionCommands";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_DEFAULT = "setVersionAdditionalVersionCommandWithoutDefault";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_NEW_LINE_CHARACTERS = "setVersionAdditionalVersionCommandWithNewLineCharacters";

    public static final String PROFILE_SET_VERSION_WITH_UPSTREAM = "setVersionWithUpstream";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITHOUT_PROMPT = "setVersionAdditionalVersionCommandWithoutPrompt";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_ENABLED_BY_PROMPT = "setVersionAdditionalVersionCommandEnabledByPrompt";

    public static final String PROFILE_SET_VERSION_ADDITIONAL_VERSION_COMMAND_WITH_INTERPOLATION_CYCLE = "setVersionAdditionalVersionCommandWithInterpolationCycle";

    private static File getProjectBasedir(String projectName) {
        return new File(PROJECTS_BASEDIR, projectName);
    }

    public static class TestProjectData {
        public final File basedir;
        public final String artifactId;
        public final String version;
        public final String releaseVersion;
        public final String maintenanceVersion;
        public final String nextReleaseVersion;
        public final String nextSnepshotVersion;
        public final String jiraProject;
        public final String buildName;
        public final String extensionGroupId;
        public final String extensionArtifactId;
        public final String extensionVersion;

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject) {
            this(aProjectName, aVersion, aJiraProject, "gitflow-tests");
        }

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject, String aBuildName) {
            this (aProjectName, aVersion, aJiraProject, aBuildName, null, null, null);
        }

        public TestProjectData(String aProjectName, String aVersion, String aJiraProject, String aBuildName,
                String anExtensionGroupId, String anExtensionArtifactId, String anExtensionVersion) {
            basedir = getProjectBasedir(aProjectName);
            artifactId = aProjectName;
            version = aVersion;
            releaseVersion = StringUtils.substringBeforeLast(version, "-SNAPSHOT");
            maintenanceVersion = StringUtils.substringBeforeLast(releaseVersion, ".");
            String tempNextReleaseVersion;
            try {
                tempNextReleaseVersion = new DefaultVersionInfo(version).getNextVersion().getReleaseVersionString();
            } catch (VersionParseException exc) {
                tempNextReleaseVersion = releaseVersion;
            }
            nextReleaseVersion = tempNextReleaseVersion;
            nextSnepshotVersion = nextReleaseVersion + "-SNAPSHOT";
            jiraProject = aJiraProject;
            buildName = aBuildName;
            extensionGroupId = anExtensionGroupId;
            extensionArtifactId = anExtensionArtifactId;
            extensionVersion = anExtensionVersion;
        }

    }

    public static synchronized void prepareRepositoryIfNotExisting(File gitRepoDir) throws Exception {
        if (!gitRepoDir.exists()) {
            gitRepoDir.mkdirs();
        }
        prepareRepository(gitRepoDir, BASIC);
        prepareRepository(gitRepoDir, WITH_UPSTREAM);
        prepareRepository(gitRepoDir, VERSIONLESS_PROJECT);
        prepareRepository(gitRepoDir, VERSIONLESS_TAG_PROJECT);
    }

    private static void prepareRepository(File repoDir, TestProjectData project) throws Exception {
        String basedirName = project.basedir.getName();
        File repoBasedir = new File(repoDir.getAbsoluteFile(), basedirName);
        File tempInitializedFile = new File(repoBasedir, INITIALIZED_FILENAME);
        if (!tempInitializedFile.exists()) {
            cleanupDir(repoBasedir);
            String projectName = project.artifactId;
            LOG.info("Creating repositories for project '" + projectName + "'.");
            long ms = System.currentTimeMillis();
            GitExecution git = new GitExecution(repoDir.getAbsoluteFile(), null);
            try (RepositorySet repositorySet = git.createGitRepositorySet(project.basedir)) {
                MyTestCase tc = new MyTestCase(project);
                try {
                    tc.setUpAbstractGitFlowMojoTestCase();
                    initRepository(project, git, repositorySet, tc);
                    tempInitializedFile.createNewFile();
                } finally {
                    tc.tearDownAbstractGitFlowMojoTestCase();
                }
            } catch (Throwable e) {
                LOG.warn("Repositories for project '" + projectName + "' couldn't be created! ["
                        + ((System.currentTimeMillis() - ms) / 1000) + "s]", e);
                throw e;
            }
            LOG.info("Repositories for project '" + projectName + "' created. ["
                    + ((System.currentTimeMillis() - ms) / 1000) + "s]");
        }
    }

    private static void cleanupDir(File repoBasedir) throws IOException {
        int maxTries = 10;
        while (repoBasedir.exists()) {
            try {
                FileUtils.deleteDirectory(repoBasedir);
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

    private static void initRepository(TestProjectData project, GitExecution git, RepositorySet repositorySet,
            MyTestCase testCase) throws Exception {
        if (project == BASIC) {
            initBasicRepository(git, repositorySet, testCase, false);
        } else if (project == WITH_UPSTREAM) {
            initUpstreamVersionRepository(git, repositorySet, testCase);
        } else if (project == VERSIONLESS_PROJECT) {
            initVersionlessRepository(git, repositorySet, testCase);
        } else if (project == VERSIONLESS_TAG_PROJECT) {
            initVersionlessWithTagRepository(git, repositorySet, testCase);
        }
    }

    private static void initVersionlessRepository(GitExecution git, RepositorySet repositorySet, MyTestCase testCase)
            throws Exception {
        ExecutorHelper.executeSetVersion(testCase, repositorySet, "1.2.3-SNAPSHOT");
        initBasicRepository(git, repositorySet, testCase, false);
    }

    private static void initVersionlessWithTagRepository(GitExecution git, RepositorySet repositorySet, MyTestCase testCase)
            throws Exception {
        ExecutorHelper.executeSetVersion(testCase, repositorySet, "1.2.3-SNAPSHOT");
        initBasicRepository(git, repositorySet, testCase, true);
    }

    private static void initBasicRepository(GitExecution git, RepositorySet repositorySet, MyTestCase testCase, boolean avoidDuplicateVersions)
            throws Exception {
        git.createBranch(repositorySet, BasicConstants.PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, "master");

        // feature branches
        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.EXISTING_FEATURE_NAME);
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.FEATURE_WITHOUT_VERSION_NAME,
                props("flow.skipFeatureVersion", "true"));
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.REMOTE_FEATURE_NAME);
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.SINGLE_FEATURE_NAME,
                props("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX));
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.FIRST_FEATURE_NAME,
                props("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX));
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.SECOND_FEATURE_NAME,
                props("flow.featureBranchPrefix", BasicConstants.TWO_FEATURE_BRANCHES_PREFIX));
        git.switchToBranch(repositorySet, "master");

        // epic branches
        ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.EXISTING_EPIC_NAME);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.FEATURE_ON_EPIC_NAME);
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_EPIC_BRANCH);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_NAME,
                props("flow.skipFeatureVersion", "true"));
        git.switchToBranch(repositorySet, "master");

        ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.EPIC_WITHOUT_VERSION_NAME,
                props("flow.tychoBuild", "true"));
        git.createAndCommitTestfile(repositorySet, "epic-testfile.txt",
                BasicConstants.EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE);
        git.push(repositorySet);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                BasicConstants.FEATURE_ON_EPIC_WITHOUT_VERSION_NAME);
        git.switchToBranch(repositorySet, BasicConstants.EPIC_WITHOUT_VERSION_BRANCH);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                BasicConstants.FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_NAME,
                props("flow.skipFeatureVersion", "true"));
        git.switchToBranch(repositorySet, "master");

        if (!avoidDuplicateVersions) {
            ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.EPIC_WITH_NEW_VERSION_NAME);
            ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                    BasicConstants.FEATURE_ON_EPIC_WITH_NEW_VERSION_NAME);
            git.switchToBranch(repositorySet, BasicConstants.EPIC_WITH_NEW_VERSION_BRANCH);
            ExecutorHelper.executeSetVersion(testCase, repositorySet, BasicConstants.EPIC_WITH_NEW_VERSION_VERSION);
            git.commitAll(repositorySet, BasicConstants.EPIC_WITH_NEW_VERSION_UPGRADE_COMMIT_MESSAGE);
            git.push(repositorySet);
            git.switchToBranch(repositorySet, "master");

            ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.SINGLE_EPIC_NAME,
                    props("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_BRANCH_PREFIX));
            git.switchToBranch(repositorySet, "master");

            ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.FIRST_EPIC_NAME,
                    props("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX));
            git.switchToBranch(repositorySet, "master");

            ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.SECOND_EPIC_NAME,
                    props("flow.epicBranchPrefix", BasicConstants.TWO_EPIC_BRANCHES_PREFIX));
            git.switchToBranch(repositorySet, "master");
        }

        // maintenance branch with feature and epic branches
        ExecutorHelper.executeMaintenanceStart(testCase, repositorySet, BasicConstants.EXISTING_MAINTENANCE_VERSION,
                BasicConstants.EXISTING_MAINTENANCE_FIRST_VERSION);
        git.createBranch(repositorySet, BasicConstants.MAINTENANCE_PRODUCTION_BRANCH);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        // epic on maintenance
        ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.EPIC_ON_MAINTENANCE_NAME);
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        ExecutorHelper.executeEpicStart(testCase, repositorySet, BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_NAME,
                props("flow.epicBranchPrefix", BasicConstants.SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX));
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        ExecutorHelper.executeEpicStart(testCase, repositorySet,
                BasicConstants.EPIC_WITHOUT_VERSION_ON_MAINTENANCE_NAME, props("flow.tychoBuild", "true"));
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        // feature on maintenance
        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.FEATURE_ON_MAINTENANCE_NAME);
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet, BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_NAME,
                props("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX));
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        // release on maintenance
        ExecutorHelper.executeReleaseStart(testCase, repositorySet, BasicConstants.RELEASE_ON_MAINTENANCE_VERSION,
                BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION);
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        ExecutorHelper.executeReleaseStart(testCase, repositorySet,
                BasicConstants.SINGLE_RELEASE_ON_MAINTENANCE_VERSION,
                BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION,
                props("flow.releaseBranchPrefix", BasicConstants.SINGLE_RELEASE_ON_MAINTENANCE_BRANCH_PREFIX));
        git.switchToBranch(repositorySet, BasicConstants.EXISTING_MAINTENANCE_BRANCH);
        ExecutorHelper.executeReleaseStart(testCase, repositorySet,
                BasicConstants.RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_VERSION,
                BasicConstants.EXISTING_MAINTENANCE_RELEASE_VERSION,
                props("flow.noProduction", "false", "flow.productionBranch", BasicConstants.PRODUCTION_BRANCH));
        git.switchToBranch(repositorySet, "master");

        // maintenance without version
        if (!avoidDuplicateVersions) {
            ExecutorHelper.executeMaintenanceStart(testCase, repositorySet,
                    BasicConstants.MAINTENANCE_WITHOUT_VERSION_VERSION, TestProjects.BASIC.version);
            // epic on maintenance without version
            ExecutorHelper.executeEpicStart(testCase, repositorySet,
                    BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_NAME,
                    props("flow.epicBranchPrefix", BasicConstants.EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX));
            // feature on maintenance without version
            git.switchToBranch(repositorySet, BasicConstants.MAINTENANCE_WITHOUT_VERSION_BRANCH);
            ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                    BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_NAME,
                    props("flow.featureBranchPrefix", BasicConstants.FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX));
            git.switchToBranch(repositorySet, "master");
        }

        // alternative master with one commit
        git.createBranch(repositorySet, BasicConstants.MASTER_WITH_COMMIT_BRANCH);
        git.createAndCommitTestfile(repositorySet, "master-testfile.txt", BasicConstants.MASTER_WITH_COMMIT_MESSAGE);
        // maintenance branch on master with commit
        ExecutorHelper.executeMaintenanceStart(testCase, repositorySet,
                BasicConstants.MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION,
                BasicConstants.MAINTENANCE_ON_MASTER_WITH_COMMIT_FIRST_VERSION);
        // feature on maintenance
        ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_NAME, props("flow.featureBranchPrefix",
                        BasicConstants.FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH_PREFIX));
        git.switchToBranch(repositorySet, "master");

        // alternative master with new version
        git.createBranch(repositorySet, BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH);
        // feature on master with new version
        ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                BasicConstants.FEATURE_ON_MASTER_WITH_NEW_VERSION_NAME,
                props("flow.developmentBranch", BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH));
        git.switchToBranch(repositorySet, BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH);
        ExecutorHelper.executeFeatureStart(testCase, repositorySet,
                BasicConstants.FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_NAME,
                props("flow.developmentBranch", BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH));
        git.switchToBranch(repositorySet, BasicConstants.MASTER_WITH_NEW_VERSION_BRANCH);
        ExecutorHelper.executeSetVersion(testCase, repositorySet, BasicConstants.MASTER_WITH_NEW_VERSION_VERSION);
        git.commitAll(repositorySet, BasicConstants.MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE);
        git.push(repositorySet);
        git.switchToBranch(repositorySet, "master");

        // release branch
        if (!avoidDuplicateVersions) {
            ExecutorHelper.executeReleaseStart(testCase, repositorySet, BasicConstants.EXISTING_RELEASE_VERSION,
                    BASIC.releaseVersion);
            git.switchToBranch(repositorySet, "master");
            ExecutorHelper.executeReleaseStart(testCase, repositorySet, BasicConstants.SINGLE_RELEASE_VERSION,
                    BASIC.releaseVersion, props("flow.releaseBranchPrefix", BasicConstants.SINGLE_RELEASE_BRANCH_PREFIX));
            git.switchToBranch(repositorySet, "master");
            ExecutorHelper.executeReleaseStart(testCase, repositorySet, BasicConstants.RELEASE_WITH_PRODUCTION_VERSION,
                    BASIC.releaseVersion,
                    props("flow.noProduction", "false", "flow.productionBranch", BasicConstants.PRODUCTION_BRANCH));
            git.switchToBranch(repositorySet, "master");
            ExecutorHelper.executeReleaseStart(testCase, repositorySet,
                    BasicConstants.RELEASE_WITH_MISSING_PRODUCTION_VERSION, BASIC.releaseVersion,
                    props("flow.noProduction", "false", "flow.productionBranch", BasicConstants.MISSING_PRODUCTION_BRANCH));
            git.switchToBranch(repositorySet, "master");
        }

        // finalize: empty dummy branch for initial checkout (to avoid CR-LF
        // conflicts)
        git.createOrphanBranch(repositorySet, "dummy", "dummy branch for parking working directory");
        git.switchToBranch(repositorySet, "dummy");
        RmCommand rm = repositorySet.getLocalRepoGit().rm();
        for (File file : repositorySet.getWorkingDirectory().listFiles()) {
            if (!file.getName().equalsIgnoreCase(".git")) {
                rm.addFilepattern(file.getName());
            }
        }
        rm.call();
        git.commitAll(repositorySet, "delete all files in dummy branch");
        git.push(repositorySet);

        repositorySet.getClonedRemoteRepoGit().fetch().call();
        repositorySet.getClonedRemoteRepoGit().checkout().setCreateBranch(true).setName("dummy")
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).setStartPoint("origin/dummy").call();

        // delete local branches as last step
        git.deleteLocalAndRemoteTrackingBranches(repositorySet, BasicConstants.REMOTE_FEATURE_BRANCH);
    }

    private static void initUpstreamVersionRepository(GitExecution git, RepositorySet repositorySet,
            MyTestCase testCase) throws Exception {
        // features
        executeFeatureStart(testCase, repositorySet, WithUpstreamConstants.FEATURE_NAME,
                WithUpstreamConstants.EXPECTED_UPSTREAM_VERSION, WithUpstreamConstants.NEW_UPSTREAM_VERSION);
        git.switchToBranch(repositorySet, "master");
        executeFeatureStart(testCase, repositorySet, WithUpstreamConstants.FEATURE_WITH_OLD_UPSTREAM_NAME,
                WithUpstreamConstants.EXPECTED_UPSTREAM_VERSION, "");
        git.switchToBranch(repositorySet, "master");
        executeFeatureStartWithoutVersionChange(testCase, repositorySet,
                WithUpstreamConstants.FEATURE_WITHOUT_VERSION_NAME);
        git.switchToBranch(repositorySet, "master");
        // release
        ExecutorHelper.executeReleaseStart(testCase, repositorySet, WithUpstreamConstants.EXISTING_RELEASE_VERSION,
                WITH_UPSTREAM.releaseVersion);
        git.switchToBranch(repositorySet, "master");

        // finalize: empty dummy branch for initail checkout (to avoid CR-LF
        // conflicts)
        git.createOrphanBranch(repositorySet, "dummy", "dummy branch for parking working directory");
        git.switchToBranch(repositorySet, "dummy");
        RmCommand rm = repositorySet.getLocalRepoGit().rm();
        for (File file : repositorySet.getWorkingDirectory().listFiles()) {
            if (!file.getName().equalsIgnoreCase(".git")) {
                rm.addFilepattern(file.getName());
            }
        }
        rm.call();
        git.commitAll(repositorySet, "delete all files in dummy branch");
        git.push(repositorySet);

        repositorySet.getClonedRemoteRepoGit().fetch().call();
        repositorySet.getClonedRemoteRepoGit().checkout().setCreateBranch(true).setName("dummy")
                .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM).setStartPoint("origin/dummy").call();
    }

    private static void executeFeatureStart(AbstractGitFlowMojoTestCase testCase, RepositorySet repositorySet,
            String featureName, String expectedUpstreamDefaultVersion, String newUpstreamVersion) throws Exception {
        final String PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH = "Enter the version of the Test Parent Pom project to reference." + LS
                + "Hints:\n"
                + "- if you have corresponding feature branch for Test Parent Pom, enter its feature branch version\n"
                + "- if you want to reference in your feature branch a specific version of Test Parent Pom, then enter the version you want to use\n"
                + "- in other case enter the current version of Test Parent Pom on development branch" + LS
                + "Enter the version:";
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", featureName);
        Prompter promptControllerMock = mock(Prompter.class);
        when(promptControllerMock.prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, expectedUpstreamDefaultVersion))
                .thenReturn(newUpstreamVersion);
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-start", userProperties,
                promptControllerMock);
        verify(promptControllerMock).prompt(PROMPT_UPSTREAM_VERSION_ON_FEATURE_BRANCH, expectedUpstreamDefaultVersion);
        verifyNoMoreInteractions(promptControllerMock);
    }

    private static void executeFeatureStartWithoutVersionChange(AbstractGitFlowMojoTestCase testCase,
            RepositorySet repositorySet, String featureName) throws Exception {
        Properties userProperties = new Properties();
        userProperties.setProperty("featureName", featureName);
        userProperties.setProperty("flow.skipFeatureVersion", "true");
        testCase.executeMojo(repositorySet.getWorkingDirectory(), "feature-start", userProperties);
    }

    private static Properties props(String... props) {
        Properties properties = new Properties();
        for (int i = 0; i < props.length; i += 2) {
            properties.setProperty(props[i], props[i + 1]);
        }
        return properties;
    }

    private static class MyTestCase extends AbstractGitFlowMojoTestCase {
        TestProjectData data;
        public MyTestCase(TestProjectData data) {
            this.data = data;
        }
        @Override
        protected CoreExtension getCoreExtensionDescriptor() {
            if (data.extensionGroupId != null && data.extensionArtifactId != null && data.extensionVersion != null) {
                CoreExtension extension = new CoreExtension();
                extension.setGroupId(data.extensionGroupId);
                extension.setArtifactId(data.extensionArtifactId);
                extension.setVersion(data.extensionVersion);
                return extension;
            }
            return null;
        }
    }

    //
    // MASTER_BRANCH
    // -- MASTER_WITH_COMMIT_BRANCH
    // ----- MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH
    // -------- FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH
    // (feature-on-maintenance-on-master-with-commit/)
    // -- MASTER_WITH_NEW_VERSION_BRANCH
    // ----- FEATURE_ON_MASTER_WITH_NEW_VERSION_BRANCH
    // ----- FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_BRANCH
    // -- EXISTING_MAINTENANCE_BRANCH
    // ----- EPIC_ON_MAINTENANCE_BRANCH
    // ----- SINGLE_EPIC_ON_MAINTENANCE_BRANCH
    // ----- FEATURE_ON_MAINTENANCE_BRANCH
    // ----- SINGLE_FEATURE_ON_MAINTENANCE_BRANCH
    // (single-feature-on-maintenance/)
    // ----- RELEASE_ON_MAINTENANCE_BRANCH
    // ----- SINGLE_RELEASE_ON_MAINTENANCE_BRANCH
    // (single-release-on-maintenance/)
    // ----- RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_BRANCH
    // -- MAINTENANCE_PRODUCTION_BRANCH
    // -- EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH
    // -- EXISTING_FEATURE_BRANCH
    // -- REMOTE_FEATURE_BRANCH
    // -- SINGLE_FEATURE_BRANCH (single-feature/)
    // -- FIRST_FEATURE_BRANCH (features/two-)
    // -- SECOND_FEATURE_BRANCH (features/two-)
    // -- EXISTING_EPIC_BRANCH
    // ----- FEATURE_ON_EPIC_BRANCH
    // -- FEATURE_WITHOUT_VERSION_ON_EPIC_BRANCH
    // -- EPIC_WITH_NEW_VERSION_BRANCH
    // ----- FEATURE_ON_EPIC_WITH_NEW_VERSION_BRANCH
    // -- SINGLE_EPIC_BRANCH (single-epic/)
    // -- FIRST_EPIC_BRANCH (epics/two-)
    // -- SECOND_EPIC_BRANCH (epics/two-)
    // -- EXISTING_RELEASE_BRANCH
    // -- SINGLE_RELEASE_BRANCH (single-release/)
    // -- RELEASE_WITH_PRODUCTION_BRANCH
    // -- RELEASE_WITH_MISSING_PRODUCTION_BRANCH
    // (single-release-with-production/)
    // PRODUCTION_BRANCH
    // FEATURE_WITHOUT_VERSION_BRANCH
    // EPIC_WITHOUT_VERSION_BRANCH
    // -- FEATURE_ON_EPIC_WITHOUT_VERSION_BRANCH
    // FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_BRANCH
    // MAINTENANCE_WITHOUT_VERSION_BRANCH
    // -- FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH
    // (feature-on-maintenance-without-version/)
    // -- EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH
    // (epic-on-maintenance-without-version/)
    //
    public interface BasicConstants {
        // alternative master
        public static final String MASTER_WITH_COMMIT_BRANCH = "master-with-commit";
        public static final String MASTER_WITH_COMMIT_MESSAGE = "MASTER: Unit test dummy file commit";

        // master with new version
        public static final String MASTER_WITH_NEW_VERSION_BRANCH = "master-with-new-version";
        public static final String MASTER_WITH_NEW_VERSION_VERSION = "2.0.0-SNAPSHOT";
        public static final String MASTER_WITH_NEW_VERSION_RELEASE_VERSION = "2.0.0";
        public static final String MASTER_WITH_NEW_VERSION_COMMIT_MESSAGE = "MASTER: version update";

        // maintenance
        public static final String EXISTING_MAINTENANCE_VERSION = "30.1";
        public static final String EXISTING_MAINTENANCE_FIRST_VERSION = "30.1.0-SNAPSHOT";
        public static final String EXISTING_MAINTENANCE_RELEASE_VERSION = "30.1.0";
        public static final String EXISTING_MAINTENANCE_BRANCH = "maintenance/gitflow-tests-"
                + EXISTING_MAINTENANCE_VERSION;

        public static final String MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION = "31.2";
        public static final String MAINTENANCE_ON_MASTER_WITH_COMMIT_FIRST_VERSION = "31.2.0-SNAPSHOT";
        public static final String MAINTENANCE_ON_MASTER_WITH_COMMIT_RELEASE_VERSION = "31.2.0";
        public static final String MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH = "maintenance/gitflow-tests-"
                + MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION;

        public static final String MAINTENANCE_WITHOUT_VERSION_VERSION = "32.3";
        public static final String MAINTENANCE_WITHOUT_VERSION_BRANCH = "maintenance/gitflow-tests-"
                + MAINTENANCE_WITHOUT_VERSION_VERSION;

        // production
        public static final String PRODUCTION_BRANCH = "existing-production";
        public static final String MISSING_PRODUCTION_BRANCH = "missing-production";
        public static final String MAINTENANCE_PRODUCTION_BRANCH = PRODUCTION_BRANCH + "-"
                + EXISTING_MAINTENANCE_BRANCH;

        // release
        public static final String EXISTING_RELEASE_VERSION = "50.1.0";
        public static final String EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION = "50.1.1-SNAPSHOT";
        public static final String EXISTING_RELEASE_BRANCH = "release/gitflow-tests-" + EXISTING_RELEASE_VERSION;

        public static final String RELEASE_ON_MAINTENANCE_VERSION = "51.2.0";
        public static final String RELEASE_ON_MAINTENANCE_NEW_DEVELOPMENT_VERSION = "51.2.1-SNAPSHOT";
        public static final String RELEASE_ON_MAINTENANCE_BRANCH = "release/gitflow-tests-"
                + RELEASE_ON_MAINTENANCE_VERSION;

        public static final String SINGLE_RELEASE_BRANCH_PREFIX = "single-release/";

        public static final String SINGLE_RELEASE_VERSION = "52.3.0";
        public static final String SINGLE_RELEASE_BRANCH = SINGLE_RELEASE_BRANCH_PREFIX + SINGLE_RELEASE_VERSION;

        public static final String SINGLE_RELEASE_ON_MAINTENANCE_BRANCH_PREFIX = "single-release-on-maintenance/";

        public static final String SINGLE_RELEASE_ON_MAINTENANCE_VERSION = "53.4.0";
        public static final String SINGLE_RELEASE_ON_MAINTENANCE_BRANCH = SINGLE_RELEASE_ON_MAINTENANCE_BRANCH_PREFIX
                + SINGLE_RELEASE_ON_MAINTENANCE_VERSION;

        public static final String RELEASE_WITH_PRODUCTION_VERSION = "54.5.0";
        public static final String RELEASE_WITH_PRODUCTION_NEW_DEVELOPMENT_VERSION = "54.5.1-SNAPSHOT";
        public static final String RELEASE_WITH_PRODUCTION_BRANCH = "release/gitflow-tests-"
                + RELEASE_WITH_PRODUCTION_VERSION;

        public static final String RELEASE_WITH_MISSING_PRODUCTION_VERSION = "55.6.0";
        public static final String RELEASE_WITH_MISSING_PRODUCTION_BRANCH = "release/gitflow-tests-"
                + RELEASE_WITH_MISSING_PRODUCTION_VERSION;

        public static final String RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_VERSION = "56.7.0";
        public static final String RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_NEW_DEVELOPMENT_VERSION = "56.7.1-SNAPSHOT";
        public static final String RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_BRANCH = "release/gitflow-tests-"
                + RELEASE_WITH_PRODUCTION_ON_MAINTENANCE_VERSION;

        // epic
        public static final String EXISTING_EPIC_ISSUE = BASIC.jiraProject + "-201";
        public static final String EXISTING_EPIC_NAME = EXISTING_EPIC_ISSUE + "-existing";
        public static final String EXISTING_EPIC_BRANCH = "epic/" + EXISTING_EPIC_NAME;
        public static final String EXISTING_EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + EXISTING_EPIC_ISSUE
                + "-SNAPSHOT";
        public static final String EXISTING_EPIC_VERSION_COMMIT_MESSAGE = EXISTING_EPIC_ISSUE
                + ": updating versions for epic branch";

        public static final String EPIC_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-202";
        public static final String EPIC_WITHOUT_VERSION_NAME = EPIC_WITHOUT_VERSION_ISSUE + "-without-version";
        public static final String EPIC_WITHOUT_VERSION_BRANCH = "epic/" + EPIC_WITHOUT_VERSION_NAME;
        public static final String EPIC_WITHOUT_VERSION_VERSION = TestProjects.BASIC.version;
        public static final String EPIC_WITHOUT_VERSION_COMMIT_MESSAGE_TESTFILE = "EPIC: Unit test dummy file commit";

        public static final String EPIC_ON_MAINTENANCE_ISSUE = BASIC.jiraProject + "-203";
        public static final String EPIC_ON_MAINTENANCE_NAME = EPIC_ON_MAINTENANCE_ISSUE + "-on-maintenance";
        public static final String EPIC_ON_MAINTENANCE_BRANCH = "epic/" + EPIC_ON_MAINTENANCE_NAME;
        public static final String EPIC_ON_MAINTENANCE_VERSION = EXISTING_MAINTENANCE_RELEASE_VERSION + "-"
                + EPIC_ON_MAINTENANCE_ISSUE + "-SNAPSHOT";
        public static final String EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE = EPIC_ON_MAINTENANCE_ISSUE
                + ": updating versions for epic branch";

        public static final String EPIC_WITHOUT_VERSION_ON_MAINTENANCE_ISSUE = BASIC.jiraProject + "-204";
        public static final String EPIC_WITHOUT_VERSION_ON_MAINTENANCE_NAME = EPIC_WITHOUT_VERSION_ON_MAINTENANCE_ISSUE
                + "-without-vers-on-maint";
        public static final String EPIC_WITHOUT_VERSION_ON_MAINTENANCE_BRANCH = "epic/"
                + EPIC_WITHOUT_VERSION_ON_MAINTENANCE_NAME;

        public static final String EPIC_WITH_NEW_VERSION_ISSUE = BASIC.jiraProject + "-205";
        public static final String EPIC_WITH_NEW_VERSION_NAME = EPIC_WITH_NEW_VERSION_ISSUE + "-with-new-version";
        public static final String EPIC_WITH_NEW_VERSION_BRANCH = "epic/" + EPIC_WITH_NEW_VERSION_NAME;
        public static final String EPIC_WITH_NEW_VERSION_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + EPIC_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT";
        public static final String EPIC_WITH_NEW_VERSION_ON_MAINTENANCE_VERSION = TestProjects.BASIC.maintenanceVersion + "-"
                + EPIC_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT";
        public static final String EPIC_WITH_NEW_VERSION_COMMIT_MESSAGE = EPIC_WITH_NEW_VERSION_ISSUE
                + ": updating versions for epic branch";
        public static final String EPIC_WITH_NEW_VERSION_UPGRADE_COMMIT_MESSAGE = "EPIC: version update";

        public static final String SINGLE_EPIC_BRANCH_PREFIX = "single-epic/";

        public static final String SINGLE_EPIC_ISSUE = BASIC.jiraProject + "-206";
        public static final String SINGLE_EPIC_NAME = SINGLE_EPIC_ISSUE + "-single";
        public static final String SINGLE_EPIC_BRANCH = SINGLE_EPIC_BRANCH_PREFIX + SINGLE_EPIC_NAME;
        public static final String SINGLE_EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + SINGLE_EPIC_ISSUE
                + "-SNAPSHOT";
        public static final String SINGLE_EPIC_VERSION_COMMIT_MESSAGE = SINGLE_EPIC_ISSUE
                + ": updating versions for epic branch";

        public static final String TWO_EPIC_BRANCHES_PREFIX = "epics/two-";

        public static final String FIRST_EPIC_ISSUE = BASIC.jiraProject + "-207";
        public static final String FIRST_EPIC_NAME = FIRST_EPIC_ISSUE + "-first";
        public static final String FIRST_EPIC_BRANCH = TWO_EPIC_BRANCHES_PREFIX + FIRST_EPIC_NAME;
        public static final String FIRST_EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + FIRST_EPIC_ISSUE
                + "-SNAPSHOT";
        public static final String FIRST_EPIC_VERSION_COMMIT_MESSAGE = FIRST_EPIC_ISSUE
                + ": updating versions for epic branch";

        public static final String SECOND_EPIC_ISSUE = BASIC.jiraProject + "-208";
        public static final String SECOND_EPIC_NAME = SECOND_EPIC_ISSUE + "-second";
        public static final String SECOND_EPIC_BRANCH = TWO_EPIC_BRANCHES_PREFIX + SECOND_EPIC_NAME;
        public static final String SECOND_EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-" + SECOND_EPIC_ISSUE
                + "-SNAPSHOT";
        public static final String SECOND_EPIC_VERSION_COMMIT_MESSAGE = SECOND_EPIC_ISSUE
                + ": updating versions for epic branch";

        public static final String SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX = "single-epic-on-maint/";

        public static final String SINGLE_EPIC_ON_MAINTENANCE_ISSUE = BASIC.jiraProject + "-209";
        public static final String SINGLE_EPIC_ON_MAINTENANCE_NAME = SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + "-on-maintenance";
        public static final String SINGLE_EPIC_ON_MAINTENANCE_BRANCH = SINGLE_EPIC_ON_MAINTENANCE_BRANCH_PREFIX
                + SINGLE_EPIC_ON_MAINTENANCE_NAME;
        public static final String SINGLE_EPIC_ON_MAINTENANCE_VERSION = EXISTING_MAINTENANCE_RELEASE_VERSION + "-"
                + SINGLE_EPIC_ON_MAINTENANCE_ISSUE + "-SNAPSHOT";
        public static final String SINGLE_EPIC_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE = SINGLE_EPIC_ON_MAINTENANCE_ISSUE
                + ": updating versions for epic branch";

        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX = "epic-on-maint-without-vers/";

        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-210";
        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_NAME = EPIC_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE
                + "-on-maint-without-vers";
        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH = EPIC_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX
                + EPIC_ON_MAINTENANCE_WITHOUT_VERSION_NAME;
        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + EPIC_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE + "-SNAPSHOT";
        public static final String EPIC_ON_MAINTENANCE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE = EPIC_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE
                + ": updating versions for epic branch";

        // feature
        public static final String EXISTING_FEATURE_ISSUE = BASIC.jiraProject + "-101";
        public static final String EXISTING_FEATURE_NAME = EXISTING_FEATURE_ISSUE + "-existing";
        public static final String EXISTING_FEATURE_BRANCH = "feature/" + EXISTING_FEATURE_NAME;
        public static final String EXISTING_FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + EXISTING_FEATURE_ISSUE + "-SNAPSHOT";
        public static final String EXISTING_FEATURE_VERSION_COMMIT_MESSAGE = EXISTING_FEATURE_ISSUE
                + ": updating versions for feature branch";

        public static final String REMOTE_FEATURE_ISSUE = BASIC.jiraProject + "-102";
        public static final String REMOTE_FEATURE_NAME = REMOTE_FEATURE_ISSUE + "-remote-only";
        public static final String REMOTE_FEATURE_BRANCH = "feature/" + REMOTE_FEATURE_NAME;
        public static final String REMOTE_FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + REMOTE_FEATURE_ISSUE + "-SNAPSHOT";

        public static final String SINGLE_FEATURE_BRANCH_PREFIX = "single-feature/";

        public static final String SINGLE_FEATURE_ISSUE = BASIC.jiraProject + "-103";
        public static final String SINGLE_FEATURE_NAME = SINGLE_FEATURE_ISSUE + "-single";
        public static final String SINGLE_FEATURE_BRANCH = SINGLE_FEATURE_BRANCH_PREFIX + SINGLE_FEATURE_NAME;
        public static final String SINGLE_FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + SINGLE_FEATURE_ISSUE + "-SNAPSHOT";
        public static final String SINGLE_FEATURE_VERSION_COMMIT_MESSAGE = SINGLE_FEATURE_ISSUE
                + ": updating versions for feature branch";

        public static final String TWO_FEATURE_BRANCHES_PREFIX = "features/two-";

        public static final String FIRST_FEATURE_ISSUE = BASIC.jiraProject + "-104";
        public static final String FIRST_FEATURE_NAME = FIRST_FEATURE_ISSUE + "-first";
        public static final String FIRST_FEATURE_BRANCH = TWO_FEATURE_BRANCHES_PREFIX + FIRST_FEATURE_NAME;
        public static final String FIRST_FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-" + FIRST_FEATURE_ISSUE
                + "-SNAPSHOT";
        public static final String FIRST_FEATURE_VERSION_COMMIT_MESSAGE = FIRST_FEATURE_ISSUE
                + ": updating versions for feature branch";

        public static final String SECOND_FEATURE_ISSUE = BASIC.jiraProject + "-105";
        public static final String SECOND_FEATURE_NAME = SECOND_FEATURE_ISSUE + "-second";
        public static final String SECOND_FEATURE_BRANCH = TWO_FEATURE_BRANCHES_PREFIX + SECOND_FEATURE_NAME;
        public static final String SECOND_FEATURE_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + SECOND_FEATURE_ISSUE + "-SNAPSHOT";
        public static final String SECOND_FEATURE_VERSION_COMMIT_MESSAGE = SECOND_FEATURE_ISSUE
                + ": updating versions for feature branch";

        public static final String SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX = "single-feature-on-maint/";

        public static final String SINGLE_FEATURE_ON_MAINTENANCE_ISSUE = BASIC.jiraProject + "-106";
        public static final String SINGLE_FEATURE_ON_MAINTENANCE_NAME = SINGLE_FEATURE_ON_MAINTENANCE_ISSUE
                + "-on-maintenance";
        public static final String SINGLE_FEATURE_ON_MAINTENANCE_BRANCH = SINGLE_FEATURE_ON_MAINTENANCE_BRANCH_PREFIX
                + SINGLE_FEATURE_ON_MAINTENANCE_NAME;
        public static final String SINGLE_FEATURE_ON_MAINTENANCE_VERSION = EXISTING_MAINTENANCE_RELEASE_VERSION + "-"
                + SINGLE_FEATURE_ON_MAINTENANCE_ISSUE + "-SNAPSHOT";
        public static final String SINGLE_FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE = SINGLE_FEATURE_ON_MAINTENANCE_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_MAINTENANCE_ISSUE = BASIC.jiraProject + "-107";
        public static final String FEATURE_ON_MAINTENANCE_NAME = FEATURE_ON_MAINTENANCE_ISSUE + "-on-maintenance";
        public static final String FEATURE_ON_MAINTENANCE_BRANCH = "feature/" + FEATURE_ON_MAINTENANCE_NAME;
        public static final String FEATURE_ON_MAINTENANCE_VERSION = EXISTING_MAINTENANCE_RELEASE_VERSION + "-"
                + FEATURE_ON_MAINTENANCE_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_MAINTENANCE_VERSION_COMMIT_MESSAGE = FEATURE_ON_MAINTENANCE_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH_PREFIX = "feature-on-maint-on-master-with-commit/";

        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_ISSUE = BASIC.jiraProject + "-108";
        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_NAME = FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_ISSUE
                + "-on-maintenance";
        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH = FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_BRANCH_PREFIX
                + FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_NAME;
        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION = MAINTENANCE_ON_MASTER_WITH_COMMIT_RELEASE_VERSION
                + "-" + FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_VERSION_COMMIT_MESSAGE = FEATURE_ON_MAINTENANCE_ON_MASTER_WITH_COMMIT_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-109";
        public static final String FEATURE_WITHOUT_VERSION_NAME = FEATURE_WITHOUT_VERSION_ISSUE + "-without-version";
        public static final String FEATURE_WITHOUT_VERSION_BRANCH = "feature/" + FEATURE_WITHOUT_VERSION_NAME;
        public static final String FEATURE_WITHOUT_VERSION_VERSION = TestProjects.BASIC.version;
        public static final String FEATURE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_WITHOUT_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX = "feature-on-maint-without-vers/";

        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-110";
        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_NAME = FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE
                + "-on-maint-without-vers";
        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH = FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_BRANCH_PREFIX
                + FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_NAME;
        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_VERSION = TestProjects.BASIC.releaseVersion
                + "-" + FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_ON_MAINTENANCE_WITHOUT_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE = BASIC.jiraProject + "-111";
        public static final String FEATURE_ON_MASTER_WITH_NEW_VERSION_NAME = FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE
                + "-on-master-with-new-version";
        public static final String FEATURE_ON_MASTER_WITH_NEW_VERSION_BRANCH = "feature/"
                + FEATURE_ON_MASTER_WITH_NEW_VERSION_NAME;
        public static final String FEATURE_ON_MASTER_WITH_NEW_VERSION_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_MASTER_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_ON_MASTER_WITH_NEW_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_ISSUE = BASIC.jiraProject
                + "-112";
        public static final String FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_NAME = FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_ISSUE;
        public static final String FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_BRANCH = "feature/"
                + FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_NAME;
        public static final String FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_VERSION = TestProjects.BASIC.releaseVersion
                + "-" + FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_WITHOUT_DESCRIPTION_ON_MASTER_WITH_NEW_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_EPIC_ISSUE = BASIC.jiraProject + "-113";
        public static final String FEATURE_ON_EPIC_NAME = FEATURE_ON_EPIC_ISSUE + "-on-epic";
        public static final String FEATURE_ON_EPIC_BRANCH = "feature/" + FEATURE_ON_EPIC_NAME;
        public static final String FEATURE_ON_EPIC_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + FEATURE_ON_EPIC_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_EPIC_VERSION_COMMIT_MESSAGE = FEATURE_ON_EPIC_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_EPIC_WITH_NEW_VERSION_ISSUE = BASIC.jiraProject + "-114";
        public static final String FEATURE_ON_EPIC_WITH_NEW_VERSION_NAME = FEATURE_ON_EPIC_WITH_NEW_VERSION_ISSUE
                + "-on-epic-with-new-version";
        public static final String FEATURE_ON_EPIC_WITH_NEW_VERSION_BRANCH = "feature/"
                + FEATURE_ON_EPIC_WITH_NEW_VERSION_NAME;
        public static final String FEATURE_ON_EPIC_WITH_NEW_VERSION_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + FEATURE_ON_EPIC_WITH_NEW_VERSION_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_EPIC_WITH_NEW_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_ON_EPIC_WITH_NEW_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_ON_EPIC_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-115";
        public static final String FEATURE_ON_EPIC_WITHOUT_VERSION_NAME = FEATURE_ON_EPIC_WITHOUT_VERSION_ISSUE
                + "-on-epic-without-version";
        public static final String FEATURE_ON_EPIC_WITHOUT_VERSION_BRANCH = "feature/"
                + FEATURE_ON_EPIC_WITHOUT_VERSION_NAME;
        public static final String FEATURE_ON_EPIC_WITHOUT_VERSION_VERSION = TestProjects.BASIC.releaseVersion + "-"
                + FEATURE_ON_EPIC_WITHOUT_VERSION_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_ON_EPIC_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_ON_EPIC_WITHOUT_VERSION_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_ISSUE = BASIC.jiraProject + "-116";
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_NAME = FEATURE_WITHOUT_VERSION_ON_EPIC_ISSUE
                + "-without-version-on-epic";
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_BRANCH = "feature/"
                + FEATURE_WITHOUT_VERSION_ON_EPIC_NAME;
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_VERSION = EXISTING_EPIC_VERSION;
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_VERSION_COMMIT_MESSAGE = FEATURE_WITHOUT_VERSION_ON_EPIC_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_ISSUE = BASIC.jiraProject + "-117";
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_NAME = FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_ISSUE
                + "-without-vers-on-epic-without-vers";
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_BRANCH = "feature/"
                + FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_NAME;
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_VERSION = TestProjects.BASIC.version;
        public static final String FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_VERSION_COMMIT_MESSAGE = FEATURE_WITHOUT_VERSION_ON_EPIC_WITHOUT_VERSION_ISSUE
                + ": updating versions for feature branch";
    }

    public interface WithUpstreamConstants {
        // feature
        public static final String FEATURE_ISSUE = WITH_UPSTREAM.jiraProject + "-101";
        public static final String FEATURE_NAME = FEATURE_ISSUE;
        public static final String FEATURE_BRANCH = "feature/" + FEATURE_NAME;
        public static final String FEATURE_VERSION = TestProjects.WITH_UPSTREAM.releaseVersion + "-" + FEATURE_ISSUE
                + "-SNAPSHOT";
        public static final String FEATURE_VERSION_COMMIT_MESSAGE = FEATURE_ISSUE
                + ": updating versions for feature branch";

        public static final String FEATURE_WITHOUT_VERSION_ISSUE = WITH_UPSTREAM.jiraProject + "-102";
        public static final String FEATURE_WITHOUT_VERSION_NAME = FEATURE_WITHOUT_VERSION_ISSUE + "-without-version";
        public static final String FEATURE_WITHOUT_VERSION_BRANCH = "feature/" + FEATURE_WITHOUT_VERSION_NAME;

        public static final String FEATURE_WITH_OLD_UPSTREAM_ISSUE = WITH_UPSTREAM.jiraProject + "-103";
        public static final String FEATURE_WITH_OLD_UPSTREAM_NAME = FEATURE_WITH_OLD_UPSTREAM_ISSUE;
        public static final String FEATURE_WITH_OLD_UPSTREAM_BRANCH = "feature/" + FEATURE_WITH_OLD_UPSTREAM_NAME;
        public static final String FEATURE_WITH_OLD_UPSTREAM_VERSION = TestProjects.WITH_UPSTREAM.releaseVersion + "-"
                + FEATURE_WITH_OLD_UPSTREAM_ISSUE + "-SNAPSHOT";
        public static final String FEATURE_WITH_OLD_UPSTREAM_VERSION_COMMIT_MESSAGE = FEATURE_WITH_OLD_UPSTREAM_ISSUE
                + ": updating versions for feature branch";

        public static final String EXPECTED_UPSTREAM_VERSION = "1.0.0";
        public static final String NEW_UPSTREAM_VERSION = "2.0.0";
        public static final String UPSTREAM_FEATURE_VERSION = EXPECTED_UPSTREAM_VERSION + "-"
                + FEATURE_WITH_OLD_UPSTREAM_ISSUE + "-SNAPSHOT";
        public static final String UPSTREAM_FEATURE_RELATIVE_PATH = "upstream-pom-" + UPSTREAM_FEATURE_VERSION + ".xml";

        // release
        public static final String EXISTING_RELEASE_VERSION = "50.1.0";
        public static final String EXISTING_RELEASE_NEW_DEVELOPMENT_VERSION = "50.1.1-SNAPSHOT";
        public static final String EXISTING_RELEASE_BRANCH = "release/gitflow-tests-" + EXISTING_RELEASE_VERSION;
    }

}
