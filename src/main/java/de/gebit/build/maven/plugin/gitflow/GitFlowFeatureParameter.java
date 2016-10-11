//
// GitFlowFeatureParameter.java
//
// Copyright (C) 2016
// GEBIT Solutions GmbH, 
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Additional parameter that will be asked of the user when creating a feature branch.
 * @author Erwin Tratar
 */
public class GitFlowFeatureParameter {

    /**
     * User prompt text. If not set only the command is executed. Can contain the {@literal @}{version} placeholder
     * which is replaced by the selected feature version as well as any project property with the {@literal @}{property} 
     *syntax.
     */
    @Parameter(required = false)
    protected String prompt;

    /**
     * Can be bound to a variable to control whether it is being executed or not. Any non-empty value that is neither
     * <code>'no'</code> nor <code>'false'</code> will be interpreted as true.
     */
    @Parameter(required = false, defaultValue = "true")
    protected String enabled;

    /**
     * If set the prompt is interpreted as a yes/no answer which controls enablement. 
     */
    @Parameter(required = false)
    protected boolean enabledByPrompt;

    /**
     * The default value for the parameter. Can use the {@literal @}{version} or {@literal @}{currentVersion} placeholders
     * as well as any project property with the {@literal @}{property} syntax.
     */
    @Parameter(required = false)
    protected String defaultValue;

    /**
     * Maven command (goals) to execute. Can use the {@literal @}{value} placeholder if a value has been prompted.
     */
    @Parameter(required = true)
    protected String command;

    /**
     * Value entered by the user.
     */
    private String value;

    /**
     * @return the prompt
     */
    public String getPrompt() {
        return prompt;
    }

    // /**
    // * @param prompt the prompt to set
    // */
    // public void setPrompt(String prompt) {
    // this.prompt = prompt;
    // }

    /**
     * @return the defaultValue
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    // /**
    // * @param defaultValue the defaultValue to set
    // */
    // public void setDefaultValue(String defaultValue) {
    // this.defaultValue = defaultValue;
    // }

    /**
     * @return the command
     */
    public String getCommand() {
        return command;
    }

    // /**
    // * @param command the command to set
    // */
    // public void setCommand(String command) {
    // this.command = command;
    // }
    
    /**
     * @return the enabled
     */
    public String getEnabled() {
        return enabled;
    }
    
    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return enabled != null && !enabled.isEmpty() && !enabled.toLowerCase().equals("no")
                && !enabled.toLowerCase().equals("false");
    }
    
    /**
     * @return the enabledByPrompt
     */
    public boolean isEnabledByPrompt() {
        return enabledByPrompt;
    }
    
    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }
    
    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }
}
