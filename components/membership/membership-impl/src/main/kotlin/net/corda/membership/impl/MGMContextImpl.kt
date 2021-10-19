package net.corda.membership.impl

import net.corda.v5.membership.identity.MGMContext
import net.corda.v5.membership.identity.parser.ObjectConverter
import java.util.SortedMap

class MGMContextImpl(
    map: SortedMap<String, String?>,
    converter: ObjectConverter
) : KeyValueStoreImpl(map, converter), MGMContext