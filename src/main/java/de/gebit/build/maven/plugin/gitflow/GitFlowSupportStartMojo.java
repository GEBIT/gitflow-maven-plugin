//
// GitFlowSupportStartMojo.java
//
// Copyright (C) 2017
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * This mojo is for compatibility so you can still use "support-start"
 * 
 * @author Erwin Tratar
 * @since 1.3.0
 * @deprecated use {@link GitFlowMaintenanceStartMojo}
 */
@Mojo(name = "support-start", aggregator = true)
@Deprecated
public class GitFlowSupportStartMojo extends GitFlowMaintenanceStartMojo {
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("The support-start goal is deprecated, use maintenance-start instead.");
        super.execute();
    }
}
