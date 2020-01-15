//
// GitFlowFeatureRebaseCleanupMojo.java
//
// Copyright (C) 2020
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * Cleanup commits on a feature branch by squashing/reodering.
 * <p>
 * Cleans up the commits on a feature branch using interactive rebase, i.e.
 * allows reordering and squashing into less commits. This is basically a
 * <code>git rebase --interactive</code> with help to set the correct
 * parameters.
 * <p>
 * No rebase on top of the development branch is executed here!
 *
 * @see GitFlowFeatureRebaseMojo
 *
 * @author Erwin Tratar
 * @author Volodja 
 * @since 1.5.11
 * @deprecated Use 'feature-cleanup' goal instead.
 */
@Deprecated
@Mojo(name = GitFlowFeatureRebaseCleanupMojo.GOAL_ALIAS, aggregator = true, threadSafe = true)
public class GitFlowFeatureRebaseCleanupMojo extends GitFlowFeatureCleanupMojo {

    static final String GOAL_ALIAS = "feature-rebase-cleanup";
    
    @Override
    protected void executeGoal() throws CommandLineException, MojoExecutionException, MojoFailureException {
        getMavenLog().warn("Goal 'flow:feature-rebase-cleanup' is deprecated. "
                + "Please use 'flow:feature-cleanup' instead.");
        super.executeGoal();
    }

}
