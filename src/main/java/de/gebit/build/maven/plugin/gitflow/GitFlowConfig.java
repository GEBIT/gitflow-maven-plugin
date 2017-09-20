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
    /** Prefix of the release branch. */
    private String releaseBranchPrefix;
    /** Prefix of the hotfix branch. */
    private String hotfixBranchPrefix;
    /** Prefix of the maintenance branches. */
    private String maintenanceBranchPrefix;
    /** 
     * Prefix of integration branches.
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
     * @param productionBranch
     *            the productionBranch to set
     */
    public void setProductionBranch(String productionBranch) {
        this.productionBranch = productionBranch;
    }

    /**
     * @return the developmentBranch
     */
    public String getDevelopmentBranch() {
        return developmentBranch;
    }

    /**
     * @param developmentBranch
     *            the developmentBranch to set
     */
    public void setDevelopmentBranch(String developmentBranch) {
        this.developmentBranch = developmentBranch;
    }

    /**
     * @return the featureBranchPrefix
     */
    public String getFeatureBranchPrefix() {
        return featureBranchPrefix;
    }

    /**
     * @param featureBranchPrefix
     *            the featureBranchPrefix to set
     */
    public void setFeatureBranchPrefix(String featureBranchPrefix) {
        this.featureBranchPrefix = featureBranchPrefix;
    }

    /**
     * @return the releaseBranchPrefix
     */
    public String getReleaseBranchPrefix() {
        return releaseBranchPrefix;
    }

    /**
     * @param releaseBranchPrefix
     *            the releaseBranchPrefix to set
     */
    public void setReleaseBranchPrefix(String releaseBranchPrefix) {
        this.releaseBranchPrefix = releaseBranchPrefix;
    }

    /**
     * @return the hotfixBranchPrefix
     */
    public String getHotfixBranchPrefix() {
        return hotfixBranchPrefix;
    }

    /**
     * @param hotfixBranchPrefix
     *            the hotfixBranchPrefix to set
     */
    public void setHotfixBranchPrefix(String hotfixBranchPrefix) {
        this.hotfixBranchPrefix = hotfixBranchPrefix;
    }

    /**
     * @param supportBranchPrefix
     *            the supportBranchPrefix to set
     * @deprecated use {@link #maintenanceBranchPrefix}
     */
    public void setSupportBranchPrefix(String supportBranchPrefix) {
        this.maintenanceBranchPrefix = supportBranchPrefix;
    }
    
    /**
     * @return the maintenanceBranchPrefix
     */
    public String getMaintenanceBranchPrefix() {
        return maintenanceBranchPrefix;
    }
    
    /**
     * @param maintenanceBranchPrefix
     *            the maintenanceBranchPrefix to set
     */
    public void setMaintenanceBranchPrefix(String maintenanceBranchPrefix) {
        this.maintenanceBranchPrefix = maintenanceBranchPrefix;
    }

    
    /**
     * @return the integrationBranchPrefix
     */
    public String getIntegrationBranchPrefix() {
        return integrationBranchPrefix;
    }
    
    /**
     * @param integrationBranchPrefix
     *            the integrationBranchPrefix to set
     */
    public void setIntegrationBranchPrefix(String integrationBranchPrefix) {
        this.integrationBranchPrefix = integrationBranchPrefix;
    }
    
    /**
     * @return the versionTagPrefix
     */
    public String getVersionTagPrefix() {
        return versionTagPrefix;
    }

    /**
     * @param versionTagPrefix
     *            the versionTagPrefix to set
     */
    public void setVersionTagPrefix(String versionTagPrefix) {
        this.versionTagPrefix = versionTagPrefix;
    }

    /**
     * @return the origin
     */
    public String getOrigin() {
        return origin;
    }

    /**
     * @param origin
     *            the origin to set
     */
    public void setOrigin(String origin) {
        this.origin = origin;
    }
    
    /**
     * @return the noProduction flag
     */
    public boolean isNoProduction() {
	return noProduction;
    }
    
    /**
     * @param noProduction
     *            flag whether there is no production branch. 
     */
    public void setNoProduction(boolean noProduction) {
	this.noProduction = noProduction;
    }
}
