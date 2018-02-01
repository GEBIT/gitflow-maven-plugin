/*
 * Copyright 2014-2016 Aleksandr Mashchenko. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
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
 * The git flow branch config mojo. Used to configure branch specific properties
 *
 * @author Erwin Tratar
 * @since 1.4.0
 */
@Mojo(name = "branch-config", aggregator = true)
public class GitFlowBranchConfigMojo extends AbstractGitFlowMojo {

	/**
	 * Set the property name to specify. If not set in interactive mode you will be asked.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "propertyName")
	private String propertyName;

	/**
	 * Specify the property value to set. If not specified, the property is removed (in interactive mode you will be
	 * asked).
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "propertyValue")
	private String propertyValue;

	/**
	 * Name of the branch to configure. If not specified the current branch is used.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "branchName")
	private String branchName;

	/**
	 * Name of the branch used to hold the branch configuration properties.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "configBranchName", defaultValue = "branch-config")
	private String configBranchName;

	/**
	 * Name of the directory used to temporarily and locally checkout the configuration branch.
	 *
	 * @since 1.4.0
	 */
	@Parameter(property = "configBranchDir", defaultValue = ".branch-config")
	private String configBranchDir;

	/** {@inheritDoc} */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			// set git flow configuration
			initGitFlowConfig();

			if (settings.isInteractiveMode() && propertyName == null) {
				try {
					propertyName = prompter.prompt("Which property to modify?");
				} catch (PrompterException e) {
					getLog().error(e);
				}
			}

			if (settings.isInteractiveMode() && propertyValue == null) {
				try {
					propertyValue = prompter.prompt("Set the value to (empty to delete)");
				} catch (PrompterException e) {
					getLog().error(e);
				}
			}

			if (StringUtils.isBlank(propertyName)) {
				throw new MojoFailureException("No property name set, aborting....");
			}

			// modify the branch config
			String currentBranch = branchName != null ? branchName : gitCurrentBranch();
			getLog().info("Settting branch property '"
					+ propertyName
					+ "' for '"
					+ currentBranch
					+ "' to '"
					+ propertyValue
					+ "'");
			gitBranchConfigWorktree(currentBranch, configBranchName, configBranchDir, propertyName, propertyValue);

		} catch (CommandLineException e) {
			getLog().error(e);
		}
	}
}
