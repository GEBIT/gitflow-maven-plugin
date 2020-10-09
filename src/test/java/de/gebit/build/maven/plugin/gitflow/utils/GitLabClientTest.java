//
// GitLabClientTest.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.utils;

import static de.gebit.build.maven.plugin.gitflow.ExceptionAsserts.assertGitFlowFailureException;
import static de.gebit.build.maven.plugin.gitflow.ExceptionAsserts.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.agentproxy.AgentProxyException;

import de.gebit.build.maven.plugin.gitflow.GitFlowFailureException;
import de.gebit.build.maven.plugin.gitflow.utils.GitLabClient.GitLabConnector;
import de.gebit.build.maven.plugin.gitflow.utils.GitLabSSHClient.ClosableSession;

/**
 * @author Volodja
 */
public class GitLabClientTest {

    private static final List<String> SUPPORTED_HOSTS = Arrays.asList("testhost", "testhost.local.gebit.de");

    private static final String REMOTE_URL = "git@testhost.local.gebit.de:test/project.git";

    private static final String REMOTE_GITLAB = "https://testhost.local.gebit.de";

    private static final String REMOTE_GITLAB_HOST = "testhost.local.gebit.de";

    private static final String GITLAB_PROJECT = "test/project";

    private static final String PASSWORD = "password";

    private static final String USER_NAME = "userName";

    private static final String PERSONAL_TOKEN = "personal-Token";

    @Rule
    public MockitoRule mockitoRuel = MockitoJUnit.rule().silent();

    @Mock
    private GitLabConnector connectorMock;
    @Mock
    private GitLabApi gitLabApiMock;
    @Mock
    private ProjectApi projectApiMock;
    @Mock
    private MergeRequestApi mergeRequestApiMock;

    @Before
    public void setUp() throws Exception {
        when(connectorMock.connect(anyString(), anyString(), anyString())).thenReturn(gitLabApiMock);
        when(gitLabApiMock.getProjectApi()).thenReturn(projectApiMock);
        when(gitLabApiMock.getMergeRequestApi()).thenReturn(mergeRequestApiMock);
    }

    @Test
    public void testConstructor() throws Exception {
        // execute
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        // verify
        URIish uri = gitLab.getRemoteUri();
        assertNotNull(uri);
        assertEquals("testhost.local.gebit.de", uri.getHost());
        assertEquals("test/project.git", uri.getPath());
    }

    @Test
    public void testConstructorWithWrongURISyntax() throws Exception {
        final String MALFORMED_URL = "///";
        // execute
        Throwable exc = assertThrows(() -> new GitLabClient(MALFORMED_URL, SUPPORTED_HOSTS, connectorMock));
        // verify
        assertGitFlowFailureException(exc, "Remote git repository URL '" + MALFORMED_URL + "' can't be parsed.",
                "Please check if URL is correct or report the error in the GBLD JIRA.");
    }

    @Test
    public void testConstructorWithLocalURI() throws Exception {
        final String LOCAL_URL = "/a/b/c";
        // execute
        Throwable exc = assertThrows(() -> new GitLabClient(LOCAL_URL, SUPPORTED_HOSTS, connectorMock));
        // verify
        assertGitFlowFailureException(exc, "Remote git repository URL '" + LOCAL_URL
                + "' seems to be a local path and is not supported for GitLab interactions.", null);
    }

    @Test
    public void testConstructorWithUnsupportedHost() throws Exception {
        final String UNSUPPORTED_HOST_URL = "git@unsupported.host:test/project.git";
        // execute
        Throwable exc = assertThrows(() -> new GitLabClient(UNSUPPORTED_HOST_URL, SUPPORTED_HOSTS, connectorMock));
        // verify
        assertGitFlowFailureException(exc, "The host 'unsupported.host' of remote git repository '"
                + UNSUPPORTED_HOST_URL + "' is not supported.\nSupported hosts: [testhost, testhost.local.gebit.de]",
                null);
    }

    @Test
    public void testConnect() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, USER_NAME, PASSWORD)).thenReturn(gitLabApiMock);
        // execute
        gitLab.connect(USER_NAME, PASSWORD);
        // verify
        verify(tempConnectorMock).connect(REMOTE_GITLAB, USER_NAME, PASSWORD);
    }

    @Test
    public void testConnectHttp() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient("http://testhost.local.gebit.de/test/project.git", SUPPORTED_HOSTS,
                tempConnectorMock);
        when(tempConnectorMock.connect("http://testhost.local.gebit.de", USER_NAME, PASSWORD))
                .thenReturn(gitLabApiMock);
        // execute
        gitLab.connect(USER_NAME, PASSWORD);
        // verify
        verify(tempConnectorMock).connect("http://testhost.local.gebit.de", USER_NAME, PASSWORD);
    }

    @Test
    public void testConnectWithPort() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient("https://testhost.local.gebit.de:42/test/project.git", SUPPORTED_HOSTS,
                tempConnectorMock);
        when(tempConnectorMock.connect("https://testhost.local.gebit.de:42", USER_NAME, PASSWORD))
                .thenReturn(gitLabApiMock);
        // execute
        gitLab.connect(USER_NAME, PASSWORD);
        // verify
        verify(tempConnectorMock).connect("https://testhost.local.gebit.de:42", USER_NAME, PASSWORD);
    }

    @Test
    public void testConnectWithException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, USER_NAME, PASSWORD))
                .thenThrow(new GitLabApiException("dummy", 500));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connect(USER_NAME, PASSWORD));
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab", null);
    }

    @Test
    public void testConnectWithUnauthorizedException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, USER_NAME, PASSWORD))
                .thenThrow(new GitLabApiException("dummy", 401));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connect(USER_NAME, PASSWORD));
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab", "Please check your username and password.");
    }

    @Test
    public void testConnectWithPersonalToken() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenReturn(gitLabApiMock);
        // execute
        gitLab.connect(PERSONAL_TOKEN);
        // verify
        verify(tempConnectorMock).connect(REMOTE_GITLAB, PERSONAL_TOKEN);
    }

    @Test
    public void testConnectWithPersonalTokenAndException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenThrow(new GitLabApiException("dummy", 500));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connect(PERSONAL_TOKEN));
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab with personal token.", null);
    }

    @Test
    public void testConnectWithPersonalTokenAndUnauthorizedException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenThrow(new GitLabApiException("dummy", 401));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connect(PERSONAL_TOKEN));
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab with personal token.", "Please check your personal token.");
    }

    @Test
    public void testConnectWithSSHKey() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabSSHClient tempSSHClientMock = mock(GitLabSSHClient.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock, tempSSHClientMock);
        when(tempSSHClientMock.connect(REMOTE_GITLAB_HOST)).thenReturn(mock(ClosableSession.class));
        when(tempSSHClientMock.createPersonalToken()).thenReturn(PERSONAL_TOKEN);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenReturn(gitLabApiMock);
        // execute
        gitLab.connectWithSSHKey();
        // verify
        verify(tempSSHClientMock).connect(REMOTE_GITLAB_HOST);
        verify(tempSSHClientMock).createPersonalToken();
        verify(tempConnectorMock).connect(REMOTE_GITLAB, PERSONAL_TOKEN);
    }

    @Test
    public void testConnectWithSSHKeyAndException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabSSHClient tempSSHClientMock = mock(GitLabSSHClient.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock, tempSSHClientMock);
        when(tempSSHClientMock.connect(REMOTE_GITLAB_HOST)).thenReturn(mock(ClosableSession.class));
        when(tempSSHClientMock.createPersonalToken()).thenReturn(PERSONAL_TOKEN);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenThrow(new GitLabApiException("dummy", 500));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connectWithSSHKey());
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab with personal token.", null);
    }

    @Test
    public void testConnectWithSSHKeyAndUnauthorizedException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabSSHClient tempSSHClientMock = mock(GitLabSSHClient.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock, tempSSHClientMock);
        when(tempSSHClientMock.connect(REMOTE_GITLAB_HOST)).thenReturn(mock(ClosableSession.class));
        when(tempSSHClientMock.createPersonalToken()).thenReturn(PERSONAL_TOKEN);
        when(tempConnectorMock.connect(REMOTE_GITLAB, PERSONAL_TOKEN)).thenThrow(new GitLabApiException("dummy", 401));
        // execute
        Throwable exc = assertThrows(() -> gitLab.connectWithSSHKey());
        // verify
        assertGitFlowFailureException(exc, "Failed to login to GitLab with personal token.", "Please check your personal token.");
    }

    @Test
    public void testConnectWithSSHKeyAndSSHException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabSSHClient tempSSHClientMock = mock(GitLabSSHClient.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock, tempSSHClientMock);
        when(tempSSHClientMock.connect(REMOTE_GITLAB_HOST)).thenThrow(JSchException.class);
        // execute
        Throwable exc = assertThrows(() -> gitLab.connectWithSSHKey());
        // verify
        assertGitFlowFailureException(exc,
                "Failed to login to GitLab using ssh key.\nReason: failed to create personal token via ssh", null);
    }

    @Test
    public void testConnectWithSSHKeyAndSSHAgentException() throws Exception {
        // set up
        GitLabConnector tempConnectorMock = mock(GitLabConnector.class);
        GitLabSSHClient tempSSHClientMock = mock(GitLabSSHClient.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, tempConnectorMock, tempSSHClientMock);
        when(tempSSHClientMock.connect(REMOTE_GITLAB_HOST)).thenThrow(AgentProxyException.class);
        // execute
        Throwable exc = assertThrows(() -> gitLab.connectWithSSHKey());
        // verify
        assertGitFlowFailureException(exc,
                "Failed to login to GitLab using ssh key.\nReason: failed to create personal token via ssh", null);
    }

    @Test
    public void testGetProject() throws Exception {
        // set up
        Project projectMock = mock(Project.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(projectMock);
        // execute
        Project project = gitLab.getProject();
        // verify
        verify(projectApiMock).getProject(GITLAB_PROJECT);
        assertSame(projectMock, project);
    }

    @Test
    public void testGetProjectTwice() throws Exception {
        // set up
        Project projectMock = mock(Project.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(projectMock);
        // execute
        Project project1 = gitLab.getProject();
        Project project2 = gitLab.getProject();
        // verify
        verify(projectApiMock).getProject(GITLAB_PROJECT);
        assertSame(projectMock, project1);
        assertSame(projectMock, project2);
    }

    @Test
    public void testGetProjectNotFound() throws Exception {
        // set up
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(null);
        // execute
        Throwable exc = assertThrows(() -> gitLab.getProject());
        // verify
        verify(projectApiMock).getProject(GITLAB_PROJECT);
        assertGitFlowFailureException(exc, "Failed to find project '" + GITLAB_PROJECT + "' on GitLab.",
                "Check if project exists on GitLab and you have access to it.");
    }

    @Test
    public void testGetProjectWithException() throws Exception {
        // set up
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenThrow(GitLabApiException.class);
        // execute
        Throwable exc = assertThrows(() -> gitLab.getProject());
        // verify
        verify(projectApiMock).getProject(GITLAB_PROJECT);
        assertGitFlowFailureException(exc, "Failed to find project '" + GITLAB_PROJECT + "' on GitLab.",
                "Check if project exists on GitLab and you have access to it.");
    }

    @Test
    public void testGetMergeRequest() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        MergeRequest mergeRequestMock = mock(MergeRequest.class);
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestFilter expectedFilter = mergeRequestFitler(PROJECT_ID, SOURCE_BRANCH);
        when(mergeRequestApiMock.getMergeRequests(refEq(expectedFilter)))
                .thenReturn(Collections.singletonList(mergeRequestMock));
        // execute
        MergeRequest mergeRequest = gitLab.getMergeRequest(SOURCE_BRANCH);
        // verify
        verify(mergeRequestApiMock).getMergeRequests(refEq(expectedFilter));
        assertSame(mergeRequestMock, mergeRequest);
    }

    private GitLabClient createAndConnectGitLabClient(int projectId)
            throws GitFlowFailureException, GitLabApiException {
        Project projectMock = mock(Project.class);
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(projectMock);
        when(projectMock.getId()).thenReturn(projectId);
        return gitLab;
    }

    private MergeRequestFilter mergeRequestFitler(int projectId, String sourceBranch) {
        MergeRequestFilter expectedFilter = new MergeRequestFilter();
        expectedFilter.setProjectId(projectId);
        expectedFilter.setState(MergeRequestState.OPENED);
        expectedFilter.setSourceBranch(sourceBranch);
        return expectedFilter;
    }

    @Test
    public void testGetMergeRequestEmtpy() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestFilter expectedFilter = mergeRequestFitler(PROJECT_ID, SOURCE_BRANCH);
        when(mergeRequestApiMock.getMergeRequests(refEq(expectedFilter))).thenReturn(Collections.EMPTY_LIST);
        // execute
        MergeRequest mergeRequest = gitLab.getMergeRequest(SOURCE_BRANCH);
        // verify
        verify(mergeRequestApiMock).getMergeRequests(refEq(expectedFilter));
        assertNull(mergeRequest);
    }

    @Test
    public void testGetMergeRequestNull() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestFilter expectedFilter = mergeRequestFitler(PROJECT_ID, SOURCE_BRANCH);
        when(mergeRequestApiMock.getMergeRequests(refEq(expectedFilter))).thenReturn(null);
        // execute
        MergeRequest mergeRequest = gitLab.getMergeRequest(SOURCE_BRANCH);
        // verify
        verify(mergeRequestApiMock).getMergeRequests(refEq(expectedFilter));
        assertNull(mergeRequest);
    }

    @Test
    public void testGetMergeRequestTwoMRs() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        MergeRequest mergeRequestMock1 = mock(MergeRequest.class);
        MergeRequest mergeRequestMock2 = mock(MergeRequest.class);
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestFilter expectedFilter = mergeRequestFitler(PROJECT_ID, SOURCE_BRANCH);
        when(mergeRequestApiMock.getMergeRequests(refEq(expectedFilter)))
                .thenReturn(Arrays.asList(mergeRequestMock1, mergeRequestMock2));
        // execute
        MergeRequest mergeRequest = gitLab.getMergeRequest(SOURCE_BRANCH);
        // verify
        verify(mergeRequestApiMock).getMergeRequests(refEq(expectedFilter));
        assertSame(mergeRequestMock1, mergeRequest);
    }

    @Test
    public void testGetMergeRequestWithException() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestFilter expectedFilter = mergeRequestFitler(PROJECT_ID, SOURCE_BRANCH);
        when(mergeRequestApiMock.getMergeRequests(refEq(expectedFilter))).thenThrow(GitLabApiException.class);
        // execute
        Throwable exc = assertThrows(() -> gitLab.getMergeRequest(SOURCE_BRANCH));
        // verify
        verify(mergeRequestApiMock).getMergeRequests(refEq(expectedFilter));
        assertGitFlowFailureException(exc, "Failed to search for merge requests in GitLab", null);
    }

    @Test
    public void testGetMergeRequestWithProjectNotFound() throws Exception {
        // set up
        final String SOURCE_BRANCH = "feature/TST-42";
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(null);
        // execute
        Throwable exc = assertThrows(() -> gitLab.getMergeRequest(SOURCE_BRANCH));
        // verify
        verifyZeroInteractions(mergeRequestApiMock);
        assertGitFlowFailureException(exc, "Failed to find project '" + GITLAB_PROJECT + "' on GitLab.",
                "Check if project exists on GitLab and you have access to it.");
    }

    @Test
    public void testCreateMergeRequest() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        final String TARGET_BRANCH = "baseBranch";
        final String TITLE = "Merge TST-42";
        MergeRequest mergeRequestMock = mock(MergeRequest.class);
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestParams expectedMRParams = mergeRequestParams(SOURCE_BRANCH, TARGET_BRANCH, TITLE);
        when(mergeRequestApiMock.createMergeRequest(eq(PROJECT_ID), refEq(expectedMRParams)))
                .thenReturn(mergeRequestMock);
        // execute
        MergeRequest mergeRequest = gitLab.createMergeRequest(SOURCE_BRANCH, TARGET_BRANCH, TITLE);
        // verify
        verify(mergeRequestApiMock).createMergeRequest(eq(PROJECT_ID), refEq(expectedMRParams));
        assertSame(mergeRequestMock, mergeRequest);
    }

    private MergeRequestParams mergeRequestParams(String sourceBranch, String targetBranch, String title) {
        return new MergeRequestParams().withSourceBranch(sourceBranch).withTargetBranch(targetBranch).withTitle(title);
    }

    @Test
    public void testCreateMergeRequestWithException() throws Exception {
        // set up
        final int PROJECT_ID = 42;
        final String SOURCE_BRANCH = "feature/TST-42";
        final String TARGET_BRANCH = "baseBranch";
        final String TITLE = "Merge TST-42";
        GitLabClient gitLab = createAndConnectGitLabClient(PROJECT_ID);
        MergeRequestParams expectedMRParams = mergeRequestParams(SOURCE_BRANCH, TARGET_BRANCH, TITLE);
        when(mergeRequestApiMock.createMergeRequest(eq(PROJECT_ID), refEq(expectedMRParams)))
                .thenThrow(GitLabApiException.class);
        // execute
        Throwable exc = assertThrows(() -> gitLab.createMergeRequest(SOURCE_BRANCH, TARGET_BRANCH, TITLE));
        // verify
        verify(mergeRequestApiMock).createMergeRequest(eq(PROJECT_ID), refEq(expectedMRParams));
        assertGitFlowFailureException(exc, "Failed to create a merge request in GitLab", null);
    }

    @Test
    public void testCreateMergeRequestWithProjectNotFound() throws Exception {
        // set up
        final String SOURCE_BRANCH = "feature/TST-42";
        final String TARGET_BRANCH = "baseBranch";
        final String TITLE = "Merge TST-42";
        GitLabClient gitLab = new GitLabClient(REMOTE_URL, SUPPORTED_HOSTS, connectorMock);
        gitLab.connect(USER_NAME, PASSWORD);
        when(projectApiMock.getProject(GITLAB_PROJECT)).thenReturn(null);
        // execute
        Throwable exc = assertThrows(() -> gitLab.createMergeRequest(SOURCE_BRANCH, TARGET_BRANCH, TITLE));
        // verify
        verifyZeroInteractions(mergeRequestApiMock);
        assertGitFlowFailureException(exc, "Failed to find project '" + GITLAB_PROJECT + "' on GitLab.",
                "Check if project exists on GitLab and you have access to it.");
    }
}
