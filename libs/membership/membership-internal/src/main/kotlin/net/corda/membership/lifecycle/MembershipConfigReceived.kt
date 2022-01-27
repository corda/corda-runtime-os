package net.corda.membership.lifecycle

import net.corda.lifecycle.LifecycleEvent
import net.corda.membership.config.MembershipConfig

data class MembershipConfigReceived(val config: MembershipConfig) : LifecycleEvent