@file:JvmName("DriverHelpers")
package net.corda.testing.driver

import net.corda.testing.driver.function.ThrowingSupplier
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.virtualnode.VirtualNodeInfo

inline fun <reified T: ClientStartableFlow> DriverDSL.runFlow(
    vNode: VirtualNodeInfo,
    flowArgMapper: ThrowingSupplier<String>
): String? {
    return runFlow(vNode, T::class.java, flowArgMapper)
}
