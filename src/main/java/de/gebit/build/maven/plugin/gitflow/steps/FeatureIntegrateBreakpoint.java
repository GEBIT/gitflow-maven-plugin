//
// FeatureFinishBreakpoint.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow.steps;

public enum FeatureIntegrateBreakpoint implements Breakpoint {
    CLEAN_INSTALL("cleanInstall"), REBASE("rebase");

    private String id;

    private FeatureIntegrateBreakpoint(String shortId) {
        id = "featureIntegrate." + shortId;
    }

    public static FeatureIntegrateBreakpoint valueById(String anId) {
        for (FeatureIntegrateBreakpoint breakpoint : values()) {
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