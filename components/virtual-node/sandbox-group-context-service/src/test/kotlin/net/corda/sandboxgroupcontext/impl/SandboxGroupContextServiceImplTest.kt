package net.corda.sandboxgroupcontext.impl

import net.corda.install.InstallService
import net.corda.install.InstallServiceListener
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getUniqueObject
import net.corda.sandboxgroupcontext.putUniqueObject
import net.corda.sandboxgroupcontext.service.impl.SandboxGroupContextServiceImpl
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture


internal class SandboxGroupContextServiceImplTest {

    private lateinit var service: SandboxGroupContextServiceImpl
    private val holdingIdentity = HoldingIdentity("foo", "bar")
    private val mainBundle = "MAIN BUNDLE"

    private val cpks = setOf(Helpers.mockTrivialCpk(mainBundle))

    private lateinit var virtualNodeContext: VirtualNodeContext

    private class InstallServiceImpl(private val cpks: Map<CPK.Identifier, CPK>) : InstallService {
        override fun get(id: CPI.Identifier): CompletableFuture<CPI?> {
            throw NotImplementedError()
        }

        override fun get(id : CPK.Identifier): CompletableFuture<CPK?> =
            CompletableFuture.supplyAsync { cpks[id] }

        override fun getCPKByHash(hash: SecureHash): CompletableFuture<CPK?> {
            throw NotImplementedError()
        }

        override fun listCPK(): List<CPK.Metadata> {
            throw NotImplementedError()
        }

        override fun listCPI(): List<CPI.Metadata> {
            throw NotImplementedError()
        }

        override fun registerForUpdates(installServiceListener: InstallServiceListener): AutoCloseable {
            throw NotImplementedError()
        }

        override val isRunning: Boolean
            get() = throw NotImplementedError()

        override fun start() {
            throw NotImplementedError()
        }

        override fun stop() {
            throw NotImplementedError()
        }
    }

    private fun Set<CPK>.toIds() = map { it.metadata.id }.toSet()
    private fun Set<CPK>.toMap() = map { it.metadata.id to it }.toMap()

    private val cpkServiceImpl = InstallServiceImpl(cpks.toMap())

    @BeforeEach
    private fun beforeEach() {
        service = SandboxGroupContextServiceImpl(Helpers.mockSandboxCreationService(listOf(cpks)), cpkServiceImpl)
        virtualNodeContext = VirtualNodeContext(holdingIdentity, cpks.toMap().keys, SandboxGroupType.FLOW)
    }

    @Test
    fun `can create a sandbox group context without initializer`() {
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ -> AutoCloseable { } }
        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(ctx.sandboxGroup.cpks.size).isEqualTo(1)
    }

    @Test
    fun `can create a sandbox group context with initializer`() {
        var initializerCalled = false
        val ctx = service.getOrCreate(virtualNodeContext) { _, _ ->
            initializerCalled = true
            AutoCloseable { }
        }

        assertThat(virtualNodeContext).isEqualTo(ctx.virtualNodeContext)
        assertThat(ctx.sandboxGroup.cpks.size).isEqualTo(1)
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
        assertThat(ctx.sandboxGroup.cpks.size).isEqualTo(1)
        assertThat(initializerCalled).isTrue

        val actualCtx = service.getOrCreate(virtualNodeContext) { _, _ -> AutoCloseable { } }
        val actualDog = actualCtx.getUniqueObject<Dog>()

        assertThat(actualDog!!).isEqualTo(dog)
        assertThat(actualDog.noise).isEqualTo(dog.noise)
        assertThat(actualDog.noise).isEqualTo(dog.noise)

        assertThat(actualCtx.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity)
        assertThat(actualHoldingIdentity).isEqualTo(holdingIdentity)
        assertThat(actualCtx.sandboxGroup.cpks.first().metadata.mainBundle).isEqualTo(mainBundle)
    }

    @Test
    fun `can create objects with same keys in different VirtualNodeContexts`() {
        val holdingIdentity1 = HoldingIdentity("OU=1", "bar")
        val holdingIdentity2 = HoldingIdentity("OU=2", "bar")
        val holdingIdentity3 = HoldingIdentity("OU=3", "bar")

        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1"))
        val cpks2 = setOf(Helpers.mockTrivialCpk("MAIN2"))
        val cpks3 = setOf(Helpers.mockTrivialCpk("MAIN3"))

        val ctx1 = VirtualNodeContext(holdingIdentity1, cpks1.toIds(), SandboxGroupType.FLOW)
        val ctx2 = VirtualNodeContext(holdingIdentity2, cpks2.toIds(), SandboxGroupType.FLOW)
        val ctx3 = VirtualNodeContext(holdingIdentity3, cpks3.toIds(), SandboxGroupType.FLOW)

        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1, cpks2, cpks3))

        val cpkService = InstallServiceImpl(cpks1.toMap() + cpks2.toMap() + cpks3.toMap())

        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService)

        val dog1 = Dog("Rover", "Woof!")
        val dog2 = Dog("Rover", "Bark!")
        val dog3 = Dog("Rover", "Howl!")

        service.getOrCreate(ctx1) { _, mc ->
            mc.putUniqueObject(dog1)
            AutoCloseable { }
        }

        service.getOrCreate(ctx2) { _, mc ->
            mc.putUniqueObject(dog2)
            AutoCloseable { }
        }

        service.getOrCreate(ctx3) { _, mc ->
            mc.putUniqueObject(dog3)
            AutoCloseable { }
        }

        // Can get correct 'unique' object from context 1
        val sandboxGroupContext1 = service.getOrCreate(ctx1) { _, _ -> AutoCloseable { } }
        assertThat(sandboxGroupContext1.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity1)
        val actualDog1 = sandboxGroupContext1.getUniqueObject<Dog>()
        assertThat(actualDog1!!).isEqualTo(dog1)
        assertThat(actualDog1.noise).isEqualTo(dog1.noise)
        assertThat(actualDog1.noise).isNotEqualTo(dog2.noise)

        // Can get correct 'unique' object from context 2
        val sandboxGroupContext2 = service.getOrCreate(ctx2) { _, _ -> AutoCloseable { } }
        assertThat(sandboxGroupContext2.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity2)
        val actualDog2 = sandboxGroupContext2.getUniqueObject<Dog>()
        assertThat(actualDog2!!).isEqualTo(dog2)
        assertThat(actualDog2.noise).isEqualTo(dog2.noise)
        assertThat(actualDog2.noise).isNotEqualTo(dog1.noise)

        // Can get correct 'unique' object from context 3
        val sandboxGroupContext3 = service.getOrCreate(ctx3) { _, _ -> AutoCloseable { } }
        assertThat(sandboxGroupContext3.virtualNodeContext.holdingIdentity).isEqualTo(holdingIdentity3)
        val actualDog3 = sandboxGroupContext3.getUniqueObject<Dog>()
        assertThat(actualDog3!!).isEqualTo(dog3)
        assertThat(actualDog3.noise).isEqualTo(dog3.noise)
        assertThat(actualDog3.noise).isNotEqualTo(dog1.noise)
    }

    @Test
    fun `closeables work as expected`() {
        val holdingIdentity1 = HoldingIdentity("OU=1", "bar")
        val cpks1 = setOf(Helpers.mockTrivialCpk("MAIN1"))
        val ctx1 = VirtualNodeContext(holdingIdentity1, cpks1.toIds(), SandboxGroupType.FLOW)
        val sandboxCreationService = Helpers.mockSandboxCreationService(listOf(cpks1))
        val cpkService = InstallServiceImpl(cpks1.toMap())
        val service = SandboxGroupContextServiceImpl(sandboxCreationService, cpkService)
        val dog1 = Dog("Rover", "Woof!")

        var isClosed = false

        service.getOrCreate(ctx1) { _, mc ->
            mc.putUniqueObject(dog1)
            AutoCloseable { isClosed = true }
        }

        service.remove(ctx1)
        assertThat(isClosed).isTrue
    }
}
