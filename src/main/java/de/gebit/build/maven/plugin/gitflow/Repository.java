/*
 * Copyright 2017 GEBIT Solutions
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

import org.apache.maven.plugin.MojoFailureException;

/**
 * Configuration for a deployment repository to check.
 * 
 * @since 1.5.6
 */
public class Repository {
    private String id;
    private String url;
    private boolean mandatory;

    public void set(String repoSpec) throws MojoFailureException {
        int separator = repoSpec.indexOf('|');
        if (separator <= 0) {
            throw new MojoFailureException("Repository spec " + repoSpec + " is not of the required format [id]|[url]");
        }
        id = repoSpec.substring(0, separator);
        url = repoSpec.substring(separator + 1);
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param aId
     *            the id to set
     */
    public void setId(String aId) {
        id = aId;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param aUrl
     *            the url to set
     */
    public void setUrl(String aUrl) {
        url = aUrl;
    }

    /**
     * @return the mandatory
     */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * @param aMandatory the mandatory to set
     */
    public void setMandatory(boolean aMandatory) {
        mandatory = aMandatory;
    }
}
