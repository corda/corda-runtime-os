package net.corda.ledger.lib.dependencies.signing

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.v5.base.types.LayeredPropertyMap

class StubLayeredPropertyMapFactory : LayeredPropertyMapFactory {
    // TODO We don't have any custom converters, is that OK?
    override fun createMap(properties: Map<String, String?>): LayeredPropertyMap {
        return LayeredPropertyMapImpl(properties, PropertyConverter(emptyMap()))
    }
}