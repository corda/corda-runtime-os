package net.corda.entityprocessor.impl.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpk.read.CpkReadService
import net.corda.entityprocessor.impl.tests.components.VirtualNodeService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Reference
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

/**
 * Test that the CPB that is loaded only exposes javax `@Entity` annotated classes in `cpk` and not `jar` files.
 * */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpbEntityTests {
    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService
    private lateinit var cpiInfoReadService: CpiInfoReadService

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 1000)
            cpiInfoReadService = setup.fetchService(timeout = 1000)
        }
    }

    @Test
    fun `entities in cpks are listed`() {
        val virtualNodeInfo = virtualNode.load(Resources.EXTENDABLE_CPB)
        val cpks = cpiInfoReadService.get(virtualNodeInfo.cpiIdentifier)!!.cpksMetadata
        val entities = cpks.flatMap { it.cordappManifest.entities }

        assertThat(entities.isNotEmpty()).isTrue

        // does contain packages from cpks
        assertThat(entities).containsAll(
            listOf("net.corda.testing.bundles.dogs.Dog", "net.corda.testing.bundles.cats.Cat", "net.corda.testing.bundles.cats.Owner")
        )
    }
}
