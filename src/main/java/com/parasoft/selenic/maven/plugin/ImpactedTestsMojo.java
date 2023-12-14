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

import static java.lang.System.lineSeparator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.api.testset.TestListResolver;

/**
 * Scans an application and analyzes a baseline coverage report to execute unit
 * tests impacted by code changes.
 */
@Mojo(name = "impacted-tests", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, threadSafe = true)
public class ImpactedTestsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Specifies the location of the Parasoft Selenic installation.
     */
    @Parameter(property = "selenic.home", defaultValue = "${env.SELENIC_HOME}")
    private File selenicHome;

    /**
     * Specifies additional JVM options. Example:
     *
     * <pre><code>{@literal <vmArgs>}
     *   {@literal <vmArg>}-Xmx2g{@literal </vmArg>}
     * {@literal </vmArgs>}</code></pre>
     */
    @Parameter(property = "selenic.vmargs")
    private List<String> vmArgs;

    /**
     * Specifies the local file that contains binaries of the application under
     * test (AUT). You can specify the path to a folder or a .war, .jar, .zip,
     * or .ear file.
     */
    @Parameter(property = "selenic.app", required = true)
    private File app;

    /**
     * Specifies the XML coverage report to use as the baseline.
     */
    @Parameter(property = "selenic.baseline", required = true)
    private File baseline;

    /**
     * Specifies the path to the .properties file that includes custom
     * configuration settings. Use the selenic.properties file in the Selenic
     * home if not specified here.
     */
    @Parameter(property = "selenic.settings")
    private File settings;

    /**
     * <p>
     * Specifies patterns to include elements during AUT scanning. By default
     * all elements are accepted. It matches fully qualified names of classes
     * given in the form of ANT path patterns:
     * </p>
     *
     * <pre><code>{@literal <includes>}
     *   {@literal <include>}com/moduleone/**{@literal </include>}
     *   {@literal <include>}com/moduletwo/runtime/*{@literal </include>}
     * {@literal </includes>}</code></pre>
     */
    @Parameter(property = "selenic.includes")
    private List<String> includes;

    /**
     * <p>
     * Specifies patterns to exclude elements during AUT scanning. It matches
     * fully qualified names of classes given in the form of ANT path patterns:
     * </p>
     *
     * <pre><code>{@literal <excludes>}
     *   {@literal <exclude>**}/*Logger{@literal </exclude>}
     *   {@literal <exclude>}com/moduleone/**{@literal </exclude>}
     * {@literal </excludes>}</code></pre>
     */
    @Parameter(property = "selenic.excludes")
    private List<String> excludes;

    /**
     * <p>
     * Allows you to configure settings directly. Settings passed with this
     * parameter will overwrite those with the same key that are specified using
     * the {@code settings} parameter. Example:
     * </p>
     *
     * <pre><code>{@literal <properties>}
     *   {@literal <report.dtp.publish>}true{@literal </report.dtp.publish>}
     *   {@literal <console.verbosity.level>}high{@literal </console.verbosity.level>}
     * {@literal </properties>}</code></pre>
     */
    @Parameter
    private Map<String, String> properties;

    /**
     * Increases output verbosity.
     */
    @Parameter(property = "selenic.showdetails", defaultValue = "false")
    private boolean showdetails; // parasoft-suppress OPT.CTLV "injected"

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (selenicHome == null) {
            throw new MojoExecutionException(Messages.get("selenic.home.not.set")); //$NON-NLS-1$
        }
        if (!selenicHome.exists() || !Files.exists(selenicHome.toPath().resolve("selenic_agent.jar")) //$NON-NLS-1$
                || !Files.exists(selenicHome.toPath().resolve("selenic_analyzer.jar"))) { //$NON-NLS-1$
            throw new MojoExecutionException(Messages.get("selenic.missing", selenicHome)); //$NON-NLS-1$
        }
        Path covtoolJar = selenicHome.toPath().resolve("coverage").resolve("Java").resolve("jtestcov") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .resolve("jtestcov.jar"); //$NON-NLS-1$
        if (!baseline.exists()) {
            throw new MojoExecutionException(Messages.get("baseline.missing", baseline.toString())); //$NON-NLS-1$
        }
        if (!app.exists()) {
            throw new MojoExecutionException(Messages.get("app.missing", app.toString())); //$NON-NLS-1$
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
        File targetDir = new File(project.getBuild().getDirectory());
        Path covtoolWorkDir = targetDir.toPath().resolve("covtool"); //$NON-NLS-1$
        try {
            if (Files.exists(covtoolWorkDir)) {
                delete(covtoolWorkDir);
            }
            Files.createDirectories(covtoolWorkDir);
        } catch (IOException e) {
            log.debug(e);
            throw new MojoExecutionException(e);
        }
        runCovtoolJar(log, covtoolJar, settingsFile, covtoolWorkDir.toFile());
        applyImpactedTests(log, covtoolWorkDir);
    }

    private void applyImpactedTests(Log log, Path covtoolWorkDir) throws MojoExecutionException {
        Path lstFile = covtoolWorkDir.resolve(".coverage").resolve("lsts").resolve("impacted_tests.lst"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        List<String> impactedTests;
        if (Files.exists(lstFile)) {
            try {
                impactedTests = Files.readAllLines(lstFile);
            } catch (IOException e) {
                throw new MojoExecutionException(Messages.get("unable.to.read.lst.file", lstFile.toString()), e); //$NON-NLS-1$
            }
        } else {
            impactedTests = Collections.emptyList();
        }
        Properties prop = project.getProperties();
        String testsToRun;
        if (impactedTests.isEmpty()) {
            testsToRun = "!**/*";  //$NON-NLS-1$
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

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    private void runCovtoolJar(Log log, Path covtoolJar, File settingsFile, File covtoolWorkDir)
            throws MojoExecutionException {
        String osName = System.getProperty("os.name"); //$NON-NLS-1$
        boolean isWindows = osName != null && osName.startsWith("Windows"); //$NON-NLS-1$
        String javaExe = Paths.get(System.getProperty("java.home"), "bin", //$NON-NLS-1$ //$NON-NLS-2$
                isWindows ? "java.exe" : "java").toFile().getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
        List<String> command = new LinkedList<>();
        command.add(javaExe);
        if (vmArgs != null) {
            command.addAll(vmArgs);
        }
        addCommand("-jar", covtoolJar.toFile(), command); //$NON-NLS-1$
        command.add("impacted"); //$NON-NLS-1$
        command.add("-selenic"); //$NON-NLS-1$
        addCommand("-app", app, command); //$NON-NLS-1$
        addCommand("-baseline", baseline, command); //$NON-NLS-1$
        addOptionalCommand("-settings", settingsFile, command); //$NON-NLS-1$
        addOptionalCommand("-include", includes, command); //$NON-NLS-1$
        addOptionalCommand("-exclude", excludes, command); //$NON-NLS-1$
        if ((properties == null) || !properties.containsKey("tia.test.format")) { //$NON-NLS-1$
            command.add("-property"); //$NON-NLS-1$
            command.add("tia.test.format=junit"); //$NON-NLS-1$
        }
        addOptionalCommand("-property", properties, command); //$NON-NLS-1$
        addOptionalCommand("-showdetails", showdetails, command); //$NON-NLS-1$
        runCommand(log, command, covtoolWorkDir);
    }

    private static void runCommand(Log log, List<String> command, File covtoolWorkDir) throws MojoExecutionException {
        if (log.isDebugEnabled()) {
            log.debug("command:" + lineSeparator() + String.join(lineSeparator(), command)); //$NON-NLS-1$
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(covtoolWorkDir);
        pb.inheritIO();
        try {
            Process process = pb.start();
            try (OutputStream out = process.getOutputStream();
                    InputStream in = process.getInputStream();
                    InputStream err = process.getErrorStream()) {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new MojoExecutionException(Messages.get("covtool.returned.exit.code", exitCode)); //$NON-NLS-1$
                }
            } catch (InterruptedException e) {
                process.destroy();
                throw new MojoExecutionException(e);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private static void addCommand(String name, File value, List<String> command) {
        command.add(name);
        command.add(value.getAbsolutePath());
    }

    private static void addOptionalCommand(String name, File value, List<String> command) {
        if (value != null) {
            addCommand(name, value, command);
        }
    }

    private static void addOptionalCommand(String name, String value, List<String> command) {
        if (value != null && !value.trim().isEmpty()) {
            command.add(name);
            command.add(value);
        }
    }

    private static void addOptionalCommand(String name, List<String> parameters, List<String> command) {
        if (parameters != null) {
            for (String parameter : parameters) {
                addOptionalCommand(name, parameter, command);
            }
        }
    }

    private static void addOptionalCommand(String name, Map<String, String> parameters, List<String> command) {
        if (parameters != null) {
            for (Entry<String, String> entry : parameters.entrySet()) {
                addOptionalCommand(name, entry.getKey() + '=' + entry.getValue(), command);
            }
        }
    }

    private static void addOptionalCommand(String name, boolean value, List<String> command) {
        if (value) {
            command.add(name);
        }
    }
}
