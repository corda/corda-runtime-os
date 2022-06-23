package net.corda.membership.verification.service

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * Listens and responds to verification requests from the MGM.
 */
interface VerificationService : Lifecycle {
    val lifecycleCoordinatorName: LifecycleCoordinatorName
}