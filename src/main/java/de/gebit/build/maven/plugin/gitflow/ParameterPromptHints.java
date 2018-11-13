//
// ParameterPromptHints.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Hints for the user prompt text of additional version commands for different
 * gitflow actions.
 *
 * @author Volodja Medvid
 * @since 2.1.4
 */
public class ParameterPromptHints {

    /**
     * Hint for the user prompt text for feature-start action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String featureStart;

    /**
     * Hint for the user prompt text for feature-rebase action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String featureRebase;

    /**
     * Hint for the user prompt text for feature-finish action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String featureFinish;

    /**
     * Hint for the user prompt text for epic-start action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String epicStart;

    /**
     * Hint for the user prompt text for epic-finish. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String epicFinish;

    /**
     * Hint for the user prompt text for maintenance-start action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String maintenanceStart;

    /**
     * Hint for the user prompt text for release-start action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String releaseStart;

    /**
     * Hint for the user prompt text for release-finish action. Can contain the
     * {@literal @}{version} placeholder which is replaced by the version to be set
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter
    protected String releaseFinish;

    /**
     * Get the hint for the user prompt text for feature-start action.
     *
     * @return The hint for feature-start action.
     */
    public String getFeatureStart() {
        return featureStart;
    }

    /**
     * Get the hint for the user prompt text for feature-rebase action.
     *
     * @return The hint for feature-rebase action.
     */
    public String getFeatureRebase() {
        return featureRebase;
    }

    /**
     * Get the hint for the user prompt text for feature-finish action.
     *
     * @return The hint for feature-finish action.
     */
    public String getFeatureFinish() {
        return featureFinish;
    }

    /**
     * Get the hint for the user prompt text for epic-start action.
     *
     * @return The hint for epic-start action.
     */
    public String getEpicStart() {
        return epicStart;
    }

    /**
     * Get the hint for the user prompt text for epic-finish action.
     *
     * @return The hint for epic-finish action.
     */
    public String getEpicFinish() {
        return epicFinish;
    }

    /**
     * Get the hint for the user prompt text for maintenance-start action.
     *
     * @return The hint for maintenance-start action.
     */
    public String getMaintenanceStart() {
        return maintenanceStart;
    }

    /**
     * Get the hint for the user prompt text for release-start action.
     *
     * @return The hint for release-start action.
     */
    public String getReleaseStart() {
        return releaseStart;
    }

    /**
     * Get the hint for the user prompt text for release-finish action.
     *
     * @return The hint for release-finish action.
     */
    public String getReleaseFinish() {
        return releaseFinish;
    }

    @Override
    public String toString() {
        return "{featureStart=" + featureStart + ", featureRebase=" + featureRebase + ", featureFinish=" + featureFinish
                + ", epicStart=" + epicStart + ", epicFinish=" + epicFinish + ", maintenanceStart=" + maintenanceStart
                + ", releaseStart=" + releaseStart + ", releaseFinish=" + releaseFinish + "}";
    }

}
