//
// AbstractGitFlowEpicMojo.java
//
// Copyright (C) 2018
// GEBIT Solutions GmbH,
// Berlin, Duesseldorf, Stuttgart (Germany)
// All rights reserved.
//
package de.gebit.build.maven.plugin.gitflow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Abstract implementation for all epic mojos.
 *
 * @author Volodymyr Medvid
 * @since 1.5.15
 */
public abstract class AbstractGitFlowEpicMojo extends AbstractGitFlowMojo {

    /**
     * A regex pattern that a new epic name must match. It is also used to
     * extract a "key" from a branch name which can be referred to as
     * <code>@key</code> in commit messages. The extraction will be performed
     * using the first matching group (if present). You will need this if your
     * commit messages need to refer to e.g. an issue tracker key.
     */
    @Parameter(property = "epicNamePattern", required = false)
    protected String epicNamePattern;

    /**
     * Extracts the epic issue number from epic name using epic name pattern.
     * E.g. extracts issue number "GBLD-42" from epic name
     * "GBLD-42-someDescription" if default epic name pattern is used. Returns
     * epic name if issue number can't be extracted.
     *
     * @param epicName
     *            the epic name
     * @return the extracted epic issue number or epic name if issue number
     *         can't be extracted
     */
    protected String extractIssueNumberFromEpicName(String epicName) {
        String issueNumber = epicName;
        if (epicNamePattern != null) {
            // extract the issue number only
            Matcher m = Pattern.compile(epicNamePattern).matcher(epicName);
            if (m.matches()) {
                if (m.groupCount() == 0) {
                    getLog().warn("Epic branch conforms to <epicNamePattern>, but ther is no matching"
                            + " group to extract the issue number.");
                } else {
                    issueNumber = m.group(1);
                }
            } else {
                getLog().warn("Epic branch does not conform to <epicNamePattern> specified, cannot "
                        + "extract issue number.");
            }
        }
        return issueNumber;
    }

}
