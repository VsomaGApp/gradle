/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




package org.gradle.integtests

import org.gradle.util.TestFile;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*
import org.gradle.api.Project;

@RunWith(DistributionIntegrationTestRunner.class)
public class JUnitIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void executesTestsInCorrectEnvironment() {
        TestFile testDir = dist.testDir;
        TestFile buildFile = testDir.file('build.gradle');
        buildFile << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.4', 'ant:ant:1.6.1', 'ant:ant-launcher:1.6.1' }
            test {
                systemProperties.testSysProperty = 'value'
                environment.TEST_ENV_VAR = 'value'
            }
        '''
        testDir.file("src/test/java/org/gradle/OkTest.java") << """
            package org.gradle;
            import static org.junit.Assert.*;
            public class OkTest {
                @org.junit.Test public void ok() throws Exception {
                    // check JUnit version
                    assertEquals("4.4", new org.junit.runner.JUnitCore().getVersion());
                    // check Ant version
                    assertTrue(org.apache.tools.ant.Main.getAntVersion().contains("1.6.1"));
                    // check working dir
                    assertEquals("${testDir.absolutePath.replaceAll('\\\\', '\\\\\\\\')}", System.getProperty("user.dir"));
                    // check Gradle classes not visible
                    try { getClass().getClassLoader().loadClass("${Project.class.getName()}"); fail(); } catch(ClassNotFoundException e) { }
                    // check context classloader
                    assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
                    // check sys properties
                    assertEquals("value", System.getProperty("testSysProperty"));
                    // check env vars
                    assertEquals("value", System.getenv("TEST_ENV_VAR"));
                    // check stdout and stderr
                    System.out.println("This is test stdout");
                    System.err.println("This is test stderr");
                }
            }
        """
        executer.withTasks('build').run();

        TestFile resultFile = testDir.file('build/test-results/TEST-org.gradle.OkTest.xml');
        resultFile.assertIsFile();
        resultFile.assertContents(containsString('This is test stdout'));
        resultFile.assertContents(containsString('This is test stderr'));
    }

    @Test
    public void reportsAndBreaksBuildWhenTestFails() {
        TestFile testDir = dist.getTestDir();
        TestFile buildFile = testDir.file('build.gradle');
        buildFile << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.7' }
        '''
        testDir.file("src/test/java/org/gradle/BrokenTest.java") << '''
            package org.gradle;
            public class BrokenTest {
                @org.junit.Test public void broken() { org.junit.Assert.fail(); }
            }
        '''
        ExecutionFailure failure = executer.withTasks('build').runWithFailure();

        failure.assertHasFileName("Build file '${buildFile}'");
        failure.assertHasDescription("Execution failed for task ':test'.");
        failure.assertThatCause(startsWith('There were failing tests.'));

        assertThat(failure.getError(), containsLine('Test broken(org.gradle.BrokenTest) FAILED'));
    }

    @Test
    public void canUseTestSuperClassesFromAnotherProject() {
        TestFile testDir = dist.getTestDir();
        testDir.file('settings.gradle').write("include 'a', 'b'");
        testDir.file('b/build.gradle') << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { compile 'junit:junit:4.7' }
        '''
        testDir.file('b/src/main/java/org/gradle/AbstractTest.java') << '''
            package org.gradle;
            public abstract class AbstractTest {
                @org.junit.Test public void ok() { }
            }
        '''
        TestFile buildFile = testDir.file('a/build.gradle');
        buildFile << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile project(':b') }
        '''
        testDir.file('a/src/test/java/org/gradle/SomeTest.java') << '''
            package org.gradle;
            public class SomeTest extends AbstractTest {
            }
        '''

        executer.withTasks('a:test').run();
        testDir.file('a/build/test-results/TEST-org.gradle.SomeTest.xml').assertIsFile();
    }

    @Test
    public void canHaveTestsOnInnerClasses() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply id: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }"
        );
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest {",
                "    public static class SomeInner {",
                "        @org.junit.Test public void ok() { }",
                "    }",
                "}");

        executer.withTasks('test').run();
        testDir.file('build/test-results/TEST-org.gradle.SomeTest$SomeInner.xml').assertIsFile();
    }

    @Test
    public void canHaveRunWithAnnotationOnSuperClass() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply id: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }"
        );
        testDir.file('src/test/java/org/gradle/CustomRunner.java').writelns(
                "package org.gradle;",
                "public class CustomRunner extends org.junit.runners.BlockJUnit4ClassRunner {",
                "    public CustomRunner(Class c) throws Exception { super(c); }",
                "}");
        testDir.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "@org.junit.runner.RunWith(CustomRunner.class)",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() { }",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");

        executer.withTasks('test').run();
        testDir.file('build/test-results/TEST-org.gradle.SomeTest.xml').assertIsFile();
    }

    @Test
    public void runsAllTestsInTheSameForkedJvm() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply id: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }"
        );
        testDir.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();
        TestFile results1 = testDir.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDir.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), equalTo(results2.linesThat(containsString('VM START TIME =')).get(0)));
    }

    @Test
    public void canSpecifyMaximumNumberOfTestClassesToExecuteInAForkedJvm() {
        TestFile testDir = dist.getTestDir();
        testDir.file('build.gradle').writelns(
                "apply id: 'java'",
                "repositories { mavenCentral() }",
                "dependencies { compile 'junit:junit:4.7' }",
                "test.forkEvery = 1"
        );
        testDir.file('src/test/java/org/gradle/AbstractTest.java').writelns(
                "package org.gradle;",
                "public abstract class AbstractTest {",
                "    @org.junit.Test public void ok() {",
                "        long time = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();",
                "        System.out.println(String.format(\"VM START TIME = %s\", time));",
                "    }",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest.java').writelns(
                "package org.gradle;",
                "public class SomeTest extends AbstractTest {",
                "}");
        testDir.file('src/test/java/org/gradle/SomeTest2.java').writelns(
                "package org.gradle;",
                "public class SomeTest2 extends AbstractTest {",
                "}");

        executer.withTasks('test').run();
        TestFile results1 = testDir.file('build/test-results/TEST-org.gradle.SomeTest.xml');
        TestFile results2 = testDir.file('build/test-results/TEST-org.gradle.SomeTest2.xml');
        results1.assertIsFile();
        results2.assertIsFile();
        assertThat(results1.linesThat(containsString('VM START TIME =')).get(0), not(equalTo(results2.linesThat(
                containsString('VM START TIME =')).get(0))));
    }

    @Test
    public void canListenForTestResults() {
        TestFile testDir = dist.getTestDir();
        testDir.file('src/main/java/AppException.java').writelns(
                "public class AppException extends Exception { }"
        );

        testDir.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest {",
                "@org.junit.Test public void pass() { }",
                "@org.junit.Test public void fail() { org.junit.Assert.fail(\"message\"); }",
                "@org.junit.Test public void knownError() { throw new RuntimeException(\"message\"); }",
                "@org.junit.Test public void unknownError() throws AppException { throw new AppException(); }",
                "}"
        );

        testDir.file('build.gradle') << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.7' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(Test suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(Test suite) { println "FINISH [$suite] [$suite.name]" }
                void beforeTest(Test test) { println "START [$test] [$test.name]" }
                void afterTest(Test test, TestResult result) { println "FINISH [$test] [$test.name] [$result.error]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assertThat(result.getOutput(), containsLine("START [all tests] []"));
        assertThat(result.getOutput(), containsLine("FINISH [all tests] []"));
        assertThat(result.getOutput(), containsLine("START [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("FINISH [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("START [test pass(SomeTest)] [pass]"));
        assertThat(result.getOutput(), containsLine("FINISH [test pass(SomeTest)] [pass] [null]"));
        assertThat(result.getOutput(), containsLine("START [test fail(SomeTest)] [fail]"));
        assertThat(result.getOutput(), containsLine("FINISH [test fail(SomeTest)] [fail] [junit.framework.AssertionFailedError: message]"));
        assertThat(result.getOutput(), containsLine("START [test knownError(SomeTest)] [knownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test knownError(SomeTest)] [knownError] [java.lang.RuntimeException: message]"));
        assertThat(result.getOutput(), containsLine("START [test unknownError(SomeTest)] [unknownError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test unknownError(SomeTest)] [unknownError] [org.gradle.messaging.dispatch.PlaceholderException: AppException: null]"));
    }

    @Test
    public void canListenForTestResultsWhenJUnit3IsUsed() {
        TestFile testDir = dist.getTestDir();
        testDir.file('src/test/java/SomeTest.java').writelns(
                "public class SomeTest extends junit.framework.TestCase {",
                "public void testPass() { }",
                "public void testFail() { junit.framework.Assert.fail(\"message\"); }",
                "public void testError() { throw new RuntimeException(\"message\"); }",
                "}"
        );

        testDir.file('build.gradle') << '''
            apply id: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:3.8' }
            def listener = new TestListenerImpl()
            test.addTestListener(listener)
            test.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(Test suite) { println "START [$suite] [$suite.name]" }
                void afterSuite(Test suite) { println "FINISH [$suite] [$suite.name]" }
                void beforeTest(Test test) { println "START [$test] [$test.name]" }
                void afterTest(Test test, TestResult result) { println "FINISH [$test] [$test.name] [$result.error]" }
            }
        '''

        ExecutionResult result = executer.withTasks("test").run();
        assertThat(result.getOutput(), containsLine("START [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("FINISH [test class SomeTest] [SomeTest]"));
        assertThat(result.getOutput(), containsLine("START [test testPass(SomeTest)] [testPass]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testPass(SomeTest)] [testPass] [null]"));
        assertThat(result.getOutput(), containsLine("START [test testFail(SomeTest)] [testFail]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testFail(SomeTest)] [testFail] [junit.framework.AssertionFailedError: message]"));
        assertThat(result.getOutput(), containsLine("START [test testError(SomeTest)] [testError]"));
        assertThat(result.getOutput(), containsLine("FINISH [test testError(SomeTest)] [testError] [java.lang.RuntimeException: message]"));
    }
}
