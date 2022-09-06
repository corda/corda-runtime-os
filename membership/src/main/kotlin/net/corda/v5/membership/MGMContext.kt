package net.corda.v5.membership

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Part of [MemberInfo], information is provided and added by MGM as part of member acceptance and upon updates
 * (eg. membership group status updates).
 *
 * Contains information such as the membership status, modification time, etc.
 *
 * Properties are exposed either through properties on interfaces in the public APIs, or internally through extension
 * properties.
 */
interface MGMContext: LayeredPropertyMap