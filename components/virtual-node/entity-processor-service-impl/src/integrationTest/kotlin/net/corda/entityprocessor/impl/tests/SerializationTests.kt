package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.entityprocessor.impl.internal.EntitySandboxServiceImpl
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.entityprocessor.impl.tests.helpers.BasicMocks
import net.corda.entityprocessor.impl.tests.helpers.Resources
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.createDog
import net.corda.entityprocessor.impl.tests.helpers.SandboxHelper.getSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.application.serialization.deserialize
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Check that we can get the serializer from the 'internal' sandbox class once it's been created.
 *
 */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SerializationTests {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

    @BeforeAll
    fun setup(
        @InjectService(timeout = 5000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            // TODO - look at using generic fake implementations for these.
            virtualNode = setup.fetchService(timeout = 5000)
            cpiInfoReadService = setup.fetchService(timeout = 5000)
            virtualNodeInfoReadService = setup.fetchService(timeout = 5000)
        }
    }

    @Test
    fun `use sandbox serializer`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)

        val entitySandboxService =
            EntitySandboxServiceImpl(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                BasicMocks.dbConnectionManager(),
                BasicMocks.componentContext()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        val expectedDog = sandbox.createDog( "Rover")
        val bytes = sandbox.getSerializer().serialize(expectedDog.instance)

        val actualDog = sandbox.getSerializer().deserialize(bytes)

        assertThat(actualDog).isEqualTo(expectedDog.instance)
    }
}
