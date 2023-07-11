package net.corda.testing.driver.impl

import java.time.Duration
import net.corda.testing.driver.node.RunFlow
import net.corda.testing.driver.function.ThrowingSupplier
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro

internal class FlowRunner(private val dsl: DriverInternalDSL) {
    fun runFlow(
        virtualNodeInfo: VirtualNodeInfo,
        flowClass: Class<*>,
        flowArgMapper: ThrowingSupplier<String>,
        timeout: Duration
    ): String? {
        return try {
            dsl.framework.getService(RunFlow::class.java, null, timeout).andThen { runner ->
                runner.runFlow(virtualNodeInfo.toAvro(), flowClass.name, flowArgMapper.get())
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }
}
