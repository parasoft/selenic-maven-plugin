/*
 * Copyright 2023 Parasoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parasoft.selenic.maven.plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

public abstract class AbstractSelenicMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * Specifies the location of the Parasoft Selenic installation.
     */
    @Parameter(property = "selenic.home", defaultValue = "${env.SELENIC_HOME}")
    protected File selenicHome;

    /**
     * Specifies the path to the .properties file that includes custom configuration
     * settings. Use the selenic.properties file in the Selenic home if not
     * specified here.
     */
    @Parameter(property = "selenic.settings")
    private File settings;

    @Override
    public void execute() throws MojoExecutionException {
        if (selenicHome == null) {
            throw new MojoExecutionException(Messages.get("selenic.home.not.set")); //$NON-NLS-1$
        }
        if (!selenicHome.exists() || !Files.exists(selenicHome.toPath().resolve("selenic_agent.jar")) //$NON-NLS-1$
                || !Files.exists(selenicHome.toPath().resolve("selenic_analyzer.jar"))) { //$NON-NLS-1$
            throw new MojoExecutionException(Messages.get("selenic.missing", selenicHome)); //$NON-NLS-1$
        }
        File settingsFile = settings;
        if (settingsFile == null) {
            Path settingsPath = selenicHome.toPath().resolve("selenic.properties"); //$NON-NLS-1$
            if (Files.exists(settingsPath)) {
                settingsFile = settingsPath.toFile();
            }
        } else if (!settingsFile.exists()) {
            throw new MojoExecutionException(Messages.get("settings.missing", settingsFile.toString())); //$NON-NLS-1$
        }
        doExecute(settingsFile);
    }

    protected abstract void doExecute(File settingsFile) throws MojoExecutionException;
}
