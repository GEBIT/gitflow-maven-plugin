/*
 * Copyright 2017 GEBIT Solutions GmbH
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow integrated marker mojo. Will update the integration branch associated with the current branch to
 * the same reference to mark a successful integration (e.g. after a build is run without any errors).
 * 
 * @since 1.5.10
 * @author Erwin Tratar
 */
@Mojo(name = "integrated", aggregator = true)
public class GitFlowIntegratedMojo extends AbstractGitFlowMojo {

    /**
     * Specifies an integration branch to update. If not provided a default is computed using  {@link GitFlowConfig#getIntegrationBranchSuffix()}.
     */
    @Parameter(property = "integrationBranch", defaultValue = "${integrationBranch}", required = false)
    private String integrationBranch;

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            if (StringUtils.isBlank(integrationBranch)) {
                integrationBranch = gitFlowConfig.getIntegrationBranchPrefix() + gitCurrentBranch();
                
                if (settings.isInteractiveMode()) {
                    try {
                        integrationBranch = prompter.prompt("What is the integration branch name?", integrationBranch);
                    } catch (PrompterException e) {
                        throw new MojoFailureException("Failed to get integration branch name", e);
                    }
                }
            }

            gitUpdateRef(integrationBranch, "HEAD");

        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}
