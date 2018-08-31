//
// FeatureIntegrateStepParameters.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

/**
 *
 * @author Volodymyr Medvid
 */
public class FeatureIntegrateStepParameters extends StepParameters<FeatureIntegrateBreakpoint> {

    public String sourceFeatureBranch;
    public String targetFeatureBranch;
    public String sourceBaseBranch;
    public String tempSourceFeatureBranch;

}
