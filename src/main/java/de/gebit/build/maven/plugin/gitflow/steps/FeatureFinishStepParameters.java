//
// FeatureFinishStepParameters.java
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
public class FeatureFinishStepParameters extends StepParameters<FeatureFinishBreakpoint> {

    public String featureBranch;
    public String baseBranch;
    public Boolean isOnFeatureBranch;
    public boolean rebasedBeforeFinish = false;
    public boolean rebasedWithoutVersionChangeCommit = false;

}
