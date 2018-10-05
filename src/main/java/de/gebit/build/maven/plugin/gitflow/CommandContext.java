/*
 * Copyright 2018 GEBIT Solutions GmbH
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
 * Context in which a command is executed or configured to be executed. 
 * @since 2.1.2
 */
public enum CommandContext {

    /**
     * A release version change.
     */
    RELEASE,

    /**
     * An internal version change. Normally commands do not want to participate in this.
     */
    INTERNAL,

    /**
     * An explicit version change. Normally commands will want to participate in this.
     */
    VERSION;
}
