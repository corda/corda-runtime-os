package net.corda.testing.driver.impl

import java.time.Duration
import net.corda.testing.driver.node.RunFlow
import net.corda.testing.driver.function.ThrowingSupplier
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro

class FlowRunner(private val dsl: DriverInternalDSL) {
    fun runFlow(
        virtualNodeInfo: VirtualNodeInfo,
        flowClass: Class<*>,
        flowArgMapper: ThrowingSupplier<String>,
        timeout: Duration
    ): String? {
        val memberX500Name = virtualNodeInfo.holdingIdentity.x500Name
        return try {
            dsl.getFramework(memberX500Name).getService(RunFlow::class.java, timeout).andThen { runner ->
                runner.runFlow(virtualNodeInfo.toAvro(), flowClass.name, flowArgMapper.get())
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }
}
