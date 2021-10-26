package net.corda.membership.impl

import net.corda.v5.membership.converter.PropertyConverter
import net.corda.v5.membership.identity.MemberContext
import java.util.SortedMap

class MemberContextImpl(
    map: SortedMap<String, String?>,
    converter: PropertyConverter
) : LayeredPropertyMapImpl(map, converter), MemberContext

