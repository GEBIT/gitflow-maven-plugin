//
// GitLabClient.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.lang3.StringUtils;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.MergeRequestParams;
import org.gitlab4j.api.models.Project;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.agentproxy.AgentProxyException;

import de.gebit.build.maven.plugin.gitflow.GitFlowFailureException;
import de.gebit.build.maven.plugin.gitflow.utils.GitLabSSHClient.ClosableSession;

/**
 * GitLab client for communication with GitLab via REST API.
 * 
 * @author Volodja
 */
public class GitLabClient {

    private GitLabConnector connector;
    private GitLabSSHClient sshClient;
    private URIish remoteUri;
    private GitLabApi gitLab;
    private Project gitProject;

    /**
     * Creates GitLab client for a remote git repository.
     * 
     * @param aRemoteUrl
     *            URL of remote git repository configured in git config.
     * @param someSupportedGitLabHosts
     *            List of GitLab hosts that are supported.
     * @throws GitFlowFailureException
     */
    public GitLabClient(String aRemoteUrl, List<String> someSupportedGitLabHosts) throws GitFlowFailureException {
        this(aRemoteUrl, someSupportedGitLabHosts, new GitLabConnector());
    }

    GitLabClient(String aRemoteUrl, List<String> someSupportedGitLabHosts, GitLabConnector aConnector)
            throws GitFlowFailureException {
        this(aRemoteUrl, someSupportedGitLabHosts, aConnector, new GitLabSSHClient());
    }

    GitLabClient(String aRemoteUrl, List<String> someSupportedGitLabHosts, GitLabConnector aConnector,
            GitLabSSHClient aSshClient) throws GitFlowFailureException {
        connector = aConnector;
        sshClient = aSshClient;
        init(aRemoteUrl, someSupportedGitLabHosts);
    }

    private void init(String aRemoteUrl, List<String> someSupportedGitLabHosts) throws GitFlowFailureException {
        try {
            remoteUri = new URIish(aRemoteUrl);
        } catch (URISyntaxException exc) {
            throw new GitFlowFailureException("Remote git repository URL '" + aRemoteUrl + "' can't be parsed.",
                    "Please check if URL is correct or report the error in the GBLD JIRA.");
        }
        String uriHost = remoteUri.getHost();
        if (uriHost == null) {
            throw new GitFlowFailureException("Remote git repository URL '" + aRemoteUrl
                    + "' seems to be a local path and is not supported for GitLab interactions.", null);
        }
        if (!someSupportedGitLabHosts.contains(uriHost.toLowerCase())) {
            throw new GitFlowFailureException("The host '" + uriHost + "' of remote git repository '" + aRemoteUrl
                    + "' is not supported.\n" + "Supported hosts: " + someSupportedGitLabHosts, null);
        }
    }

    /**
     * Gets parsed remote git repository URI.
     * 
     * @return Parsed remote git repository URI.
     */
    public URIish getRemoteUri() {
        return remoteUri;
    }

    /**
     * Connects to GitLab using ssh key.<br>
     * Tries to create personal token via ssh and connects to GitLab using
     * created personal token.
     * 
     * @throws GitFlowFailureException
     */
    public void connectWithSSHKey() throws GitFlowFailureException {
        String personalToken;
        try (ClosableSession session = sshClient.connect(remoteUri.getHost())) {
            personalToken = sshClient.createPersonalToken();
        } catch (JSchException | IOException | AgentProxyException exc) {
            throw new GitFlowFailureException(exc,
                    "Failed to login to GitLab using ssh key.\nReason: failed to create personal token via ssh", null);
        }
        connect(personalToken);
    }

    /**
     * Connects to GitLab using ssh key.<br>
     * Tries to create personal token via ssh and connects to GitLab using
     * created personal token.
     * 
     * @throws GitFlowFailureException
     */
    public void connect(String aPersonalToken) throws GitFlowFailureException {
        String gitLabUrl = getGitLabUrl();
        try {
            gitLab = connector.connect(gitLabUrl, aPersonalToken);
        } catch (GitLabApiException exc) {
            boolean unauthorized = exc.getHttpStatus() == HttpsURLConnection.HTTP_UNAUTHORIZED;
            throw new GitFlowFailureException(exc, "Failed to login to GitLab with personal token.",
                    unauthorized ? "Please check your personal token." : null);
        }
    }

    /**
     * Connects to GitLab using username and password.
     * 
     * @param aUserName
     *            GitLab user name.
     * @param aUserPass
     *            GitLab password.
     * @throws GitFlowFailureException
     */
    public void connect(String aUserName, String aUserPass) throws GitFlowFailureException {
        String gitLabUrl = getGitLabUrl();
        try {
            gitLab = connector.connect(gitLabUrl, aUserName, aUserPass);
        } catch (GitLabApiException exc) {
            boolean unauthorized = exc.getHttpStatus() == HttpsURLConnection.HTTP_UNAUTHORIZED;
            throw new GitFlowFailureException(exc, "Failed to login to GitLab",
                    unauthorized ? "Please check your username and password." : null);
        }
    }

    private String getGitLabUrl() {
        StringBuilder url = new StringBuilder();
        if ("http".equals(remoteUri.getScheme())) {
            url.append("http");
        } else {
            url.append("https");
        }
        url.append("://");
        url.append(remoteUri.getHost());
        if (remoteUri.getPort() > 0) {
            url.append(":");
            url.append(remoteUri.getPort());
        }
        return url.toString();
    }

    /**
     * Gets the first opened merge request for passed source branch.
     * 
     * @param aBranchName
     *            Source branch name
     * @return The first opened merge request or <code>null</code> if no opened
     *         merge requests found.
     * @throws GitFlowFailureException
     *             if GitLab project can't be found or any exception occurs
     */
    public MergeRequest getMergeRequest(String aBranchName) throws GitFlowFailureException {
        Project project = getProject();
        MergeRequestApi mergeRequestApi = gitLab.getMergeRequestApi();
        MergeRequestFilter mrFilter = new MergeRequestFilter();
        mrFilter.setSourceBranch(aBranchName);
        mrFilter.setState(MergeRequestState.OPENED);
        mrFilter.setProjectId(project.getId());
        List<MergeRequest> mrs;
        try {
            mrs = mergeRequestApi.getMergeRequests(mrFilter);
        } catch (GitLabApiException exc) {
            throw new GitFlowFailureException(exc, "Failed to search for merge requests in GitLab", null);
        }
        if (mrs == null || mrs.isEmpty()) {
            return null;
        } else {
            return mrs.get(0);
        }
    }

    /**
     * Gets the GitLab project for current git repository.
     * 
     * @return The git project.
     * @throws GitFlowFailureException
     *             if project can't be found or any exception occurs
     */
    public Project getProject() throws GitFlowFailureException {
        if (gitProject == null) {
            String projectPath = StringUtils.removeEnd(remoteUri.getPath(), ".git");
            ProjectApi projectApi = gitLab.getProjectApi();
            try {
                gitProject = projectApi.getProject(projectPath);
            } catch (GitLabApiException exc) {
                throw new GitFlowFailureException(exc, "Failed to find project '" + projectPath + "' on GitLab.",
                        "Check if project exists on GitLab and you have access to it.");
            }
            if (gitProject == null) {
                throw new GitFlowFailureException("Failed to find project '" + projectPath + "' on GitLab.",
                        "Check if project exists on GitLab and you have access to it.");
            }
        }
        return gitProject;
    }

    /**
     * Creates a merge request.
     * 
     * @param aSourceBranch
     *            Source branch of the merge request.
     * @param aTargetBranch
     *            Target branch of the merge request.
     * @param aTitle
     *            Merge request title.
     * @return Created merge request.
     * @throws GitFlowFailureException
     *             if GitLab project can't be found or any exception occurs
     */
    public MergeRequest createMergeRequest(String aSourceBranch, String aTargetBranch, String aTitle)
            throws GitFlowFailureException {
        Project project = getProject();
        MergeRequestApi mergeRequestApi = gitLab.getMergeRequestApi();
        MergeRequestParams mrParams = new MergeRequestParams();
        mrParams.withSourceBranch(aSourceBranch);
        mrParams.withTargetBranch(aTargetBranch);
        mrParams.withTitle(aTitle);
        try {
            return mergeRequestApi.createMergeRequest(project.getId(), mrParams);
        } catch (GitLabApiException exc) {
            throw new GitFlowFailureException(exc, "Failed to create a merge request in GitLab", null);
        }
    }

    static class GitLabConnector {
        public GitLabApi connect(String aURL, String aUserName, String aUserPass) throws GitLabApiException {
            return GitLabApi.oauth2Login(aURL, aUserName, aUserPass);
        }

        public GitLabApi connect(String aURL, String aPersonalToken) throws GitLabApiException {
            GitLabApi api = new GitLabApi(aURL, aPersonalToken);
            api.getVersion();
            return api;
        }
    }

}
