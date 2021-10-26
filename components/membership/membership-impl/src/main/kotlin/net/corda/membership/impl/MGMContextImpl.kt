package net.corda.membership.impl

import net.corda.v5.membership.converter.PropertyConverter
import net.corda.v5.membership.identity.MGMContext
import java.util.SortedMap

class MGMContextImpl(
    map: SortedMap<String, String?>,
    converter: PropertyConverter
) : LayeredPropertyMapImpl(map, converter), MGMContext