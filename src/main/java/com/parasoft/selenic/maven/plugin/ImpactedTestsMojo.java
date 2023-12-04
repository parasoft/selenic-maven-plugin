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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Scans an application and analyzes a baseline coverage report to execute unit
 * tests impacted by code changes.
 */
@Mojo(name = "impacted-tests", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, threadSafe = true)
public class ImpactedTestsMojo extends AbstractMojo {

    /**
     * Specifies the location of the Parasoft Selenic installation.
     */
    @Parameter(property = "selenic.home", defaultValue = "${env.SELENIC_HOME}")
    private File selenicHome;

    @Override
    public void execute() throws MojoExecutionException {
        if (selenicHome == null) {
            throw new MojoExecutionException(Messages.get("selenic.home.not.set")); //$NON-NLS-1$
        }
    }
}
