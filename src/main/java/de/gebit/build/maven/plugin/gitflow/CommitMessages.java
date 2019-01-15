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
 * Git commit messages.
 *
 * @author Aleksandr Mashchenko
 *
 */
public class CommitMessages {
    private String featureStartMessage;
    private String featureNewModulesMessage;
    private String featureFinishMessage;

    private String epicStartMessage;
    private String epicNewModulesMessage;
    private String epicFinishMessage;

    private String hotfixStartMessage;
    private String hotfixFinishMessage;

    private String releaseStartMessage;
    private String releaseFinishMessage;

    private String tagHotfixMessage;
    private String tagReleaseMessage;

    private String maintenanceStartMessage;
    private String branchConfigMessage;

    /**
     * An optional pattern for branch config commit messages.
     * May contain the placeholder @{message} that will be replaced with the
     * provided commit message.
     * Other placeholders will be looked up in the project properties.
     */
    private String branchConfigMessagePattern;

    /**
     * An optional pattern for merge commit messages.
     * May contain the placeholder @{message} that will be replaced with the
     * original commit message.
     * Other placeholders will be looked up in the project properties.
     */
    private String mergeMessagePattern;

    public CommitMessages() {
        featureStartMessage = "updating versions for feature branch";
        featureNewModulesMessage = "updating versions for new modules on feature branch";
        featureFinishMessage = "updating versions for development branch";

        epicStartMessage = "updating versions for epic branch";
        epicNewModulesMessage = "updating versions for new modules on epic branch";
        epicFinishMessage = "updating versions for development branch";

        hotfixStartMessage = "updating versions for hotfix";
        hotfixFinishMessage = "updating for next development version";

        releaseStartMessage = "updating versions for release";
        releaseFinishMessage = "updating for next development version";

        tagHotfixMessage = "tagging hotfix";
        tagReleaseMessage = "tagging release";

        maintenanceStartMessage = "updating versions for maintenance branch";
        branchConfigMessage = "changed branch configuration";

        branchConfigMessagePattern = null;
        mergeMessagePattern = null;
    }

    /**
     * @return the featureStartMessage
     */
    public String getFeatureStartMessage() {
        return featureStartMessage;
    }

    /**
     * @param aFeatureStartMessage
     *            the featureStartMessage to set
     */
    public void setFeatureStartMessage(String aFeatureStartMessage) {
        this.featureStartMessage = aFeatureStartMessage;
    }

    /**
     * @return the featureNewModulesMessage
     */
    public String getFeatureNewModulesMessage() {
        return featureNewModulesMessage;
    }

    /**
     * @param aFeatureNewModulesMessage
     *            the featureNewModulesMessage to set
     */
    public void setFeatureNewModulesMessage(String aFeatureNewModulesMessage) {
        this.featureNewModulesMessage = aFeatureNewModulesMessage;
    }

    /**
     * @return the epicNewModulesMessage
     */
    public String getEpicNewModulesMessage() {
        return epicNewModulesMessage;
    }

    /**
     * @param aEpicNewModulesMessage
     *            the epicNewModulesMessage to set
     */
    public void setEpicNewModulesMessage(String aEpicNewModulesMessage) {
        this.epicNewModulesMessage = aEpicNewModulesMessage;
    }

    /**
     * @return the featureFinishMessage
     */
    public String getFeatureFinishMessage() {
        return featureFinishMessage;
    }

    /**
     * @param aFeatureFinishMessage
     *            the featureFinishMessage to set
     */
    public void setFeatureFinishMessage(String aFeatureFinishMessage) {
        this.featureFinishMessage = aFeatureFinishMessage;
    }

    /**
     * @return the epicStartMessage
     */
    public String getEpicStartMessage() {
        return epicStartMessage;
    }

    /**
     * @param anEpicStartMessage
     *            the epicStartMessage to set
     */
    public void setEpicStartMessage(String anEpicStartMessage) {
        this.epicStartMessage = anEpicStartMessage;
    }

    /**
     * @return the epicFinishMessage
     */
    public String getEpicFinishMessage() {
        return epicFinishMessage;
    }

    /**
     * @param anEpicFinishMessage
     *            the epicFinishMessage to set
     */
    public void setEpicFinishMessage(String anEpicFinishMessage) {
        this.epicFinishMessage = anEpicFinishMessage;
    }

    /**
     * @return the hotfixStartMessage
     */
    public String getHotfixStartMessage() {
        return hotfixStartMessage;
    }

    /**
     * @param aHotfixStartMessage
     *            the hotfixStartMessage to set
     */
    public void setHotfixStartMessage(String aHotfixStartMessage) {
        this.hotfixStartMessage = aHotfixStartMessage;
    }

    /**
     * @return the hotfixFinishMessage
     */
    public String getHotfixFinishMessage() {
        return hotfixFinishMessage;
    }

    /**
     * @param aHotfixFinishMessage
     *            the hotfixFinishMessage to set
     */
    public void setHotfixFinishMessage(String aHotfixFinishMessage) {
        this.hotfixFinishMessage = aHotfixFinishMessage;
    }

    /**
     * @return the releaseStartMessage
     */
    public String getReleaseStartMessage() {
        return releaseStartMessage;
    }

    /**
     * @param aReleaseStartMessage
     *            the releaseStartMessage to set
     */
    public void setReleaseStartMessage(String aReleaseStartMessage) {
        this.releaseStartMessage = aReleaseStartMessage;
    }

    /**
     * @return the releaseFinishMessage
     */
    public String getReleaseFinishMessage() {
        return releaseFinishMessage;
    }

    /**
     * @param aReleaseFinishMessage
     *            the releaseFinishMessage to set
     */
    public void setReleaseFinishMessage(String aReleaseFinishMessage) {
        this.releaseFinishMessage = aReleaseFinishMessage;
    }

    /**
     * @return the tagHotfixMessage
     */
    public String getTagHotfixMessage() {
        return tagHotfixMessage;
    }

    /**
     * @param aTagHotfixMessage
     *            the tagHotfixMessage to set
     */
    public void setTagHotfixMessage(String aTagHotfixMessage) {
        this.tagHotfixMessage = aTagHotfixMessage;
    }

    /**
     * @return the tagReleaseMessage
     */
    public String getTagReleaseMessage() {
        return tagReleaseMessage;
    }

    /**
     * @param aTagReleaseMessage
     *            the tagReleaseMessage to set
     */
    public void setTagReleaseMessage(String aTagReleaseMessage) {
        this.tagReleaseMessage = aTagReleaseMessage;
    }

    /**
     * @return the maintenanceStartMessage
     */
    public String getMaintenanceStartMessage() {
        return maintenanceStartMessage;
    }

    /**
     * @param aMaintenanceStartMessage
     *            the maintenanceStartMessage to set
     */
    public void setMaintenanceStartMessage(String aMaintenanceStartMessage) {
        this.maintenanceStartMessage = aMaintenanceStartMessage;
    }

    /**
     * @return the branchConfigMessage
     */
    public String getBranchConfigMessage() {
        return branchConfigMessage;
    }

    /**
     * @param aBranchConfigMessage
     *            the branchConfigMessage to set
     */
    public void setBranchConfigMessage(String aBranchConfigMessage) {
        this.branchConfigMessage = aBranchConfigMessage;
    }

    /**
     * @return the mergeMessagePattern
     */
    public String getMergeMessagePattern() {
        return mergeMessagePattern;
    }

    /**
     * @param aMergeMessagePattern the mergeMessagePattern to set
     */
    public void setMergeMessagePattern(String aMergeMessagePattern) {
        mergeMessagePattern = aMergeMessagePattern;
    }

    /**
     * @return the branchConfigMessagePattern
     */
    public String getBranchConfigMessagePattern() {
        return branchConfigMessagePattern;
    }

    /**
     * @param aBranchConfigMessagePattern the branchConfigMessagePattern to set
     */
    public void setBranchConfigMessagePattern(String aBranchConfigMessagePattern) {
        branchConfigMessagePattern = aBranchConfigMessagePattern;
    }
}
