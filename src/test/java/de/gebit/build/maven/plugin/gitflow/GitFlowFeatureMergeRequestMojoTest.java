//
// GitFlowFeatureMergeRequestMojoTest.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import de.gebit.build.maven.plugin.gitflow.TestProjects.BasicConstants;
import de.gebit.build.maven.plugin.gitflow.jgit.RepositorySet;

/**
 * @author Volodja
 */
public class GitFlowFeatureMergeRequestMojoTest extends AbstractGitFlowMojoTestCase {

    private static final String GOAL = "feature-merge-request";

    private static final String FEATURE_BRANCH = BasicConstants.EXISTING_FEATURE_BRANCH;

    private static final String FEATURE_ISSUE = BasicConstants.EXISTING_FEATURE_ISSUE;

    private static final String MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE;

    private static final String MERGE_REQUEST_TITLE_TEMPLATE = "Resolve feature @{key}";

    private static final String GITLAB_USER = "gluser";

    private static final String GITLAB_PASSWORD = "glpass";

    private static final String GITLAB_PROJECT = "test/project";

    private static final String PROMPT_MESSAGE_ONE_FEATURE_SELECT = "Feature branches:" + LS + "1. "
            + BasicConstants.SINGLE_FEATURE_BRANCH + LS + "Choose feature branch which to create merge request for";

    private static final String PROMPT_MR_WITH_BASE_BRANCH_AHEAD = "Base branch '" + MASTER_BRANCH
            + "' has changes that are not yet included in feature branch '" + FEATURE_BRANCH
            + "'. Do you want to start merge request anyway?";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private RepositorySet repositorySet;

    private int gitLabPort;

    private Properties userProperties;

    @Before
    public void setUp() throws Exception {
        repositorySet = git.useGitRepositorySet(TestProjects.BASIC, FEATURE_BRANCH);
        gitLabPort = wireMockRule.port();
        userProperties = new Properties();
        userProperties.setProperty("flow.supportedGitLabHosts", "localhost");
        userProperties.setProperty("flow.gitLabProjectUrl", gitLabProjectUrl());
    }

    @After
    public void tearDown() throws Exception {
        if (repositorySet != null) {
            repositorySet.close();
        }
    }

    private String gitLabProjectUrl() {
        return "http://localhost:" + gitLabPort + "/" + GITLAB_PROJECT;
    }

    @Test
    public void testExecuteWithCommandLineException() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithCommandLineException(repositorySet.getWorkingDirectory(), GOAL);
        // verify
        assertGitflowFailureOnCommandLineException(repositorySet, result);
    }

    @Test
    public void testExecute() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID);
        when(promptControllerMock.prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID);
        verify(promptControllerMock).prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    private void stubAllForDefaultMergerRequest(int expectedProjectId)
            throws UnsupportedEncodingException, PrompterException {
        stubAllForDefaultMergerRequest(expectedProjectId, MERGE_REQUEST_TITLE);
    }

    private void stubAllForDefaultMergerRequest(int expectedProjectId, String mrTitle)
            throws UnsupportedEncodingException, PrompterException {
        stubAllForDefaultMergerRequest(expectedProjectId, FEATURE_BRANCH, mrTitle);
    }

    private void stubAllForDefaultMergerRequest(int expectedProjectId, String sourceBranch, String mrTitle)
            throws UnsupportedEncodingException, PrompterException {
        final int EXPECTED_MERGE_REQUEST_ID = 43;
        staubForDefaultLogin();
        stubForGetProject(GITLAB_PROJECT, expectedProjectId);
        stubForGetMergeRequest(expectedProjectId, sourceBranch);
        stubForCreateMergeRequest(expectedProjectId, sourceBranch, MASTER_BRANCH, mrTitle, EXPECTED_MERGE_REQUEST_ID);
    }

    private void staubForDefaultLogin() throws PrompterException {
        stubForOAuth(GITLAB_USER, GITLAB_PASSWORD);
        when(promptControllerMock.prompt(eq("Enter GitLab user name:"), anyString())).thenReturn(GITLAB_USER);
        when(promptControllerMock.promptForPassword("Enter GitLab user password:")).thenReturn(GITLAB_PASSWORD);
    }

    private void verifyAllForDefaultMergeRequest(int expectedProjectId)
            throws PrompterException, UnsupportedEncodingException {
        verifyAllForDefaultMergeRequest(expectedProjectId, MERGE_REQUEST_TITLE);
    }

    private void verifyAllForDefaultMergeRequest(int expectedProjectId, String mrTitle)
            throws PrompterException, UnsupportedEncodingException {
        verifyAllForDefaultMergeRequest(expectedProjectId, FEATURE_BRANCH, mrTitle);
    }

    private void verifyAllForDefaultMergeRequest(int expectedProjectId, String sourceBranch, String mrTitle)
            throws PrompterException, UnsupportedEncodingException {
        verifyRequestForDefaultLogin();
        verifyRequestForGetProject(GITLAB_PROJECT);
        verifyRequestForGetMergeRequest(expectedProjectId, sourceBranch);
        verifyRequestForCreateMergeRequest(expectedProjectId, sourceBranch, MASTER_BRANCH, mrTitle);
    }

    private void verifyRequestForDefaultLogin() throws PrompterException {
        verify(promptControllerMock).prompt(eq("Enter GitLab user name:"), anyString());
        verify(promptControllerMock).promptForPassword("Enter GitLab user password:");
        verifyRequestForOAuth(GITLAB_USER, GITLAB_PASSWORD);
    }

    @SuppressWarnings("unused")
    private void printAllServerRequests() {
        wireMockRule.getAllServeEvents().stream().forEach(e -> System.out.println(e.getRequest()));
    }

    private void stubForOAuth(String user, String pass) {
        stubFor(post(urlEqualTo("/oauth/token"))
                .withRequestBody(equalToJson("{ \"grant_type\": \"password\", \"username\": \"" + user
                        + "\", \"password\": \"" + pass + "\" }"))
                .willReturn(okJson("{ \"access_token\": \"DUMMYTOKEN\" }")));
    }

    private void stubForGetProject(String projectPath, int expectedProjectId) throws UnsupportedEncodingException {
        stubFor(get(urlEqualTo("/api/v4/projects/" + encode(projectPath)))
                .willReturn(okJson("{ \"id\": " + expectedProjectId + "}")));
    }

    private void stubForGetMergeRequest(int projectId, String sourceBranch, int... expectedMergeRequestIds) {
        StringBuilder response = new StringBuilder();
        for (int expectedMergeRequestId : expectedMergeRequestIds) {
            if (response.length() > 0) {
                response.append(", ");
            }
            appendMergeRequestResponse(response, expectedMergeRequestId);
        }
        response.insert(0, "[");
        response.append("]");
        stubFor(get(urlPathEqualTo("/api/v4/projects/" + projectId + "/merge_requests"))
                .withQueryParam("state", equalTo("opened")).withQueryParam("source_branch", equalTo(sourceBranch))
                .willReturn(okJson(response.toString())));
    }

    private String createMergeRequestResponse(int mergeRequestId) {
        return appendMergeRequestResponse(new StringBuilder(), mergeRequestId).toString();
    }

    private StringBuilder appendMergeRequestResponse(StringBuilder response, int mergeRequestId) {
        response.append("{ \"id\": ");
        response.append(mergeRequestId);
        response.append(", \"web_url\": \"");
        response.append(createMergeRequestUrl(mergeRequestId));
        response.append("\" }");
        return response;
    }

    private String createMergeRequestUrl(int mergeRequestId) {
        return gitLabProjectUrl() + "/-/merge_requests/" + mergeRequestId;
    }

    private void stubForCreateMergeRequest(int projectId, String sourceBranch, String targetBranch, String title,
            int expectedMergeRequestId) throws UnsupportedEncodingException {
        stubFor(post(urlPathEqualTo("/api/v4/projects/" + projectId + "/merge_requests"))
                .withRequestBody(param("source_branch", sourceBranch))
                .withRequestBody(param("target_branch", targetBranch)).withRequestBody(param("title", title))
                .willReturn(okJson(createMergeRequestResponse(expectedMergeRequestId))));
    }

    private StringValuePattern param(String key, String value) throws UnsupportedEncodingException {
        return matching("(^|.*&)" + quote(key) + "=" + quote(encode(value)) + "(&.*|$)");
    }

    private String encode(String str) throws UnsupportedEncodingException {
        return URLEncoder.encode(str, StandardCharsets.UTF_8.toString());
    }

    private String quote(String str) {
        return Pattern.quote(str);
    }

    private void verifyRequestForOAuth(String user, String pass) {
        WireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(equalToJson(
                "{ \"grant_type\": \"password\", \"username\": \"" + user + "\", \"password\": \"" + pass + "\" }")));
    }

    private void verifyRequestForGetProject(String projectPath) throws UnsupportedEncodingException {
        WireMock.verify(exactly(1), getRequestedFor(urlEqualTo("/api/v4/projects/" + encode(projectPath))));
    }

    private void verifyRequestForGetMergeRequest(int projectId, String sourceBranch) {
        WireMock.verify(exactly(1), getRequestedFor(urlPathEqualTo("/api/v4/projects/" + projectId + "/merge_requests"))
                .withQueryParam("state", equalTo("opened")).withQueryParam("source_branch", equalTo(sourceBranch)));
    }

    private void verifyRequestForCreateMergeRequest(int projectId, String sourceBranch, String targetBranch,
            String title) throws UnsupportedEncodingException {
        WireMock.verify(exactly(1),
                postRequestedFor(urlPathEqualTo("/api/v4/projects/" + projectId + "/merge_requests"))
                        .withRequestBody(param("source_branch", sourceBranch))
                        .withRequestBody(param("target_branch", targetBranch)).withRequestBody(param("title", title)));
    }

    @Test
    public void testExecuteWithLocalPathFromGitRemoteConfig() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureExceptionRegEx(result,
                new GitFlowFailureInfo(
                        "\\QRemote git repository URL '\\E.*[\\\\/]basic-project[\\\\/]origin\\.git"
                                + "\\Q' seems to be a local path and is not supported for GitLab interactions.\\E",
                        null));
    }

    @Test
    public void testExecuteInBatchMode() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties);
        // verify
        assertGitFlowFailureException(result,
                "'mvn flow:feature-merge-request' can't be executed in non-interactive mode.",
                "Please create the feature merge request in interactive mode.",
                "'mvn flow:feature-merge-request' to run in interactive mode");
    }

    @Test
    public void testExecuteOnMaster() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String USED_FEATURE_BRANCH = BasicConstants.SINGLE_FEATURE_BRANCH;
        final String USED_MERGE_REQUEST_TITLE = "Resolve feature " + BasicConstants.SINGLE_FEATURE_ISSUE;
        git.switchToBranch(repositorySet, USED_FEATURE_BRANCH);
        prepareFeatureBranchForMergeRequest();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, USED_FEATURE_BRANCH, USED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.featureBranchPrefix", BasicConstants.SINGLE_FEATURE_BRANCH_PREFIX);
        when(promptControllerMock.prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"))).thenReturn("1");
        when(promptControllerMock.prompt("What is the title of the merge request?", USED_MERGE_REQUEST_TITLE))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, USED_FEATURE_BRANCH, USED_MERGE_REQUEST_TITLE);
        verify(promptControllerMock).prompt(PROMPT_MESSAGE_ONE_FEATURE_SELECT, Arrays.asList("1"));
        verify(promptControllerMock).prompt("What is the title of the merge request?", USED_MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteNoFeatureBranches() throws Exception {
        // set up
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("flow.featureBranchPrefix", "no-features/");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no feature branches in your repository.", null);
    }

    @Test
    public void testExecuteWithBranchName() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        prepareFeatureBranchForMergeRequest();
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID);
        userProperties.setProperty("branchName", FEATURE_BRANCH);
        when(promptControllerMock.prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID);
        verify(promptControllerMock).prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    private void prepareFeatureBranchForMergeRequest() throws GitAPIException, IOException {
        git.createAndCommitTestfile(repositorySet);
        git.push(repositorySet);
    }

    @Test
    public void testExecuteWithBranchNameNotFeature() throws Exception {
        // set up
        final String OTHER_BRANCH = "otherBranch";
        git.switchToBranch(repositorySet, MASTER_BRANCH);
        userProperties.setProperty("branchName", OTHER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result,
                "Branch '" + OTHER_BRANCH + "' defined in 'branchName' property is not a feature branch.",
                "Please define a feature branch in order to proceed.");
    }

    @Test
    public void testExecuteWithBranchNotExistingRemotely() throws Exception {
        // set up
        git.deleteRemoteBranch(repositorySet, FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote branch '" + FEATURE_BRANCH + "' doesn't exist.", null);
    }

    @Test
    public void testExecuteWithLocalBranchAhead() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + FEATURE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + FEATURE_BRANCH + "'");
    }

    @Test
    public void testExecuteWithLocalAndRemoteBranchesDiverge() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt", "REMOTE: test file");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Local and remote branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the remote changes into local branch and push it in order to proceed.",
                "'git pull' and 'git push " + FEATURE_BRANCH + "'");
    }

    @Test
    public void testExecuteWithLocalBranchAheadAndSelectedRemoteBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        userProperties.setProperty("branchName", "origin/" + FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Local branch is ahead of the remote branch '" + FEATURE_BRANCH + "'.",
                "Push commits made on local branch to the remote branch in order to proceed.",
                "'git push " + FEATURE_BRANCH + "'");
    }

    @Test
    public void testExecuteWithLocalAndRemoteBranchesDivergeAndSelectedRemoteBranch() throws Exception {
        // set up
        git.createAndCommitTestfile(repositorySet);
        git.remoteCreateTestfileInBranch(repositorySet, FEATURE_BRANCH, "remote_testfile.txt", "REMOTE: test file");
        userProperties.setProperty("branchName", "origin/" + FEATURE_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Local and remote branches '" + FEATURE_BRANCH + "' diverge.",
                "Rebase or merge the remote changes into local branch and push it in order to proceed.",
                "'git pull' and 'git push " + FEATURE_BRANCH + "'");
    }

    @Test
    public void testExecuteWithBaseBranchNotExistingRemotely() throws Exception {
        // set up
        git.deleteRemoteBranch(repositorySet, MASTER_BRANCH);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Remote base branch '" + MASTER_BRANCH + "' doesn't exist.", null);
    }

    @Test
    public void testExecuteWithNoChangesOnFeatureBranch() throws Exception {
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyZeroInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "There are no real changes in feature branch '" + FEATURE_BRANCH + "'.",
                "Delete the feature branch or commit some changes first.",
                "'mvn flow:feature-abort' to delete the feature branch",
                "'git add' and 'git commit' to commit some changes into feature branch and "
                        + "'mvn flow:feature-merge-request' to start the feature merge request again");
    }

    @Test
    public void testExecuteWithChangesOnBaseBranchAnswerNo() throws Exception {
        // set up
        prepareFeatureBranchForMergeRequest();
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "master_testfile.txt", "MASTER: test file");
        when(promptControllerMock.prompt(PROMPT_MR_WITH_BASE_BRANCH_AHEAD, Arrays.asList("y", "n"), "y"))
                .thenReturn("n");
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verify(promptControllerMock).prompt(PROMPT_MR_WITH_BASE_BRANCH_AHEAD, Arrays.asList("y", "n"), "y");
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "Starting of feature merge request aborted by user.", null);
    }

    @Test
    public void testExecuteWithChangesOnBaseBranchAnswerYes() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        prepareFeatureBranchForMergeRequest();
        git.remoteCreateTestfileInBranch(repositorySet, MASTER_BRANCH, "master_testfile.txt", "MASTER: test file");
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID);
        when(promptControllerMock.prompt(PROMPT_MR_WITH_BASE_BRANCH_AHEAD, Arrays.asList("y", "n"), "y"))
                .thenReturn("y");
        when(promptControllerMock.prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID);
        verify(promptControllerMock).prompt(PROMPT_MR_WITH_BASE_BRANCH_AHEAD, Arrays.asList("y", "n"), "y");
        verify(promptControllerMock).prompt("What is the title of the merge request?", MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithMergeRequestAlreadyExists() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final int EXPECTED_MERGE_REQUEST_ID = 43;
        prepareFeatureBranchForMergeRequest();
        staubForDefaultLogin();
        stubForGetProject(GITLAB_PROJECT, EXPECTED_PROJECT_ID);
        stubForGetMergeRequest(EXPECTED_PROJECT_ID, FEATURE_BRANCH, EXPECTED_MERGE_REQUEST_ID);
        // test
        MavenExecutionResult result = executeMojoWithResult(repositorySet.getWorkingDirectory(), GOAL, userProperties,
                promptControllerMock);
        // verify
        verifyRequestForDefaultLogin();
        verifyRequestForGetProject(GITLAB_PROJECT);
        verifyRequestForGetMergeRequest(EXPECTED_PROJECT_ID, FEATURE_BRANCH);
        verifyNoMoreInteractions(promptControllerMock);
        assertGitFlowFailureException(result, "An open MR for feature branch '" + FEATURE_BRANCH
                + "' already exists in GitLab\n" + createMergeRequestUrl(EXPECTED_MERGE_REQUEST_ID), null);
    }

    @Test
    public void testExecuteWithMergeRequestTitleTemplate() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + ")";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitleTemplate",
                "Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch})");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithMergeRequestTitle() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + ")";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitle",
                "Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch})");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithTitlePlaceholderAsMergeRequestTitleTemplateWithoutMergeRequestTitle() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + ")";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitleTemplate", "@{title}");
        when(promptControllerMock.prompt("What is the title of the merge request?"))
                .thenReturn("Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch})");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verify(promptControllerMock).prompt("What is the title of the merge request?");
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithTitlePlaceholderAsMergeRequestTitleTemplateWithMergeRequestTitle() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String USED_MERGE_REQUEST_TITLE = "Merge @{sourceBranch} into @{targetBranch}";
        final String USED_MERGE_REQUEST_TITLE_REPLACED = "Merge " + FEATURE_BRANCH + " into " + MASTER_BRANCH;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + ")";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitleTemplate", "@{title}");
        userProperties.setProperty("flow.mergeRequestTitle", USED_MERGE_REQUEST_TITLE);
        when(promptControllerMock.prompt("What is the title of the merge request?", USED_MERGE_REQUEST_TITLE_REPLACED))
                .thenReturn("Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch})");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verify(promptControllerMock).prompt("What is the title of the merge request?",
                USED_MERGE_REQUEST_TITLE_REPLACED);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithTitlePlaceholderAsMergeRequestTitleTemplateWithMergeRequestTitleSelectDefault()
            throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String USED_MERGE_REQUEST_TITLE = "Merge @{sourceBranch} into @{targetBranch}";
        final String USED_MERGE_REQUEST_TITLE_REPLACED = "Merge " + FEATURE_BRANCH + " into " + MASTER_BRANCH;
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, USED_MERGE_REQUEST_TITLE_REPLACED);
        userProperties.setProperty("flow.mergeRequestTitleTemplate", "@{title}");
        userProperties.setProperty("flow.mergeRequestTitle", USED_MERGE_REQUEST_TITLE);
        when(promptControllerMock.prompt("What is the title of the merge request?", USED_MERGE_REQUEST_TITLE_REPLACED))
                .thenReturn("");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, USED_MERGE_REQUEST_TITLE_REPLACED);
        verify(promptControllerMock).prompt("What is the title of the merge request?",
                USED_MERGE_REQUEST_TITLE_REPLACED);
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithTitlePlaceholderInMergeRequestTitleTemplateWithoutMergeRequestTitle() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + "): test feature";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitleTemplate",
                "Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch}): @{title}");
        when(promptControllerMock.prompt("What is the title of the merge request?")).thenReturn("test feature");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verify(promptControllerMock).prompt("What is the title of the merge request?");
        verifyNoMoreInteractions(promptControllerMock);
    }

    @Test
    public void testExecuteWithTitlePlaceholderInMergeRequestTitleTemplateWithMergeRequestTitle() throws Exception {
        // set up
        final int EXPECTED_PROJECT_ID = 42;
        final String EXPECTED_MERGE_REQUEST_TITLE = "Resolve feature " + FEATURE_ISSUE + " (merge " + FEATURE_BRANCH
                + " into " + MASTER_BRANCH + "): test feature";
        prepareFeatureBranchForMergeRequest();
        stubAllForDefaultMergerRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        userProperties.setProperty("flow.mergeRequestTitleTemplate",
                "Resolve feature @{key} (merge @{sourceBranch} into @{targetBranch}): @{title}");
        userProperties.setProperty("flow.mergeRequestTitle", "test feature");
        // test
        executeMojo(repositorySet.getWorkingDirectory(), GOAL, userProperties, promptControllerMock);
        // verify
        verifyAllForDefaultMergeRequest(EXPECTED_PROJECT_ID, EXPECTED_MERGE_REQUEST_TITLE);
        verifyNoMoreInteractions(promptControllerMock);
    }
}
