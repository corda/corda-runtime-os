package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.helpers.BasicMocks
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.db.persistence.testkit.helpers.SandboxHelper.createDog
import net.corda.persistence.common.EntitySandboxServiceFactory
import net.corda.persistence.common.getSerializationService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.serialization.deserialize
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
            EntitySandboxServiceFactory().create(
                virtualNode.sandboxGroupContextComponent,
                cpiInfoReadService,
                virtualNodeInfoReadService,
                BasicMocks.dbConnectionManager()
            )

        val sandbox = entitySandboxService.get(virtualNodeInfo.holdingIdentity)

        val expectedDog = sandbox.createDog( "Rover")
        val bytes = sandbox.getSerializationService().serialize(expectedDog.instance)

        val actualDog = sandbox.getSerializationService().deserialize(bytes)

        assertThat(actualDog).isEqualTo(expectedDog.instance)
    }
}
