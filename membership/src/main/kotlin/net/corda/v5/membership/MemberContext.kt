package net.corda.v5.membership

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Part of [MemberInfo], MemberContext part is provided by the member as part of the initial MemberInfo proposal (i.e.
 * group registration).
 *
 * Contains information such as the node's endpoints, x500 name, key information, etc.
 *
 * Properties are exposed either through properties on interfaces in the public APIs, or internally through extension
 * properties.
 */
interface MemberContext: LayeredPropertyMap