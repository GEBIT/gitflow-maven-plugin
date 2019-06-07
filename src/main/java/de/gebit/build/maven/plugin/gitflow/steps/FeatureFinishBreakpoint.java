//
// FeatureFinishBreakpoint.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

public enum FeatureFinishBreakpoint implements Breakpoint {
    TEST_PROJECT_AFTER_REBASE("testProjectAfterRebase"), CLEAN_INSTALL("cleanInstall"), FINAL_MERGE(
            "finalMerge"), REBASE_BEFORE_FINISH(
                    "rebaseBeforeFinish"), REBASE_WITHOUT_VERSION_CHANGE("rebaseWithoutVersionChange");

    private String id;

    private FeatureFinishBreakpoint(String shortId) {
        id = "featureFinish." + shortId;
    }

    public static FeatureFinishBreakpoint valueById(String anId) {
        for (FeatureFinishBreakpoint breakpoint : values()) {
            if (breakpoint.getId().equals(anId)) {
                return breakpoint;
            }
        }
        return null;
    }

    @Override
    public String getId() {
        return id;
    }
}