package net.corda.flow.testing.sandbox

import net.bytebuddy.implementation.bind.MethodDelegationBinder.MethodInvoker.Virtual
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class CreateSandboxTest {

    @InjectService
    lateinit var sandboxGroupContextComponent: SandboxGroupContextComponent

    @Test
    fun `create sandboxes`() {
        println("Sandbox created")

        // NEED TO CREATE SANDBOX GROUP CONTEXT FOR FLOWS


        // need to generate a holding identity
        val holdingIdentity = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=R3"), "dummy")
        val cpkFileHashes = emptySet<SecureHash>()
        val vNodeContext = VirtualNodeContext(holdingIdentity, cpkFileHashes, SandboxGroupType.FLOW, null)

        // cpks need to be loaded into the sandbox group context component

        val sandboxGroupContext = sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, _ ->
            AutoCloseable { }
        }

        println(sandboxGroupContext)
    }
}