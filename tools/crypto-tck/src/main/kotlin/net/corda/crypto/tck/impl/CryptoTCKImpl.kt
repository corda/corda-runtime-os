package net.corda.crypto.tck.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.tck.ComplianceTestType
import net.corda.crypto.tck.CryptoTCK
import net.corda.crypto.tck.ExecutionBuilder
import net.corda.crypto.tck.ExecutionOptions
import net.corda.crypto.tck.impl.compliance.CryptoServiceCompliance
import net.corda.crypto.tck.impl.compliance.SessionInactivityCompliance
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.engine.JupiterTestEngine
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.LoggingListener
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import org.opentest4j.TestAbortedException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.PrintWriter

@Component(service = [CryptoTCK::class], immediate = true)
class CryptoTCKImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    override val schemeMetadata: CipherSchemeMetadata
) : CryptoTCK {
    companion object {
        private val logger = contextLogger()
    }

    private val version: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            CryptoTCKImpl::class.java.classLoader
                .getResourceAsStream("META-INF/MANIFEST.MF")
                .use {
                    val manifest = java.util.jar.Manifest(it)
                    val name = manifest.mainAttributes.getValue("Bundle-Name")
                    val version = manifest.mainAttributes.getValue("Bundle-Version")
                    arrayOf(
                        name ?: "UNKNOWN",
                        version ?: "UNKNOWN"
                    )
                }
        } catch (e: Exception) {
            emptyArray()
        }.joinToString(" ")
    }

    override fun builder(serviceName: String, serviceConfig: Any): ExecutionBuilder =
        ExecutionBuilder(this, serviceName, serviceConfig)

    override fun run(options: ExecutionOptions) = try {
        val summaryListener = SummaryGeneratingListener()

        val spec = buildComplianceSpec(options)

        val request = buildTestRequest(spec)

        val out = PrintWriter(System.out)

        val launcher = buildTestLauncher(options, summaryListener, out)

        logger.info("EXECUTING COMPLIANCE TESTS: $version")

        launcher.execute(request)

        summaryListener.printReport(spec, out)

        ComplianceSpecExtension.unregister(spec)

        assertTrue(summaryListener.summary.isSuccess(), "TCK SUITE FAILED.")
    } catch (e: Throwable) {
        logger.error("TCK SUITE FAILED.", e)
        throw e
    }

    private fun buildComplianceSpec(options: ExecutionOptions): ComplianceSpec {
        return ComplianceSpec(options = options)
    }

    private fun buildTestRequest(spec: ComplianceSpec): LauncherDiscoveryRequest {
        val request = LauncherDiscoveryRequestBuilder.request()
        if (spec.options.tests.contains(ComplianceTestType.CRYPTO_SERVICE)) {
            request.selectors(DiscoverySelectors.selectClass(CryptoServiceCompliance::class.java))
        }
        if (spec.options.tests.contains(ComplianceTestType.SESSION_INACTIVITY)) {
            request.selectors(DiscoverySelectors.selectClass(SessionInactivityCompliance::class.java))
        }
        ComplianceSpecExtension.register(request, spec)
        return request.build()
    }

    private fun buildTestLauncher(
        options: ExecutionOptions,
        summaryListener: SummaryGeneratingListener,
        out: PrintWriter
    ): Launcher {
        val launcherConfig = LauncherConfig.builder()
            .enableTestEngineAutoRegistration(false)
            .enableTestExecutionListenerAutoRegistration(false)
            .addTestEngines(JupiterTestEngine())
            .addTestExecutionListeners(summaryListener)
            .addTestExecutionListeners(loggingListener())
            .addTestExecutionListeners(LegacyXmlReportGeneratingListener(options.testResultsDirectory, out))
            .build()
        return LauncherFactory.create(launcherConfig)
    }

    private fun loggingListener(): LoggingListener =
        LoggingListener.forBiConsumer { t, s ->
            if (t != null) {
                when (t) {
                    is TestAbortedException -> logger.warn(s.get())
                    is AssertionError -> logger.error(s.get())
                    else -> logger.error(s.get(), t)
                }
            } else {
                logger.info(s.get())
            }
        }

    private fun SummaryGeneratingListener.printReport(spec: ComplianceSpec, out: PrintWriter) {
        out.println("==========================")
        out.println("COMPLETED COMPLIANCE TESTS: $version")
        out.println("options.${spec.options::serviceName.name}=${spec.options.serviceName}")
        out.println("options.${spec.options::concurrency.name}=${spec.options.concurrency}")
        out.println("options.${spec.options::maxAttempts.name}=${spec.options.maxAttempts}")
        out.println("options.${spec.options::attemptTimeout.name}=${spec.options.attemptTimeout}")
        if (spec.options.sessionComplianceSpec != null) {
            out.println(
                "options.${spec.options::sessionComplianceTimeout.name}=" +
                        "${spec.options.sessionComplianceTimeout}"
            )
            out.println(
                "options.${spec.options::sessionComplianceSpec.name}=[" +
                        "${spec.options.sessionComplianceSpec.first},${spec.options.sessionComplianceSpec.second}]"
            )
        }
        out.println("options.${spec.options::tests.name}=${spec.options.tests.joinToString()}")
        out.println("options.${spec.options::testResultsDirectory.name}=${spec.options.testResultsDirectory}")
        out.println("options.${spec.options::usedSignatureSpecs.name}:")
        spec.options.usedSignatureSpecs.forEach {
            out.println("options.${spec.options::usedSignatureSpecs.name}:${it.key}=" +
                    "[${it.value.joinToString()}]"
            )
        }
        out.println("==========================")
        when (summary.isSuccess()) {
            true -> out.println("${System.lineSeparator()}TESTS WERE SUCCESSFUL!")
            else -> out.println("${System.lineSeparator()}TESTS HAVE FAILED!!!")
        }
        summary.printTo(out)
        summary.printFailuresTo(out)
    }

    private fun TestExecutionSummary.isSuccess(): Boolean = containersFailedCount == 0L &&
            testsFailedCount == 0L
}