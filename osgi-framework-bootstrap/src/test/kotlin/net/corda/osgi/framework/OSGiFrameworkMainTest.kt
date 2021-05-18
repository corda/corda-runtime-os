package net.corda.osgi.framework

import org.junit.jupiter.api.Test

/**
 * This test installs and runs the test application in the module `framework-app-tester`.
 *
 * The [net.corda.osgi.framework.test.AppTester] implements the [net.corda.osgi.api.Lifecycle].
 *
 * **NOTE!**
 *
 * **Run the gradle `appJar` task to build the application before to run this test.**
 *
 * JUnit finds the test application in the classpath:
 *  1. [OSGiFrameworkMain] installs the application with its dependencies;
 *  2. [OSGiFrameworkWrap.startApplications] bootstraps the application with
 *  [net.corda.osgi.framework.test.AppTester] inside;
 *  3. [net.corda.osgi.framework.test.AppTester.startup] calls
 *  [net.corda.osgi.api.ShutdownService.shutdown] to ask to quit;
 *  5. [OSGiFrameworkWrap.stop] calls [net.corda.osgi.framework.test.AppTester.shutdown],
 *  6. [OSGiFrameworkWrap.stop] stops the OSGi framework.
 *  7. [OSGiFrameworkMain] is free to quit.
 *
 */
class OSGiFrameworkMainTest {

    @Test
    fun main() {
        OSGiFrameworkMain.main(arrayOf())
    }

}