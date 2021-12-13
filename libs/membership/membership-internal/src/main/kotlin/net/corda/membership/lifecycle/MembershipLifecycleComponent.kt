package net.corda.membership.lifecycle

import net.corda.membership.config.MembershipConfig

interface MembershipLifecycleComponent {

    fun handleConfigEvent(config: MembershipConfig)
}
