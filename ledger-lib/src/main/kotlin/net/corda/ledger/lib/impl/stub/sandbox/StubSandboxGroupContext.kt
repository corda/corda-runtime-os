package net.corda.ledger.lib.impl.stub.sandbox

import net.corda.crypto.core.SecureHashImpl
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.CompletableFuture

class StubSandboxGroupContext : SandboxGroupContext {

    override val sandboxGroup: SandboxGroup
        get() = StubSandboxGroup()


    override fun <T : Any> get(key: String, valueType: Class<out T>): T? {
        TODO("Not yet implemented")
    }

    override val completion: CompletableFuture<Boolean>
        get() = TODO("Not yet implemented")
    override val virtualNodeContext: VirtualNodeContext
        get() = VirtualNodeContext(
            HoldingIdentity(
                MemberX500Name("Alice", "Alice Corp", "LDN", "GB"),
                "1"
            ),
            setOf(CPK1_CHECKSUM),
            SandboxGroupType.FLOW,
            null
        )

    private val CPK1_CHECKSUM = SecureHashImpl("ALG", byteArrayOf(0, 0, 0, 0))
}
