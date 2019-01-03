//
// GitFlowFeatureIntegrateAbortMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;

import de.gebit.build.maven.plugin.gitflow.steps.FeatureIntegrateBreakpoint;

/**
 * Abort a rebase in process that was started during execution of
 * <code>flow:feature-integrate</code>.
 * <p>
 * Aborts the integration process properly. It can only be used on paused rebase caused by conflicts.
 *
 * @author Volodymyr Medvid
 * @see GitFlowFeatureIntegrateMojo
 * @since 2.1.0
 */
@Mojo(name = GitFlowFeatureIntegrateAbortMojo.GOAL, aggregator = true, threadSafe = true)
public class GitFlowFeatureIntegrateAbortMojo extends AbstractGitFlowFeatureMojo {

    static final String GOAL = "feature-integrate-abort";

    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().info("Starting feature integrate abort process");
        checkCentralBranchConfig();
        String tempSourceFeatureBranch = gitRebaseBranchInProcess();
        if (tempSourceFeatureBranch == null) {
            throw new GitFlowFailureException("No rebase in progress detected. Nothing to abort.", null);
        } else if (!tempSourceFeatureBranch
                .startsWith(createTempFeatureBranchName(gitFlowConfig.getFeatureBranchPrefix()))) {
            throw new GitFlowFailureException("Rebasing branch is not a temporary feature branch created during feature "
                    + "integrate process: " + tempSourceFeatureBranch, null);

        }
        FeatureIntegrateBreakpoint breakpoint = null;
        String breakpointId = gitGetBranchLocalConfig(tempSourceFeatureBranch, "breakpoint");
        if (breakpointId != null) {
            breakpoint = FeatureIntegrateBreakpoint.valueById(breakpointId);
        }
        if (breakpoint != FeatureIntegrateBreakpoint.REBASE) {
            throw new GitFlowFailureException("No rebase breakpoint found.",
                    "Please consult a gitflow expert on how to fix this!");
        }
        String sourceFeatureBranch = gitGetBranchLocalConfig(tempSourceFeatureBranch, "sourceFeatureBranch");
        String targetFeatureBranch = gitGetBranchLocalConfig(tempSourceFeatureBranch, "targetFeatureBranch");
        if (sourceFeatureBranch == null) {
            throw new GitFlowFailureException(
                    "No info about source feature branch found in local branch config. "
                            + "This indicates a severe error condition on your branches.",
                    "Please consult a gitflow expert on how to fix this!");
        }
        if (!getPrompter().promptConfirmation("You have a rebase in process on your current branch. "
                + "Are you sure you want to abort the integration of feature branch '" + sourceFeatureBranch
                + "' into feature branch '" + targetFeatureBranch + "'?", true, true)) {
            throw new GitFlowFailureException("Aborting feature integrate process aborted by user.", null);
        }
        getMavenLog().info("Aborting rebase in progress of the feature integration");
        gitRebaseAbort();
        getMavenLog().info("Switching back to source feature branch '" + sourceFeatureBranch + "'");
        gitCheckout(sourceFeatureBranch);
        if (gitBranchExists(tempSourceFeatureBranch)) {
            getLog().info("Deleting temporary branch used for feature integrate.");
            gitBranchDeleteForce(tempSourceFeatureBranch);
        }
        getMavenLog().info("Feature rebase abort process finished");
    }

}
