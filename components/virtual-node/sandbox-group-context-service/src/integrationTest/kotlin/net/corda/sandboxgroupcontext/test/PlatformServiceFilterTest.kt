package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.testing.PlatformMessageProvider
import net.corda.v5.testing.PlatformService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.osgi.service.component.propertytypes.ServiceRanking
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class PlatformServiceFilterTest {
    companion object {
        private const val PLATFORM_SERVICE_NAME = "platform.service"
        private const val MESSENGER_CPB = "META-INF/sandbox-messenger-cpk.cpb"
        private const val MESSENGER_FLOW = "com.example.messenger.PlatformMessengerFlow"
        private const val TIMEOUT_MILLIS = 10000L
    }

    @Suppress("JUnitMalformedDeclaration")
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService

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
            virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
        }
    }

    private fun getMessageFor(filter: String?): String {
        return virtualNode.withSandbox(MESSENGER_CPB, FLOW, filter) { vns, ctx ->
            val messengerClass = vns.getFlowClass(MESSENGER_FLOW, ctx)
            vns.runFlow(messengerClass)
        }
    }

    @Test
    fun testDefaultPlatformServices() {
        assertEquals("Service One", getMessageFor(null))
    }

    @Test
    fun testFilteringForPlatformServiceOne() {
        assertEquals("Service One", getMessageFor("(|(!($PLATFORM_SERVICE_NAME=*))($PLATFORM_SERVICE_NAME=one))"))
    }

    @Test
    fun testFilteringForPlatformServiceTwo() {
        assertEquals("Service Two", getMessageFor("(|(!($PLATFORM_SERVICE_NAME=*))($PLATFORM_SERVICE_NAME=two))"))
    }

    @Suppress("unused")
    @Component(service = [ PlatformService::class ], property = [ "$PLATFORM_SERVICE_NAME=one" ])
    @ServiceRanking(Int.MAX_VALUE)
    class PlatformServiceOne : PlatformService {
        override fun getMessage(): String {
            return "Service One"
        }
    }

    @Suppress("unused")
    @Component(service = [ PlatformService::class ], property = [ "$PLATFORM_SERVICE_NAME=two" ] )
    @ServiceRanking(Int.MIN_VALUE)
    class PlatformServiceTwo : PlatformService {
        override fun getMessage(): String {
            return "Service Two"
        }
    }

    @Suppress("unused")
    @Component(
        service = [ PlatformMessageProvider::class, UsedByFlow::class ],
        scope = PROTOTYPE
    )
    class PlatformMessageProviderImpl @Activate constructor(
        @Reference
        private val platformService: PlatformService
    ): PlatformMessageProvider, UsedByFlow {
        override fun getMessage(): String {
            return platformService.message
        }
    }
}
