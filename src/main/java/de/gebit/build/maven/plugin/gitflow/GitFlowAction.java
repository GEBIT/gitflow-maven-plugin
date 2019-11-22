//
// GitFlowAction.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

/**
 * Gitflow action type like feature-start, feature-finish etc.
 *
 * @author Volodja Medvid
 * @since 2.1.4
 */
public enum GitFlowAction {

    FEATURE_START(CommandContext.VERSION),
    FEATURE_REBASE(CommandContext.VERSION),
    FEATURE_FINISH(CommandContext.INTERNAL),
    EPIC_START(CommandContext.VERSION),
    EPIC_UPDATE(CommandContext.VERSION),
    EPIC_FINISH(CommandContext.INTERNAL),
    MAINTENANCE_START(CommandContext.VERSION),
    RELEASE_START(CommandContext.RELEASE),
    RELEASE_FINISH(CommandContext.VERSION),
    HOTFIX_START(CommandContext.INTERNAL),
    HOTFIX_FINISH(CommandContext.INTERNAL),
    SET_VERSION(CommandContext.VERSION),
    MAKE_VERSIONLESS(CommandContext.VERSION),
    BUILD_VERSION(CommandContext.VERSION);

    private CommandContext commandContext;

    private GitFlowAction(CommandContext aCommandContext) {
        commandContext = aCommandContext;
    }

    /**
     * Get the command context for the gitflow action.
     *
     * @return The command context.
     */
    public CommandContext getCommandContext() {
        return commandContext;
    }
}
