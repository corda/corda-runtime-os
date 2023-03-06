package net.corda.membership.lib

import net.corda.v5.membership.GroupParameters

/**
 * Extension of [GroupParameters] which indicates that the group parameters are unsigned.
 */
interface UnsignedGroupParameters : InternalGroupParameters

