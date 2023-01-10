@file:JvmName("FlowDriverDSL")
package net.corda.testing.driver.flow

import java.util.function.Supplier
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.flow.api.RunFlow
import net.corda.testing.driver.impl.DriverDSLImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro

private const val TIMEOUT_MILLIS = 10000L

fun DriverDSL.runFlow(virtualNodeInfo: VirtualNodeInfo, flowClass: Class<*>, flowArgMapper: Supplier<String>): String? {
    val memberX500Name = virtualNodeInfo.holdingIdentity.x500Name
    return try {
        (this as DriverDSLImpl).getFramework(memberX500Name).getService(RunFlow::class.java, TIMEOUT_MILLIS).andThen { runner ->
            runner.runFlow(virtualNodeInfo.toAvro(), flowClass.name, flowArgMapper.get())
        }
    } catch (e: RuntimeException) {
        throw e
    } catch (e: Exception) {
        throw CordaRuntimeException(e::class.java.name, e.message, e)
    }
}
