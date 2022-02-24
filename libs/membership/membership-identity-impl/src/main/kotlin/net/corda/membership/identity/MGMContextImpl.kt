package net.corda.membership.identity

import net.corda.layeredpropertymap.LayeredPropertyMapImpl
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.v5.membership.identity.MGMContext
import java.util.SortedMap

class MGMContextImpl(
    map: SortedMap<String, String?>,
    converter: PropertyConverter
) : LayeredPropertyMapImpl(map, converter), MGMContext