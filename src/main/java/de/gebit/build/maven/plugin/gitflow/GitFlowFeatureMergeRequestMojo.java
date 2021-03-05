//
// GitFlowFeatureMergeRequestMojo.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.gitlab4j.api.models.MergeRequest;

import de.gebit.build.maven.plugin.gitflow.utils.GitLabClient;

/**
 * Create a merge request for the feature branch in GitLab.
 * <p>
 * Create a merge request in order to review the implementation before feature
 * finish.
 * </p>
 * Example:
 *
 * <pre>
 * mvn flow:feature-merge-request -N [-Dflow.draft=true|false] [-D...]
 * </pre>
 * 
 * @see GitFlowFeatureStartMojo
 * @see GitFlowFeatureFinishMojo
 * @author Volodja
 * @since 2.3.0
 */
@Mojo(name = GitFlowFeatureMergeRequestMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureMergeRequestMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-merge-request";

    private static final String DEFAULT_MERGE_REQUEST_TITLE = "Resolve feature @{key}";

    /**
     * The feature branch which a merge request should be created for.
     */
    @Parameter(property = "branchName")
    private String branchName;

    /**
     * List of supported GitLab hosts where merge request can be started.
     */
    @Parameter(property = "flow.supportedGitLabHosts", defaultValue = "gitlab,gitlab.local.gebit.de")
    private List<String> supportedGitLabHosts;

    /**
     * The URL of the GitLab project that should be used instead of the URL from
     * git remote configuration.
     */
    @Parameter(property = "flow.gitLabProjectUrl")
    private String gitLabProjectUrl;

    /**
     * Whether to try to login to GitLab using personal token created via ssh.
     * Supported only since GitLab 13.4.
     */
    @Parameter(property = "flow.gitLabLoginBySSHKey", defaultValue = "true")
    private boolean gitLabLoginBySSHKey;

    /**
     * The value for the <code>@{interactiveTitlePart}</code> placeholder in the
     * <code>mergeRequestTitle</code>. Only specify this when interactive
     * prompting is not desired.
     */
    @Parameter(property = "flow.mergeRequestInteractiveTitlePart")
    private String mergeRequestInteractiveTitlePart;

    /**
     * The template to be used for defining the title of a merge request.
     * <p>
     * Supported placeholders (all optional):
     * <li>@{interactiveTitlePart} - the value of
     * <code>mergeRequestInteractiveTitlePart</code> (if set); otherwise
     * interactively asked from the user</li>
     * <li>@{key} - Jira issue key of the feature (e.g.
     * <code>ABC-42</code>)</li>
     * <li>@{sourceBranch} - source branch of the MR (e.g.
     * <code>feature/ABC-42-description</code>)</li>
     * <li>@{targetBranch} - target branch of the MR (e.g.
     * <code>master</code>)</li>
     * </p>
     */
    @Parameter(property = "flow.mergeRequestTitle")
    private String mergeRequestTitle;

    /**
     * Mark merge request as draft. Corresponding prefix (e.g. [Draft]) will be
     * add to the merge request title.
     */
    @Parameter(property = "flow.draft", defaultValue = "false")
    private boolean draft;

    /**
     * Prefix for merge request title if the merge request is marked as
     * draft.<br/>
     * GitLab supports following prefixes: "[Draft]", "Draft:" or
     * "(Draft)".<br/>
     * Note: GitLab will remove support for prefix "WIP:" in version 14.0.
     */
    @Parameter(property = "flow.draftTitlePrefix", defaultValue = "[Draft]")
    private String draftTitlePrefix;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature merge request process");

        GitLabClient gitLab = createGitLabClient(gitLabProjectUrl, supportedGitLabHosts);

        checkCentralBranchConfig();

        String featureBranchLocalName = selectFeatureBranch();
        gitAssertRemoteBranchUpToDate(featureBranchLocalName);

        String baseBranchLocalName = gitFeatureBranchBaseBranchName(featureBranchLocalName);
        getMavenLog().info("Base branch of feature branch is '" + baseBranchLocalName + "'");
        if (!gitRemoteBranchExists(baseBranchLocalName)) {
            throw new GitFlowFailureException(
                    createBranchNotExistingError("Remote base branch '" + baseBranchLocalName + "'", null));
        }
        String featureBranchRemoteName = gitLocalToRemoteRef(featureBranchLocalName);
        String baseBranchRemoteName = gitLocalToRemoteRef(baseBranchLocalName);
        if (!hasCommitsExceptVersionChangeCommitOnFeatureBranch(featureBranchRemoteName, baseBranchRemoteName)) {
            throw new GitFlowFailureException(
                    "There are no real changes in feature branch '" + featureBranchLocalName + "'.",
                    "Delete the feature branch or commit some changes first.",
                    "'mvn flow:feature-abort' to delete the feature branch",
                    "'git add' and 'git commit' to commit some changes into feature branch and "
                            + "'mvn flow:feature-merge-request' to start the feature merge request again");
        }
        if (!gitIsAncestorBranch(baseBranchRemoteName, featureBranchRemoteName)) {
            if (!getPrompter().promptConfirmation("Base branch '" + baseBranchLocalName
                    + "' has changes that are not yet included in feature branch '" + featureBranchLocalName
                    + "'. Do you want to start merge request anyway?", true, true)) {
                throw new GitFlowFailureException("Starting of feature merge request aborted by user.", null);
            }
        }

        if (!tryToConnectWithSSHKey(gitLab)) {
            connectWithCredentials(gitLab);
        }

        MergeRequest mr = gitLab.getMergeRequest(featureBranchLocalName);
        if (mr != null) {
            throw new GitFlowFailureException("An open MR for feature branch '" + featureBranchLocalName
                    + "' already exists in GitLab\n" + mr.getWebUrl(), null);
        }
        mr = gitLab.createMergeRequest(featureBranchLocalName, baseBranchLocalName,
                createMRTitle(featureBranchLocalName, baseBranchLocalName));
        getMavenLog().info("Feature merge request was successfully created: " + mr.getWebUrl());

        getMavenLog().info("Feature merge request process finished");
    }

    private void connectWithCredentials(GitLabClient gitLab) throws GitFlowFailureException {
        ensureInteractiveMode();
        String userName = null;
        String userPass = null;
        try {
            userName = getPrompter().promptValue("Enter GitLab user name", System.getProperty("user.name"));
            userPass = getPrompter().promptForPassword("Enter GitLab user password");
        } catch (PrompterException exc) {
            throw new GitFlowFailureException(exc, "Failed to get value from user prompt", null);
        }
        gitLab.connect(userName, userPass);
    }

    private boolean tryToConnectWithSSHKey(GitLabClient gitLab) {
        if (gitLabLoginBySSHKey) {
            try {
                gitLab.connectWithSSHKey();
                return true;
            } catch (GitFlowFailureException exc) {
                getMavenLog().warn("Failed to connect to GitLab using ssh key.");
                getLog().info(exc);
            }
        }
        return false;
    }

    private String createMRTitle(String sourceBranch, String targetBranch)
            throws MojoFailureException, CommandLineException {
        String issueNumber = getFeatureIssueNumber(sourceBranch);
        String template;
        if (StringUtils.isEmpty(mergeRequestTitle)) {
            template = getPrompter().promptRequiredParameterValue("What is the title of the merge request?",
                    "mergeRequestInteractiveTitlePart", mergeRequestInteractiveTitlePart,
                    substituteMRTitle(DEFAULT_MERGE_REQUEST_TITLE, sourceBranch, targetBranch, issueNumber));
        } else if (mergeRequestTitle.equals("@{interactiveTitlePart}")) {
            template = getPrompter().promptRequiredParameterValue("What is the title of the merge request?",
                    "mergeRequestInteractiveTitlePart", null,
                    substituteMRTitle(mergeRequestInteractiveTitlePart, sourceBranch, targetBranch, issueNumber));
        } else if (mergeRequestTitle.contains("@{interactiveTitlePart}")) {
            template = substituteMRTitle(mergeRequestTitle, sourceBranch, targetBranch, issueNumber,
                    Collections.singletonMap("interactiveTitlePart", "<additional details>"));
            String titleTemplate = getPrompter().promptOptionalParameterValue(
                    "Merge request title pattern being used:\n  " + prependDraftIfNeeded(template)
                            + "\nPlease specify additional details for the merge request title",
                    "mergeRequestInteractiveTitlePart", mergeRequestInteractiveTitlePart);
            template = substituteMRTitle(mergeRequestTitle, sourceBranch, targetBranch, issueNumber,
                    Collections.singletonMap("interactiveTitlePart", (titleTemplate == null) ? "" : titleTemplate));
        } else {
            template = mergeRequestTitle;
        }
        return prependDraftIfNeeded(substituteMRTitle(template, sourceBranch, targetBranch, issueNumber));
    }

    private String prependDraftIfNeeded(String title) {
        if (draft && StringUtils.isNotBlank(draftTitlePrefix)) {
            return draftTitlePrefix + " " + title;
        }
        return title;
    }

    private String substituteMRTitle(String template, String sourceBranch, String targetBranch, String issueNumber)
            throws MojoFailureException {
        return substituteMRTitle(template, sourceBranch, targetBranch, issueNumber, null);
    }

    private String substituteMRTitle(String template, String sourceBranch, String targetBranch, String issueNumber,
            Map<String, String> additionalReplacements) throws MojoFailureException {
        if (template == null) {
            return null;
        }
        Map<String, String> replacements = new HashMap<>();
        replacements.put("sourceBranch", sourceBranch);
        replacements.put("targetBranch", targetBranch);
        replacements.put("key", issueNumber);
        if (additionalReplacements != null) {
            replacements.putAll(additionalReplacements);
        }
        return substituteStrings(template, replacements);
    }

    private void ensureInteractiveMode() throws GitFlowFailureException {
        if (!settings.isInteractiveMode()) {
            throw new GitFlowFailureException(
                    "'mvn flow:feature-merge-request' can't be executed in non-interactive mode if ssh is not properly "
                            + "set up.",
                    "Please set up ssh using GEBIT Installer (contact DINF team if support needed) or create the "
                            + "feature merge request in interactive mode.",
                    "'mvn flow:feature-merge-request' to run in interactive mode");
        }
    }

    private String selectFeatureBranch() throws GitFlowFailureException, MojoFailureException, CommandLineException {
        String featureBranchLocalName;
        if (StringUtils.isNotEmpty(branchName)) {
            featureBranchLocalName = gitLocalRef(branchName);
            if (!isFeatureBranch(featureBranchLocalName)) {
                throw new GitFlowFailureException(
                        "Branch '" + branchName + "' defined in 'branchName' property is not a feature branch.",
                        "Please define a feature branch in order to proceed.");
            }
            getLog().info("Creating merge request for specified feature branch: " + featureBranchLocalName);
        } else {
            String currentBranch = gitCurrentBranch();
            if (isFeatureBranch(currentBranch)) {
                featureBranchLocalName = currentBranch;
                getLog().info("Creating merge request for current feature branch: " + featureBranchLocalName);
            } else {
                List<String> branches = gitAllFeatureBranches();
                if (branches.isEmpty()) {
                    throw new GitFlowFailureException("There are no feature branches in your repository.", null);
                }
                featureBranchLocalName = getPrompter().promptToSelectFromOrderedList("Feature branches:",
                        "Choose feature branch which to create merge request for", branches,
                        new GitFlowFailureInfo(
                                "In non-interactive mode 'mvn flow:feature-merge-request' can be executed only on a "
                                        + "feature branch.",
                                "Please switch to a feature branch first or run in interactive mode.",
                                "'git checkout BRANCH' to switch to the feature branch",
                                "'mvn flow:feature-merge-request' to run in interactive mode"));
                getLog().info("Creating merge request for selected feature branch: " + featureBranchLocalName);
            }
        }
        return featureBranchLocalName;
    }

}
