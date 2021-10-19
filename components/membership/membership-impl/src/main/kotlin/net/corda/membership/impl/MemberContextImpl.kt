package net.corda.membership.impl

import net.corda.v5.membership.identity.MemberContext
import net.corda.v5.membership.identity.parser.ObjectConverter
import java.util.SortedMap

class MemberContextImpl(
    map: SortedMap<String, String?>,
    converter: ObjectConverter
) : KeyValueStoreImpl(map, converter), MemberContext

