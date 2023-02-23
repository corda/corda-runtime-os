package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.testing.MessageProvider
import net.corda.v5.testing.uuid.UUIDProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class SandboxSingletonsTest {
    companion object {
        private const val TIMEOUT_MILLIS = 10000L
        private const val CPB = "META-INF/sandbox-singletons-cpk.cpb"
        private const val MAP_PROVIDER_FLOW = "com.example.singletons.TestDataProvider"

        @JvmStatic
        @RegisterExtension
        private val lifecycle = AllTestsLifecycle()
    }

    private lateinit var sandboxData: Map<String, Any?>
    private var unmatchedService: Any? = null
    private var optionalService: Any? = null
    private var targetMaximum: Any? = null
    private var targetMinimum: Any? = null
    private var targetDefault: Any? = null
    private var defaultMessage: Any? = null
    private var targetedMessages: Any? = null
    private var allMessages: Any? = null
    private var unmatchedServices: Any? = null
    private var optionalServices: Any? = null

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS).withSandbox(CPB, FLOW) { vns, ctx ->
                val mapProviderClass = vns.getFlowClass(MAP_PROVIDER_FLOW, ctx)
                sandboxData = vns.runFlow(mapProviderClass)
                unmatchedService = sandboxData["unmatchedService"]
                optionalService = sandboxData["optionalService"]
                targetMaximum = sandboxData["targetMaximum"]
                targetMinimum = sandboxData["targetMinimum"]
                targetDefault = sandboxData["targetDefault"]
                defaultMessage = sandboxData["default"]
                unmatchedServices = sandboxData["unmatchedServices"]
                optionalServices = sandboxData["optionalServices"]
                targetedMessages = sandboxData["targeted"]
                allMessages = sandboxData["all"]

                // Check that the sandbox's UUIDProvider is not injectable.
                val mapProviderContext = vns.getBundleContext(mapProviderClass)
                assertNull(mapProviderContext.getServiceReferences(UUIDProvider::class.java.name, null))
            }
        }
    }

    @Test
    fun testTargetMaximum() {
        assertThat(targetMaximum)
            .describedAs("targetMaximum should be MessageProvider")
            .isInstanceOf(MessageProvider::class.java)
        assertThat(targetMaximum.toString()).startsWith("MessageMaximum[")
    }

    @Test
    fun testTargetMinimum() {
        assertThat(targetMinimum)
            .describedAs("targetMinimum should be MessageProvider")
            .isInstanceOf(MessageProvider::class.java)
        assertThat(targetMinimum.toString()).startsWith("MessageMinimum[")
    }

    @Test
    fun testTargetDefault() {
        assertThat(targetDefault)
            .describedAs("targetDefault should be MessageProvider")
            .isInstanceOf(MessageProvider::class.java)
        assertThat(targetDefault.toString()).startsWith("MessageDefault[")
    }

    @Test
    fun testDefaultMessage() {
        assertThat(defaultMessage)
            .describedAs("defaultMessage should be MessageProvider")
            .isInstanceOf(MessageProvider::class.java)
        assertSame(targetMaximum, defaultMessage)
    }

    @Test
    fun testUnderlyingMessageIsSingleton() {
        assertThat(defaultMessage)
            .describedAs("defaultMessage should be MessageProvider")
            .isInstanceOf(MessageProvider::class.java)
        val expectedMessage = (defaultMessage as MessageProvider).message
        assertEquals(expectedMessage, (targetDefault as MessageProvider).message)
        assertEquals(expectedMessage, (targetMaximum as MessageProvider).message)
        assertEquals(expectedMessage, (targetMinimum as MessageProvider).message)
    }

    @Test
    fun testAllMessagesArePresent() {
        assertThat(allMessages)
            .describedAs("allMessages should be List")
            .isInstanceOf(List::class.java)
        assertThat(allMessages as List<*>)
            .containsExactlyInAnyOrder(targetDefault, targetMaximum, targetMinimum)
    }

    @Test
    fun testTargetedMessagesArePresent() {
        assertThat(targetedMessages)
            .describedAs("targetMessages should be List")
            .isInstanceOf(List::class.java)
        assertThat(targetedMessages as List<*>)
            .containsExactly(targetMaximum, targetMinimum)
    }

    @Test
    fun testUnmatchedServiceIsMissing() {
        assertThat(unmatchedService)
            .describedAs("unmatchedService should be NO MATCH")
            .isEqualTo("NO MATCH")
    }

    @Test
    fun testOptionalServiceIsMissing() {
        assertThat(optionalService)
            .describedAs("optionalService should be MISSING")
            .isEqualTo("MISSING")
    }

    @Test
    fun testUnmatchedServicesAreMissing() {
        assertThat(unmatchedServices)
            .describedAs("unmatchedServices should be empty")
            .isEqualTo(emptyList<Any>())
    }

    @Test
    fun testOptionalServicesAreMissing() {
        assertThat(optionalServices)
            .describedAs("optionalServices should be empty")
            .isEqualTo(emptyList<Any>())
    }
}
