package net.corda.sandboxes.stresstest

import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.nio.file.Path
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension


internal const val CPI_ONE = "META-INF/sandbox-cpk-one.cpb"
internal const val CPI_THREE = "META-INF/sandbox-cpk-three.cpb"


@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SandboxStressTestSample {
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var sandboxFactory: SandboxFactory

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
            sandboxFactory = setup.fetchService(timeout = 1000)
        }
    }

    @Test
    fun `create a sandbox for 10 virtual nodes`() {
        println(sandboxFactory.group1.id)
    }
}

@Component(service = [ SandboxFactory::class ])
class SandboxFactory @Activate constructor(
    @Reference
    private val cpiLoader: CpiLoader,
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    val sandboxContextService: SandboxContextService
) {
    val group1 = createSandboxGroupFor(CPI_ONE)
    val group2 = createSandboxGroupFor(CPI_THREE)

    fun createSandboxGroupFor(cpiResource: String): SandboxGroup {
        val cpi = cpiLoader.loadCPI(cpiResource)
        return sandboxCreationService.createSandboxGroup(cpi.cpks)
    }

    fun destroySandboxGroup(group: SandboxGroup) {
        sandboxCreationService.unloadSandboxGroup(group)
    }

    @Suppress("unused")
    @Deactivate
    fun done() {
        destroySandboxGroup(group1)
        destroySandboxGroup(group2)
    }
}