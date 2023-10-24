package net.corda.sandboxgroupcontext.test

import java.nio.file.Path
import java.util.UUID
import java.util.stream.Stream
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.testing.PlatformMessageProvider
import net.corda.v5.testing.uuid.UUIDProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
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
class NonInjectableSingletonTest {
    companion object {
        private const val MESSENGER_CPB = "META-INF/sandbox-messenger-cpk.cpb"
        private const val MESSENGER_FLOW = "com.example.messenger.PlatformMessengerFlow"
        private const val TEST_VALUE_ZERO = "test.value:Integer=0"
        private const val TEST_VALUE_ONE = "test.value:Integer=1"
        private const val TIMEOUT_MILLIS = 10000L

        private val ZERO_UUID = UUID(0, 0)
        private val ONE_UUID = UUID(1, 1)
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

    private fun getMessageFor(filter: String?, expectedUUIDProviders: Int): String {
        return virtualNode.withSandbox(MESSENGER_CPB, FLOW, filter) { vns, ctx ->
            assertThat(ctx.getSandboxSingletonServices<UUIDProvider>())
                .describedAs("Number of UUIDProvider singletons")
                .hasSize(expectedUUIDProviders)
            assertThat(ctx.getSandboxSingletonServices<InternalService>())
                .describedAs("Number of InternalService singletons")
                .hasSize(1)

            val messengerClass = vns.getFlowClass(MESSENGER_FLOW, ctx)
            val bundleContext = vns.getBundleContext(messengerClass)
            assertThat(bundleContext.bundle.location).startsWith("FLOW/")

            val internalServices = bundleContext.getServiceReferences(InternalService::class.java.name, null)
            assertThat(internalServices)
                .withFailMessage("NonInjectable InternalService services %s registered inside sandbox", internalServices)
                .isNullOrEmpty()

            val uuidProviders = bundleContext.getServiceReferences(UUIDProvider::class.java.name, null)
            assertThat(uuidProviders)
                .withFailMessage("NonInjectable UUIDProvider services %s registered inside sandbox", uuidProviders)
                .isNullOrEmpty()

            vns.runFlow(messengerClass)
        }
    }

    private class UUIDArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of("(test.value=0)", 1, ZERO_UUID.toString()),
                Arguments.of("(test.value=1)", 1, ONE_UUID.toString()),
                Arguments.of("(test.value=*)", 2, "$ONE_UUID,$ZERO_UUID")
            )
        }
    }

    @ParameterizedTest
    @ArgumentsSource(UUIDArgumentProvider::class)
    fun testFilteringForUUIDProvider(filter: String?, expectedUUIDProviders: Int, expectedMessage: String) {
        assertEquals(expectedMessage, getMessageFor(filter, expectedUUIDProviders))
    }

    @Suppress("unused")
    @Component(
        service = [ PlatformMessageProvider::class, UsedByFlow::class ],
        property = [ TEST_VALUE_ZERO, TEST_VALUE_ONE ],
        scope = PROTOTYPE
    )
    @ServiceRanking(Int.MIN_VALUE / 2)
    class PlatformUUIDMessageProviderImpl @Activate constructor(
        @Reference
        private val internalService: InternalService
    ) : PlatformMessageProvider, UsedByFlow {
        override fun getMessage(): String {
            return internalService.getMessage()
        }
    }

    fun interface InternalService {
        fun getMessage(): String
    }

    @Suppress("unused")
    @Component(
        service = [ InternalService::class, UsedByFlow::class ],
        property = [ TEST_VALUE_ZERO, TEST_VALUE_ONE, CORDA_UNINJECTABLE_SERVICE ],
        scope = PROTOTYPE
    )
    @ServiceRanking(Int.MIN_VALUE / 2)
    class UUIDInternalServiceImpl @Activate constructor(
        @Reference
        private val uuidProviders: List<UUIDProvider>
    ) : InternalService, UsedByFlow {
        override fun getMessage(): String {
            // These services are in decreasing service ranking order.
            // This is a consequence of how ServiceDefinition is implemented
            // rather than being an OSGi requirement.
            return uuidProviders.map(UUIDProvider::getUUID).joinToString(",")
        }
    }

    @Suppress("unused")
    @Component(
        service = [ InternalService::class, UsedByFlow::class ],
        property = [ CORDA_UNINJECTABLE_SERVICE ],
        scope = PROTOTYPE
    )
    class DefaultInternalServiceImpl : InternalService, UsedByFlow {
        override fun getMessage(): String {
            return "NOT THIS ONE!"
        }
    }

    @Suppress("unused")
    @Component(
        service = [ UUIDProvider::class, UsedByFlow::class ],
        property = [ TEST_VALUE_ONE, CORDA_UNINJECTABLE_SERVICE ],
        scope = PROTOTYPE
    )
    @ServiceRanking(Int.MIN_VALUE / 2)
    class OneUUIDProviderImpl : UUIDProvider, UsedByFlow {
        override fun getUUID(): UUID {
            return ONE_UUID
        }
    }

    @Suppress("unused")
    @Component(
        service = [ UUIDProvider::class, UsedByFlow::class ],
        property = [ TEST_VALUE_ZERO, CORDA_UNINJECTABLE_SERVICE ],
        scope = PROTOTYPE
    )
    @ServiceRanking(Int.MIN_VALUE)
    class ZeroUUIDProviderImpl : UUIDProvider, UsedByFlow {
        override fun getUUID(): UUID {
            return ZERO_UUID
        }
    }
}
