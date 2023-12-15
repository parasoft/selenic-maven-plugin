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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.surefire.api.testset.TestListResolver;

/**
 * Scans an application and analyzes a baseline coverage report to execute unit
 * tests impacted by code changes.
 */
@Mojo(name = "impacted-tests", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, threadSafe = true)
public class ImpactedTestsMojo extends AbstractCoverageMojo {

    /**
     * Specifies the XML coverage report to use as the baseline.
     */
    @Parameter(property = "selenic.coverage.baseline", required = true)
    private File baseline;

    ImpactedTestsMojo() {
        super("impacted"); //$NON-NLS-1$
    }

    @Override
    protected void addAdditionalArguments(List<String> command) throws MojoExecutionException {
        if (!baseline.exists()) {
            throw new MojoExecutionException(Messages.get("baseline.missing", baseline)); //$NON-NLS-1$
        }
        addCommand("-baseline", baseline, command); //$NON-NLS-1$
    }

    @Override
    protected void doOtherWork(Log log, Path covtoolWorkDir) throws MojoExecutionException {
        Path lstFile = covtoolWorkDir.resolve(".coverage").resolve("lsts").resolve("impacted_tests.lst"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        List<String> impactedTests;
        if (Files.exists(lstFile)) {
            try {
                impactedTests = Files.readAllLines(lstFile);
            } catch (IOException e) {
                throw new MojoExecutionException(Messages.get("unable.to.read.lst.file", lstFile), e); //$NON-NLS-1$
            }
        } else {
            impactedTests = Collections.emptyList();
        }
        Properties prop = project.getProperties();
        String testsToRun;
        if (impactedTests.isEmpty()) {
            testsToRun = "!**/*"; //$NON-NLS-1$
            prop.setProperty("surefire.failIfNoSpecifiedTests", "false"); //$NON-NLS-1$ //$NON-NLS-2$
            prop.setProperty("failsafe.failIfNoSpecifiedTests", "false"); //$NON-NLS-1$ //$NON-NLS-2$
            log.debug("No impacted tests to run"); //$NON-NLS-1$
        } else {
            TestListResolver testListResolver = new TestListResolver(impactedTests);
            testsToRun = testListResolver.getPluginParameterTest();
            log.debug("Applying impacted tests to run: " + testsToRun); //$NON-NLS-1$
        }
        prop.setProperty("test", testsToRun); //$NON-NLS-1$
        prop.setProperty("it.test", testsToRun); //$NON-NLS-1$
    }
}
