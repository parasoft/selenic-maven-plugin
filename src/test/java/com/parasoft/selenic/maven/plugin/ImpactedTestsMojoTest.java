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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.withSettings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedConstruction;

public class ImpactedTestsMojoTest {
    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
        }

        @Override
        protected void after() {
        }
    };

    @Test
    public void testExecute() throws Exception {
        File pom = new File("target/test-classes/project-to-test/");
        assertNotNull(pom);
        assertTrue(pom.exists());

        ImpactedTestsMojo impactedTestsMojo = (ImpactedTestsMojo) rule.lookupConfiguredMojo(pom, "impacted-tests");
        assertNotNull(impactedTestsMojo);
        List<String> command;
        Path mockSelenicInstallation = Files.createTempDirectory("selenic");
        Path appFolder = Files.createDirectories(pom.toPath().resolve("target").resolve("appfolder"));
        try {
            Files.createFile(mockSelenicInstallation.resolve("selenic_agent.jar"));
            Files.createFile(mockSelenicInstallation.resolve("selenic_analyzer.jar"));
            Path mockCovtoolInstallation = mockSelenicInstallation.resolve("coverage").resolve("Java").resolve("jtestcov");
            Files.createDirectories(mockCovtoolInstallation);
            Files.createFile(mockCovtoolInstallation.resolve("jtestcov.jar"));
            rule.setVariableValueToObject(impactedTestsMojo, "selenicHome", mockSelenicInstallation.toFile());
            Process process = mock(Process.class);
            try (MockedConstruction<ProcessBuilder> processBuilder = mockConstruction(ProcessBuilder.class,
                    withSettings().defaultAnswer(CALLS_REAL_METHODS), (mock, context) -> {
                        doReturn(process).when(mock).start();
                        doReturn(context.arguments().get(0)).when(mock).command();
                    })) {
                impactedTestsMojo.execute();
                List<ProcessBuilder> constructed = processBuilder.constructed();
                assertEquals(1, constructed.size());
                command = constructed.get(0).command();
            }
        } finally {
            try (Stream<Path> stream = Files.walk(mockSelenicInstallation)) {
                stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        assertEquals(24, command.size());
        assertThat(command.get(0), endsWith(Paths.get("bin", SystemUtils.IS_OS_WINDOWS ? "java.exe" : "java").toString()));
        assertEquals("-jar", command.get(1));
        assertThat(command.get(2), endsWith(Paths.get("coverage", "Java", "jtestcov", "jtestcov.jar").toString()));
        assertEquals("impacted", command.get(3));
        assertEquals("-selenic", command.get(4));
        assertEquals("-settings", command.get(5));
        assertEquals(new File(pom, "settings.properties").getAbsolutePath(), command.get(6));
        assertEquals("-property", command.get(7));
        assertEquals("console.verbosity.level=high", command.get(8));
        assertEquals("-property", command.get(9));
        assertEquals("parasoft.eula.accepted=true", command.get(10));
        assertEquals("-property", command.get(11));
        assertEquals("tia.test.format=junit", command.get(12));
        assertEquals("-showdetails", command.get(13));
        assertEquals("-app", command.get(14));
        assertEquals(appFolder.toFile().getAbsolutePath(), command.get(15));
        assertEquals("-include", command.get(16));
        assertEquals("**/com/parasoft/**", command.get(17));
        assertEquals("-exclude", command.get(18));
        assertEquals("**/.log", command.get(19));
        assertEquals("-exclude", command.get(20));
        assertEquals("**/foo", command.get(21));
        assertEquals("-baseline", command.get(22));
        assertEquals(new File(pom, "baseline.xml").getAbsolutePath(), command.get(23));
    }

    /** Do not need the MojoRule. */
    @WithoutMojo
    @Test
    public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() {
        assertTrue(true);
    }

}
