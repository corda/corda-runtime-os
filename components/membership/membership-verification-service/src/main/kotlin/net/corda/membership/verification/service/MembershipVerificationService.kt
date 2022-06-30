package net.corda.membership.verification.service

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * Listens and responds to verification requests sent by the MGM.
 */
interface MembershipVerificationService : Lifecycle {
    val lifecycleCoordinatorName: LifecycleCoordinatorName
}