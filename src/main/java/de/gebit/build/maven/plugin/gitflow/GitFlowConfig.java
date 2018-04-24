/*
 * Copyright 2014-2016 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gebit.build.maven.plugin.gitflow;

/**
 * Git flow configuration.
 *
 * @author Aleksandr Mashchenko
 *
 */
public class GitFlowConfig {
    /** Name of the production branch. */
    private String productionBranch;
    /** Name of the development branch. */
    private String developmentBranch;
    /** Prefix of the feature branch. */
    private String featureBranchPrefix;
    /**
     * Prefix of epic branches.
     *
     * @since 1.5.15
     */
    private String epicBranchPrefix;
    /** Prefix of the release branch. */
    private String releaseBranchPrefix;
    /** Prefix of the hotfix branch. */
    private String hotfixBranchPrefix;
    /** Prefix of the maintenance branches. */
    private String maintenanceBranchPrefix;
    /**
     * Prefix of integration branches.
     *
     * @since 1.5.10
     */
    private String integrationBranchPrefix;
    /** Prefix of the version tag. */
    private String versionTagPrefix;
    /** Set to true if there is no production branch. */
    private boolean noProduction;
    /** Name of the default remote. */
    private String origin;

    /**
     * Default constructor.
     */
    public GitFlowConfig() {
        this.productionBranch = "master";
        this.developmentBranch = "develop";
        this.featureBranchPrefix = "feature/";
        this.epicBranchPrefix = "epic/";
        this.releaseBranchPrefix = "release/";
        this.hotfixBranchPrefix = "hotfix/";
        this.maintenanceBranchPrefix = "maintenance/";
        this.versionTagPrefix = "";
        this.noProduction = false;
        this.origin = "origin";
        this.integrationBranchPrefix = "integration/";
    }

    /**
     * @return the productionBranch
     */
    public String getProductionBranch() {
        return productionBranch;
    }

    /**
     * @param aProductionBranch
     *            the productionBranch to set
     */
    public void setProductionBranch(String aProductionBranch) {
        this.productionBranch = aProductionBranch;
    }

    /**
     * @return the developmentBranch
     */
    public String getDevelopmentBranch() {
        return developmentBranch;
    }

    /**
     * @param aDevelopmentBranch
     *            the developmentBranch to set
     */
    public void setDevelopmentBranch(String aDevelopmentBranch) {
        this.developmentBranch = aDevelopmentBranch;
    }

    /**
     * @return the featureBranchPrefix
     */
    public String getFeatureBranchPrefix() {
        return featureBranchPrefix;
    }

    /**
     * @param aFeatureBranchPrefix
     *            the featureBranchPrefix to set
     */
    public void setFeatureBranchPrefix(String aFeatureBranchPrefix) {
        this.featureBranchPrefix = aFeatureBranchPrefix;
    }

    /**
     * @return the epicBranchPrefix
     */
    public String getEpicBranchPrefix() {
        return epicBranchPrefix;
    }

    /**
     * @param anEpicBranchPrefix
     *            the epicBranchPrefix to set
     */
    public void setEpicBranchPrefix(String anEpicBranchPrefix) {
        this.epicBranchPrefix = anEpicBranchPrefix;
    }

    /**
     * @return the releaseBranchPrefix
     */
    public String getReleaseBranchPrefix() {
        return releaseBranchPrefix;
    }

    /**
     * @param aReleaseBranchPrefix
     *            the releaseBranchPrefix to set
     */
    public void setReleaseBranchPrefix(String aReleaseBranchPrefix) {
        this.releaseBranchPrefix = aReleaseBranchPrefix;
    }

    /**
     * @return the hotfixBranchPrefix
     */
    public String getHotfixBranchPrefix() {
        return hotfixBranchPrefix;
    }

    /**
     * @param aHotfixBranchPrefix
     *            the hotfixBranchPrefix to set
     */
    public void setHotfixBranchPrefix(String aHotfixBranchPrefix) {
        this.hotfixBranchPrefix = aHotfixBranchPrefix;
    }

    /**
     * @return the maintenanceBranchPrefix
     */
    public String getMaintenanceBranchPrefix() {
        return maintenanceBranchPrefix;
    }

    /**
     * @param aMaintenanceBranchPrefix
     *            the maintenanceBranchPrefix to set
     */
    public void setMaintenanceBranchPrefix(String aMaintenanceBranchPrefix) {
        this.maintenanceBranchPrefix = aMaintenanceBranchPrefix;
    }

    /**
     * @return the integrationBranchPrefix
     */
    public String getIntegrationBranchPrefix() {
        return integrationBranchPrefix;
    }

    /**
     * @param anIntegrationBranchPrefix
     *            the integrationBranchPrefix to set
     */
    public void setIntegrationBranchPrefix(String anIntegrationBranchPrefix) {
        this.integrationBranchPrefix = anIntegrationBranchPrefix;
    }

    /**
     * @return the versionTagPrefix
     */
    public String getVersionTagPrefix() {
        return versionTagPrefix;
    }

    /**
     * @param aVersionTagPrefix
     *            the versionTagPrefix to set
     */
    public void setVersionTagPrefix(String aVersionTagPrefix) {
        this.versionTagPrefix = aVersionTagPrefix;
    }

    /**
     * @return the origin
     */
    public String getOrigin() {
        return origin;
    }

    /**
     * @param anOrigin
     *            the origin to set
     */
    public void setOrigin(String anOrigin) {
        this.origin = anOrigin;
    }

    /**
     * @return the noProduction flag
     */
    public boolean isNoProduction() {
        return noProduction;
    }

    /**
     * @param isNoProduction
     *            flag whether there is no production branch.
     */
    public void setNoProduction(boolean isNoProduction) {
        this.noProduction = isNoProduction;
    }
}
