package net.corda.membership.impl.lifecycle

import net.corda.lifecycle.LifecycleEvent
import net.corda.membership.config.MembershipConfig

class NewMembershipConfigReceived(
    val config: MembershipConfig
) : LifecycleEvent
