package org.jboss.gm.cli;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.SystemUtils;
import org.gradle.api.logging.LogLevel;
import org.jboss.gm.analyzer.alignment.AlignmentPlugin;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import ch.qos.logback.core.pattern.color.ANSIConstants;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MainTest {

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private String escapeBackslashes(String dir) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return FilenameUtils.normalize(dir, true).replace("/", "\\\\\\\\");
        }

        return dir;
    }

    @Test
    public void testGradleNotFound() throws IOException {
        Main m = new Main();
        String[] args = new String[] { "-t", tempDir.newFolder().getAbsolutePath(), "-l", UUID.randomUUID().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Unable to locate Gradle installation"));
        }
    }

    @Test
    public void testInvokeGradle() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertFalse(systemOutRule.getLog().contains("loggingColours=false"));
        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
        assertTrue(systemOutRule.getLog().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeGradleDisabledColor() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        environmentVariables.set("NO_COLOR", "1");

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("loggingColours=false"));
        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
        assertFalse(systemOutRule.getLog().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeGradleWithProperties() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "help", "--info",
                "-Dfoobar=nothing",
                "-Dfoobar=barfoo", "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=https://www.foo.com/tmp/fake-file" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Welcome to Gradle"));
    }

    @Test
    public void testInvokeGroovy() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=" + groovy };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
        assertTrue(systemOutRule.getLog().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLog().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("groovyScripts="));
        assertTrue(systemOutRule.getLog().contains("Verification tasks"));
    }

    @Test
    public void testDisableGME() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DgroovyScripts=" + groovy,
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-Dmanipulation.disable=true" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertFalse(systemOutRule.getLog().contains("Verification tasks"));
        assertTrue(systemOutRule.getLog().contains("Gradle Manipulator disabled"));
    }

    @Test
    public void testInvokeAlignment() throws Exception {

        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        init = init.replaceFirst("(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();

        try (Reader reader = new FileReader(new File(root, "gradle.properties"))) {
            actualVersion.load(reader);
        }

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DdependencySource=NONE",
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10"
        };
        int result = m.run(args);
        assertEquals(0, result);

        File gmeGradle = new File(projectRoot.getParentFile().getAbsolutePath(), "gme.gradle");
        assertTrue(systemOutRule.getLog().contains("Task :generateAlignmentMetadata"));

        System.err.println("Verifying it has injected gme.gradle with version " + actualVersion.getProperty("version"));
        assertTrue(gmeGradle.exists());
        assertTrue(FileUtils.readFileToString(gmeGradle, Charset.defaultCharset())
                .contains("org.jboss.gm:manipulation:" + actualVersion.getProperty("version")));
        assertTrue(systemOutRule.getLog().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeAlignmentFails() throws Exception {
        final File target = tempDir.newFolder();
        final File source = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());
        final File root = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath())
                .getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
        final File projectRoot = new File(target, source.getName());
        FileUtils.copyFile(source, projectRoot);

        environmentVariables.set("NO_COLOR", "1");

        // This hack-fest is because the development version is not in Maven Central so it won't be resolvable
        // This adds the compiled libraries as flat dir repositories.
        final File initFile = tempDir.newFile();
        final String dir1 = escapeBackslashes(
                new File(AlignmentPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        final String dir2 = escapeBackslashes(
                new File(ManipulationModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
        String init = FileUtils.readFileToString(new File(root, "/analyzer/build/resources/main/analyzer-init.gradle"),
                Charset.defaultCharset());
        init = init.replaceFirst("(?s)mavenLocal.*snapshots\"\\n\\s+[}]",
                "\n        flatDir {\n            dirs '" + dir1 +
                        "'\n        }\n" +
                        "\n        flatDir {\n            dirs '" + dir2 +
                        "'\n        }\n");
        System.err.println("Writing to " + initFile + ":" + init);
        FileUtils.writeStringToFile(initFile, init, Charset.defaultCharset());

        Properties actualVersion = new Properties();

        try (Reader reader = new FileReader(new File(root, "gradle.properties"))) {
            actualVersion.load(reader);
        }

        Main m = new Main();
        String[] args = new String[] { "-t", projectRoot.getParentFile().getAbsolutePath(), "generateAlignmentMetadata",
                "--init-script=" + initFile.getCanonicalPath(),
                "-DignoreUnresolvableDependencies=true",
                "-DdependencyOverride.junit:junit@*=4.10"
        };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            assertTrue(e.getCause().getMessage().contains("must be configured in order for dependency scanning to work"));
        }
        assertTrue(systemErrRule.getLog().contains("'restURL' must be configured in order for dependency scanning to work"));
        assertFalse(systemErrRule.getLog().contains(ANSIConstants.ESC_START));
    }

    @Test
    public void testInvokeGradleWithExternal() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        Main m = new Main();
        String[] args = new String[] { "--trace", "-d", "-t", projectRoot.getParentFile().getAbsolutePath(), "getGitVersion" };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Process 'command 'git'' finished with exit value"));
    }

    @Test
    public void testInvokeGroovyWithDuplicateOption() throws Exception {

        final File projectRoot = new File(MainTest.class.getClassLoader().getResource("build.gradle").getPath());

        final URL groovy = Thread.currentThread().getContextClassLoader().getResource("sample.groovy");

        Main m = new Main();
        String[] args = new String[] { "--no-colour", "-t", projectRoot.getParentFile().getAbsolutePath(), "tasks",
                "-DdependencyOverride.org.jboss.slf4j:*@*=",
                "-DgroovyScripts=" + groovy };
        m.run(args);

        assertTrue(systemOutRule.getLog().contains("Running Groovy script on"));
        assertTrue(systemOutRule.getLog().contains("Tasks runnable from root project"));
        assertTrue(systemOutRule.getLog().contains("dependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("with JVM args '[-DdependencyOverride.org.jboss.slf4j:*@*="));
        assertFalse(systemOutRule.getLog().contains(", DdependencyOverride.org.jboss.slf4j:*@*="));
        assertTrue(systemOutRule.getLog().contains("groovyScripts="));
    }

    @Test
    public void testGradleInvalidTarget1() throws IOException {
        Main m = new Main();
        String[] args = new String[] { "-t", tempDir.newFile().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().contains("Pass project root as directory not file"));
        }
    }

    @Test
    public void testGradleInvalidTarget2() {
        Main m = new Main();
        String[] args = new String[] { "-t", UUID.randomUUID().toString() };
        try {
            m.run(args);
            fail("No exception thrown");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().contains("Unable to locate target directory"));
        }
    }
}
