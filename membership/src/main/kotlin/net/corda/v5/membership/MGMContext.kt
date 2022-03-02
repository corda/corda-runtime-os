package net.corda.v5.membership

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Part of [MemberInfo], information is provided and added by MGM as part of member acceptance and upon updates
 * (eg. membership group status updates).
 */
interface MGMContext: LayeredPropertyMap