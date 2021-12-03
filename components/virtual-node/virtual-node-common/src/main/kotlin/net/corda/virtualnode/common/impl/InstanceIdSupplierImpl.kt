package net.corda.virtualnode.common.impl

import net.corda.virtualnode.common.InstanceId
import net.corda.virtualnode.common.InstanceIdSupplier
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [InstanceIdSupplier::class, InstanceId::class])
class InstanceIdSupplierImpl @Activate constructor(): InstanceIdSupplier, InstanceId {
    private var value : Int? = null

    override fun get(): Int? = value

    override fun set(instanceId: Int?) {
        value = instanceId
    }
}
