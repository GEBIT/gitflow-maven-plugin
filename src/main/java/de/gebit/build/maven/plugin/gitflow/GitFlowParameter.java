//
// GitFlowParameter.java
//
// Copyright (C) 2016
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.Arrays;
import java.util.EnumSet;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Additional parameter that will be asked of the user when creating a feature branch.
 * @author Erwin Tratar
 */
public class GitFlowParameter {

    /**
     * User prompt text. If not set only the command is executed. Can contain the {@literal @}{version} placeholder
     * which is replaced by the selected feature version as well as any project property with the {@literal @}{property}
     *syntax.
     */
    @Parameter(required = false)
    protected String prompt;

    /**
     * Explicit command contexts for a command. The command is executed only if
     * used in one of the specified contexts. By default only for normal version
     * changes.
     * 
     * @since 2.1.2
     */
    @Parameter(required = false)
    protected CommandContext[] contexts = new CommandContext[] { CommandContext.VERSION };

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
     * Name of a property which, if defined will be used as the value skipping the querying.
     * @since 1.5.4
     */
    @Parameter(required = false)
    protected String property;

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

    /**
     * @return the defaultValue
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @return the property
     */
    public String getProperty() {
        return property;
    }

    /**
     * @return the command
     */
    public String getCommand() {
        return command;
    }

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
     * @param aValue the value to set
     */
    public void setValue(String aValue) {
        this.value = aValue;
    }

    /**
     * @return an EnumSet of enabled command contexts.
     * @since 2.1.2
     */
    public EnumSet<CommandContext> getCommandContexts() {
        switch (contexts.length) {
        case 0:
            return EnumSet.noneOf(CommandContext.class);
        case 1:
            return EnumSet.<CommandContext>of(contexts[0]);
        default:
            return EnumSet.<CommandContext>of(contexts[0], Arrays.copyOfRange(contexts, 1, contexts.length));
        }
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("GitFlowParameter(");
        if (prompt != null) { 
            result.append("Prompt=").append(prompt).append(",");
        }
        if (enabled != null) {
            result.append("Enabled=").append(enabled).append(",");
        }
        if (enabledByPrompt) {
            result.append("enabledByPrompt,");
        }
        if (defaultValue != null) {
            result.append("defaultValue=").append(defaultValue).append(",");
        }
        if (property != null) {
            result.append("property=").append(property).append(",");
        }
        if (command != null) {
            result.append("command=").append(command).append(",");
        }
        if (value != null) {
            result.append("value=").append(value).append(",");
        }
        result.append(")");
        return result.toString();
    }
}
