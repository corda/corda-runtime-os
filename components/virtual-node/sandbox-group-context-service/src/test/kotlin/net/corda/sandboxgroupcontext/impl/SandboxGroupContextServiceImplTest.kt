package net.corda.sandboxgroupcontext.impl

import net.corda.cpk.read.CpkReadService
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.Cpk
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.putUniqueObject
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextServiceImpl
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.osgi.framework.BundleContext
import org.osgi.service.component.runtime.ServiceComponentRuntime

class SandboxGroupContextServiceImplTest {

    private lateinit var service: SandboxGroupContextServiceImpl
    private val holdingIdentity = createTestHoldingIdentity("CN=Foo, O=Foo Corp, L=LDN, C=GB", "bar")
    private val mainBundle = "MAIN BUNDLE"

    private val scr = mock<ServiceComponentRuntime>()
    private val bundleContext = mock<BundleContext>()
    private val cpks = setOf(Helpers.mockTrivialCpk(mainBundle, "example", "1.0.0"))

    private lateinit var virtualNodeContext: VirtualNodeContext

    private fun createVirtualNodeContextForFlow(holdingIdentity: HoldingIdentity, cpkFileChecksums: Set<SecureHash>):
            VirtualNodeContext {
        return VirtualNodeContext(
            holdingIdentity,
            cpkFileChecksums,
            SandboxGroupType.FLOW,
            null
        )
    }

    class CpkReadServiceFake(private val cpks: Set<Cpk>) : CpkReadService {
        override fun get(cpkFileChecksum: SecureHash): Cpk? {
            return cpks.singleOrNull { (it.metadata.fileChecksum == cpkFileChecksum) }
        }

        override val isRunning: Boolean
            get() = true

        override fun start() {
        }

        override fun stop() {
        }
    }

    private val cpkServiceImpl = CpkReadServiceFake(cpks)

    @BeforeEach
    fun beforeEach() {
        service = SandboxGroupContextServiceImpl(
            Helpers.mockSandboxCreationService(listOf(cpks)),
            cpkServiceImpl,
            scr,
            bundleContext
        )
        service.initCaches(1)
        virtualNodeContext = createVirtualNodeContextForFlow(
            holdingIdentity,
            cpks.mapTo(mutableSetOf()) { it.metadata.fileChecksum }
        )
    }

    @Test
    fun `can create a sandbox group context without initializer`() {
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ -> AutoCloseable { } }
        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
    }

    @Test
    fun `can create a sandbox group context with initializer`() {
        var initializerCalled = false
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ ->
            initializerCalled = true
            AutoCloseable { }
        }

        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(initializerCalled).isTrue
    }

    data class Dog(val name: String, val noise: String)

    @Test
    fun `can create add objects during initializer`() {
        var initializerCalled = false
        val dog = Dog("Rover", "Woof!")
        var actualHoldingIdentity: HoldingIdentity? = null
        val ctx = service.getOrCreate(virtualNodeContext) { holdingIdentity, mutableContext ->
            initializerCalled = true
            actualHoldingIdentity = holdingIdentity
            mutableContext.putUniqueObject(dog)
            AutoCloseable { }
        }

        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(initializerCalled).isTrue

        val actualDog = ctx.getUniqueObject<Dog>()

        assertThat(actualDog!!).isEqualTo(dog)
        assertThat(actualDog.noise).isEqualTo(dog.noise)
        assertThat(actualDog.noise).isEqualTo(dog.noise)

        assertThat(ctx.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity)
        assertThat(actualHoldingIdentity).isEqualTo(holdingIdentity)
    }

    @Test
    fun `can create objects with same keys in different VirtualNodeContexts`() {
        val holdingIdentity1 = createTestHoldingIdentity("CN=Foo-1, O=Foo Corp, L=LDN, C=GB", "bar")
        val holdingIdentity2 = createTestHoldingIdentity("CN=Foo-2, O=Foo Corp, L=LDN, C=GB", "bar")
        val holdingIdentity3 = createTestHoldingIdentity("CN=Foo-3, O=Foo Corp, L=LDN, C=GB", "bar")

        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1", "apple", "1.0.0"))
        val cpks2 = setOf(Helpers.mockTrivialCpk("MAIN2", "banana", "2.0.0"))
        val cpks3 = setOf(Helpers.mockTrivialCpk("MAIN3", "cranberry", "3.0.0"))

        val ctx1 = createVirtualNodeContextForFlow(
            holdingIdentity1,
            cpks1.map { it.metadata.fileChecksum }.toSet()
        )
        val ctx2 = createVirtualNodeContextForFlow(
            holdingIdentity2,
            cpks2.map { it.metadata.fileChecksum }.toSet()
        )
        val ctx3 = createVirtualNodeContextForFlow(
            holdingIdentity3,
            cpks3.map { it.metadata.fileChecksum }.toSet()
        )

        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1, cpks2, cpks3))

        val cpkService = CpkReadServiceFake(cpks1 + cpks2 + cpks3)

        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext).apply {
            initCaches(1)
        }

        val dog1 = Dog("Rover", "Woof!")
        val dog2 = Dog("Rover", "Bark!")
        val dog3 = Dog("Rover", "Howl!")

        val sandboxGroupContext1 = service.getOrCreate(ctx1) { _, mc ->
            mc.putUniqueObject(dog1)
            AutoCloseable { }
        }

        val sandboxGroupContext2 = service.getOrCreate(ctx2) { _, mc ->
            mc.putUniqueObject(dog2)
            AutoCloseable { }
        }

        val sandboxGroupContext3 = service.getOrCreate(ctx3) { _, mc ->
            mc.putUniqueObject(dog3)
            AutoCloseable { }
        }

        // Can get correct 'unique' object from context 1
        assertThat(sandboxGroupContext1.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity1)
        val actualDog1 = sandboxGroupContext1.getUniqueObject<Dog>()
        assertThat(actualDog1!!).isEqualTo(dog1)
        assertThat(actualDog1.noise).isEqualTo(dog1.noise)
        assertThat(actualDog1.noise).isNotEqualTo(dog2.noise)

        // Can get correct 'unique' object from context 2
        assertThat(sandboxGroupContext2.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity2)
        val actualDog2 = sandboxGroupContext2.getUniqueObject<Dog>()
        assertThat(actualDog2!!).isEqualTo(dog2)
        assertThat(actualDog2.noise).isEqualTo(dog2.noise)
        assertThat(actualDog2.noise).isNotEqualTo(dog1.noise)

        // Can get correct 'unique' object from context 3
        assertThat(sandboxGroupContext3.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity3)
        val actualDog3 = sandboxGroupContext3.getUniqueObject<Dog>()
        assertThat(actualDog3!!).isEqualTo(dog3)
        assertThat(actualDog3.noise).isEqualTo(dog3.noise)
        assertThat(actualDog3.noise).isNotEqualTo(dog1.noise)
    }

    @Test
    fun `remove removes from cache`() {
        val holdingIdentity1 = createTestHoldingIdentity("CN=Foo, O=Foo Corp, L=LDN, C=GB", "bar")
        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1", "example", "1.0.0"))
        val ctx1 = createVirtualNodeContextForFlow(
            holdingIdentity1,
            cpks1.map { parseSecureHash("DUMMY:1234567890abcdef") }.toSet()
        )
        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1))
        val cpkService = CpkReadServiceFake(cpks1)
        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext)

        val ex = assertThrows<IllegalStateException> { service.remove(ctx1) }
        assertThat(ex).hasMessageStartingWith("remove: ")
    }

    @Test
    fun `assert hasCpks`() {
        val existingCpks = setOf(
            Helpers.mockTrivialCpk("MAIN1", "apple", "1.0.0"),
            Helpers.mockTrivialCpk("MAIN2", "banana", "2.0.0"),
            Helpers.mockTrivialCpk("MAIN3", "cranberry", "3.0.0")
        )
        val nonExistingCpk = setOf(Helpers.mockTrivialCpk("MAIN4", "orange", "4.0.0"))

        val service = existingCpks.let {
            val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(it))
            val cpkService = CpkReadServiceFake(it)
            SandboxGroupContextServiceImpl(sandboxCreationService, cpkService, scr, bundleContext)
        }

        val existingCpkChecksums = existingCpks.map {
            it.metadata.fileChecksum
        }.toSet()

        val nonExistingCpkChecksums = nonExistingCpk.map { it.metadata.fileChecksum }.toSet()

        val noCpks = emptySet<SecureHash>()

        assertTrue(service.hasCpks(existingCpkChecksums))
        assertFalse(service.hasCpks(nonExistingCpkChecksums))
        assertTrue(service.hasCpks(noCpks))
    }
}
