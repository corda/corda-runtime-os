package net.corda.membership.lib.impl

import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.toMap
import net.corda.v5.membership.GroupParameters
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [GroupParametersFactory::class])
class GroupParametersFactoryImpl @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory
) : GroupParametersFactory {
    override fun create(parameters: KeyValuePairList): GroupParameters =
        GroupParametersImpl(layeredPropertyMapFactory.createMap(parameters.toMap()))
}
