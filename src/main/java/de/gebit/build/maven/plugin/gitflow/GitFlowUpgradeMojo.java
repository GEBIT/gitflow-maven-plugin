//
// GitFlowUpgradeMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.ExtendedPrompter.SelectOption;

/**
 * Check if repository meta data like central branch config etc. conforms the
 * current version of gitflow and upgrade it if necessary.
 *
 * @author Volodymyr Medvid
 * @since 2.1.0
 */
@Mojo(name = "upgrade", aggregator = true)
public class GitFlowUpgradeMojo extends AbstractGitFlowMojo {

    @Parameter(property = "featureNamePattern")
    protected String featureNamePattern;

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        checkOrInitCentralBranchConfig();
    }

    private void checkOrInitCentralBranchConfig() throws MojoFailureException, CommandLineException {
        String configBranchVersion = gitGetBranchCentralConfig(configBranchName, "version");
        if (!BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION.equals(configBranchVersion)) {
            if (configBranchVersion == null) {
                getLog().info("Initializing central branch config on version "
                        + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION + ".");
            } else {
                getLog().info("Upgrading central branch config from version " + configBranchVersion + " to version "
                        + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION + ".");
            }
            reinitCentralBranchConfig();
            if (configBranchVersion == null) {
                getLog().info("Central branch config initialized on version "
                        + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION + ".");
            } else {
                getLog().info("Central branch config upgraded from version " + configBranchVersion + " to version "
                        + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION + ".");
            }
        } else {
            getLog().info("Central branch config is up to date (version: "
                    + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION + ").");
        }
    }

    private void reinitCentralBranchConfig() throws MojoFailureException, CommandLineException {
        BranchCentralConfigChanges changes = new BranchCentralConfigChanges();
        gitFetchOnce();
        CentralBranchConfigCache configCache = getCentralBranchConfigCache();
        List<String> branches = gitAllBranches("");
        for (String branch : branches) {
            Properties properties = configCache.getProperties(branch);
            if (properties == null) {
                properties = new Properties();
            }
            if (isFeatureBranch(branch)) {
                collectMissingFeatureBranchConfigs(changes, branch, properties);
            }
            if (isEpicBranch(branch)) {
                collectMissingEpicBranchConfigs(changes, branch, properties);
            }
        }
        changes.set(configBranchName, "version", BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION);
        gitApplyBranchCentralConfigChanges(changes,
                "init on version " + BranchConfigKeys.CENTRAL_BRANCH_CONFIG_VERSION);
    }

    private void collectMissingFeatureBranchConfigs(BranchCentralConfigChanges changes, String featureBranch,
            Properties properties) throws MojoFailureException, CommandLineException {
        getLog().info("Configuring feature branch '" + featureBranch + "'...");
        String featureBranchRef = featureBranch;
        if (!gitBranchExists(featureBranch)) {
            featureBranchRef = gitFlowConfig.getOrigin() + "/" + featureBranch;
        }
        Properties tmpChanges = new Properties();
        if (!properties.containsKey(BranchConfigKeys.BRANCH_TYPE)) {
            tmpChanges.setProperty(BranchConfigKeys.BRANCH_TYPE, BranchType.FEATURE.getType());
        }
        String baseBranch;
        if (!properties.containsKey(BranchConfigKeys.BASE_BRANCH)) {
            List<String> baseBranchCandidates = gitFeatureBranchBaseBranches(featureBranchRef);
            if (baseBranchCandidates.size() == 1) {
                baseBranch = baseBranchCandidates.get(0);
            } else if (baseBranchCandidates.size() == 0) {
                baseBranch = getPrompter().promptValue(
                        "Base branch for feature branch '" + featureBranch + "' can not be determined automatically!\n"
                                + "Enter the base branch for the feature branch or [S] to skip this branch",
                        new GitFlowFailureInfo(
                                "Base branch for feature branch '" + featureBranch
                                        + "' can not be determined automatically!",
                                "Run 'mvn flow:upgrade' in interactive mode."));
                baseBranch = baseBranch.trim();
                if ("S".equalsIgnoreCase(baseBranch)) {
                    getLog().warn("Central configuration of feature branch '" + featureBranch + "' skipped!");
                    return;
                }
            } else {
                baseBranch = baseBranchCandidates.get(0);
                SelectOption selectedOption = getPrompter().promptToSelectFromOrderedListAndOptions(
                        "Base branch for feature branch '" + featureBranch + "' can not be determined unambiguously.\n"
                                + "Several candidates found:",
                        "Choose base branch for the feature branch.", baseBranchCandidates, null,
                        Arrays.asList(new SelectOption("T", null, "<prompt for explicit base branch name>"),
                                new SelectOption("S", null, "<skip this feature branch>")),
                        new GitFlowFailureInfo(
                                "Base branch for feature branch '" + featureBranch
                                        + "' can not be determined unambiguously!",
                                "Run 'mvn flow:upgrade' in interactive mode."));
                if ("T".equalsIgnoreCase(selectedOption.getKey())) {
                    baseBranch = getPrompter()
                            .promptValue("Enter the base branch for the feature branch '" + featureBranch + "'");
                } else if ("S".equalsIgnoreCase(selectedOption.getKey())) {
                    getLog().warn("Central configuration of feature branch '" + featureBranch + "' skipped!");
                    return;
                } else {
                    baseBranch = selectedOption.getValue();
                }
            }
            tmpChanges.setProperty(BranchConfigKeys.BASE_BRANCH, baseBranch);
        } else {
            baseBranch = properties.getProperty(BranchConfigKeys.BASE_BRANCH);
        }
        String featureIssue;
        if (!properties.containsKey(BranchConfigKeys.ISSUE_NUMBER)) {
            String featureName = featureBranch.substring(gitFlowConfig.getFeatureBranchPrefix().length());
            featureIssue = extractIssueNumberFromName(featureName, featureNamePattern,
                    "Feature branch conforms to <featureNamePattern>, but ther is no matching group to extract the "
                            + "issue number.",
                    "Feature branch does not conform to <featureNamePattern> specified, cannot extract issue number.");
            if (featureIssue == null) {
                featureIssue = featureName;
            }
            tmpChanges.setProperty(BranchConfigKeys.ISSUE_NUMBER, featureIssue);
        } else {
            featureIssue = properties.getProperty(BranchConfigKeys.ISSUE_NUMBER);
        }
        String featureStartMessage = substituteWithIssueNumber(commitMessages.getFeatureStartMessage(), featureIssue);
        if (!properties.containsKey(BranchConfigKeys.START_COMMIT_MESSAGE)) {
            tmpChanges.setProperty(BranchConfigKeys.START_COMMIT_MESSAGE, featureStartMessage);
        }
        if (!properties.containsKey(BranchConfigKeys.VERSION_CHANGE_COMMIT)) {
            String branchPoint = gitBranchPoint(featureBranchRef, baseBranch);
            String firstCommitOnBranch = gitFirstCommitOnBranch(featureBranchRef, branchPoint);
            String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
            if (firstCommitMessage.contains(featureStartMessage)) {
                tmpChanges.setProperty(BranchConfigKeys.VERSION_CHANGE_COMMIT, firstCommitOnBranch);
            }
        }
        if (!tmpChanges.isEmpty()) {
            getLog().info("Adding new configs for feature branch '" + featureBranch + "':");
            Enumeration<?> propertyNames = tmpChanges.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String key = (String) propertyNames.nextElement();
                String value = tmpChanges.getProperty(key);
                getLog().info(" - " + key + ": " + value);
                changes.set(featureBranch, key, value);
            }
            getLog().info("Configuration of feature branch '" + featureBranch + "' finished.");
        } else {
            getLog().info("Feature branch '" + featureBranch + "' is already configured.");
        }
    }

    private void collectMissingEpicBranchConfigs(BranchCentralConfigChanges changes, String epicBranch,
            Properties properties) throws MojoFailureException, CommandLineException {
        getLog().info("Configuring epic branch '" + epicBranch + "'...");
        Properties tmpChanges = new Properties();
        if (!properties.containsKey(BranchConfigKeys.BRANCH_TYPE)) {
            tmpChanges.setProperty(BranchConfigKeys.BRANCH_TYPE, BranchType.EPIC.getType());
        }
        String baseBranch;
        if (!properties.containsKey(BranchConfigKeys.BASE_BRANCH)) {
            List<String> baseBranchCandidates = gitEpicBranchBaseBranches(epicBranch);
            if (baseBranchCandidates.size() == 1) {
                baseBranch = baseBranchCandidates.get(0);
            } else if (baseBranchCandidates.size() == 0) {
                baseBranch = getPrompter().promptValue(
                        "Base branch for epic branch '" + epicBranch + "' can not be determined automatically!\n"
                                + "Enter the base branch for the epic branch or [S] to skip this branch",
                        new GitFlowFailureInfo(
                                "Base branch for epic branch '" + epicBranch + "' can not be determined automatically!",
                                "Run 'mvn flow:upgrade' in interactive mode."));
                baseBranch = baseBranch.trim();
                if ("S".equalsIgnoreCase(baseBranch)) {
                    getLog().warn("Central configuration of epic branch '" + epicBranch + "' skipped!");
                    return;
                }
            } else {
                baseBranch = baseBranchCandidates.get(0);
                SelectOption selectedOption = getPrompter().promptToSelectFromOrderedListAndOptions(
                        "Base branch for epic branch '" + epicBranch + "' can not be determined unambiguously.\n"
                                + "Several candidates found:",
                        "Choose base branch for the epic branch.", baseBranchCandidates, null,
                        Arrays.asList(new SelectOption("T", null, "<prompt for explicit base branch name>"),
                                new SelectOption("S", null, "<skip this epic branch>")),
                        new GitFlowFailureInfo(
                                "Base branch for epic branch '" + epicBranch + "' can not be determined unambiguously!",
                                "Run 'mvn flow:upgrade' in interactive mode."));
                if ("T".equalsIgnoreCase(selectedOption.getKey())) {
                    baseBranch = getPrompter()
                            .promptValue("Enter the base branch for the epic branch '" + epicBranch + "'");
                } else if ("S".equalsIgnoreCase(selectedOption.getKey())) {
                    getLog().warn("Central configuration of epic branch '" + epicBranch + "' skipped!");
                    return;
                } else {
                    baseBranch = selectedOption.getValue();
                }
            }
            tmpChanges.setProperty(BranchConfigKeys.BASE_BRANCH, baseBranch);
        } else {
            baseBranch = properties.getProperty(BranchConfigKeys.BASE_BRANCH);
        }
        String epicIssue;
        if (!properties.containsKey(BranchConfigKeys.ISSUE_NUMBER)) {
            String epicName = epicBranch.substring(gitFlowConfig.getEpicBranchPrefix().length());
            epicIssue = extractIssueNumberFromName(epicName, epicNamePattern,
                    "Epic branch conforms to <epicNamePattern>, but ther is no matching group to extract the issue "
                            + "number.",
                    "Epic branch does not conform to <epicNamePattern> specified, cannot extract issue number.");
            if (epicIssue == null) {
                epicIssue = epicName;
            }
            tmpChanges.setProperty(BranchConfigKeys.ISSUE_NUMBER, epicIssue);
        } else {
            epicIssue = properties.getProperty(BranchConfigKeys.ISSUE_NUMBER);
        }
        String epicStartMessage = substituteWithIssueNumber(commitMessages.getEpicStartMessage(), epicIssue);
        if (!properties.containsKey(BranchConfigKeys.START_COMMIT_MESSAGE)) {
            tmpChanges.setProperty(BranchConfigKeys.START_COMMIT_MESSAGE, epicStartMessage);
        }
        if (!properties.containsKey(BranchConfigKeys.VERSION_CHANGE_COMMIT)) {
            String branchPoint = gitBranchPoint(epicBranch, baseBranch);
            String firstCommitOnBranch = gitFirstCommitOnBranch(epicBranch, branchPoint);
            String firstCommitMessage = gitCommitMessage(firstCommitOnBranch);
            if (firstCommitMessage.contains(epicStartMessage)) {
                tmpChanges.setProperty(BranchConfigKeys.VERSION_CHANGE_COMMIT, firstCommitOnBranch);
            }
        }
        if (!tmpChanges.isEmpty()) {
            getLog().info("Adding new configs for epic branch '" + epicBranch + "':");
            Enumeration<?> propertyNames = tmpChanges.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String key = (String) propertyNames.nextElement();
                String value = tmpChanges.getProperty(key);
                getLog().info(" - " + key + ": " + value);
                changes.set(epicBranch, key, value);
            }
            getLog().info("Configuration of epic branch '" + epicBranch + "' finished.");
        } else {
            getLog().info("Epic branch '" + epicBranch + "' is already configured.");
        }
    }

    private List<String> gitEpicBranchBaseBranches(String epicBranch)
            throws MojoFailureException, CommandLineException {
        Map<String, List<String>> branchPointCandidates = new HashMap<>();
        addEpicBranchPointCandidates(epicBranch, branchPointCandidates);
        return gitBranchBaseBranches(epicBranch, branchPointCandidates);
    }

    private List<String> gitFeatureBranchBaseBranches(String featureBranch)
            throws MojoFailureException, CommandLineException {
        Map<String, List<String>> branchPointCandidates = new HashMap<>();
        addFeatureBranchPointCandidates(featureBranch, branchPointCandidates);
        return gitBranchBaseBranches(featureBranch, branchPointCandidates);
    }

    private List<String> gitBranchBaseBranches(String branch, Map<String, List<String>> branchPointCandidates)
            throws MojoFailureException, CommandLineException {
        Set<String> branchPoints = branchPointCandidates.keySet();
        String nearestBranchPoint = gitNearestAncestorCommit(branch, branchPoints);
        if (nearestBranchPoint != null) {
            return branchPointCandidates.get(nearestBranchPoint);
        }
        return Collections.EMPTY_LIST;
    }

    protected void addFeatureBranchPointCandidates(String featureBranch,
            Map<String, List<String>> branchPointCandidates) throws CommandLineException, MojoFailureException {
        addEpicBranchPointCandidates(featureBranch, branchPointCandidates);
        List<String> remoteEpicBranches = gitRemoteEpicBranches();
        if (remoteEpicBranches.size() > 0) {
            gitFetchBranches(remoteEpicBranches);
            for (String epicBranch : remoteEpicBranches) {
                addBranchPointCandidate(branchPointCandidates, featureBranch, epicBranch, true);
            }
        }
        List<String> localEpicBranches = gitLocalEpicBranches();
        if (localEpicBranches.size() > 0) {
            for (String epicBranch : localEpicBranches) {
                if (!branchPointCandidates.containsKey(epicBranch)) {
                    addBranchPointCandidate(branchPointCandidates, featureBranch, epicBranch, false);
                }
            }
        }
    }

    private void addEpicBranchPointCandidates(String branch, Map<String, List<String>> branchPointCandidates)
            throws CommandLineException, MojoFailureException {
        String developmentBranch = gitFlowConfig.getDevelopmentBranch();
        gitFetchBranches(developmentBranch);
        if (gitIsRemoteBranchFetched(gitFlowConfig.getOrigin(), developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, branch, developmentBranch, true);
        } else if (gitBranchExists(developmentBranch)) {
            addBranchPointCandidate(branchPointCandidates, branch, developmentBranch, false);
        }
        List<String> remoteMaintenanceBranches = gitRemoteMaintenanceBranches();
        if (remoteMaintenanceBranches.size() > 0) {
            gitFetchBranches(remoteMaintenanceBranches);
            for (String maintenanceBranch : remoteMaintenanceBranches) {
                addBranchPointCandidate(branchPointCandidates, branch, maintenanceBranch, true);
            }
        }
        List<String> localMaintenanceBranches = gitLocalMaintenanceBranches();
        if (localMaintenanceBranches.size() > 0) {
            for (String maintenanceBranch : localMaintenanceBranches) {
                if (!branchPointCandidates.containsKey(maintenanceBranch)) {
                    addBranchPointCandidate(branchPointCandidates, branch, maintenanceBranch, false);
                }
            }
        }
    }

    private void addBranchPointCandidate(Map<String, List<String>> branchPointCandidates, String featureBranch,
            String baseBranch, boolean remote) throws MojoFailureException, CommandLineException {
        String branchPoint = gitBranchPoint((remote ? gitFlowConfig.getOrigin() + "/" : "") + baseBranch,
                featureBranch);
        if (branchPoint != null) {
            List<String> baseBranches = branchPointCandidates.get(branchPoint);
            if (baseBranches == null) {
                baseBranches = new ArrayList<>();
                branchPointCandidates.put(branchPoint, baseBranches);
            }
            if (!baseBranches.contains(baseBranch)) {
                baseBranches.add(baseBranch);
            }
        }
    }

}
