package net.corda.v5.membership

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Part of [MemberInfo], MemberContext part is provided by the member as part of the initial MemberInfo proposal.
 * Contains information such as the node's endpoints, party's name.
 */
interface MemberContext: LayeredPropertyMap