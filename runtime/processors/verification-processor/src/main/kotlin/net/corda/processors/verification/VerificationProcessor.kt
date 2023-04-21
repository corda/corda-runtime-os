package net.corda.processors.verification

import net.corda.libs.configuration.SmartConfig

/** Verification processor is used for verification of ledger transaction contracts within Verification Sandbox. */
interface VerificationProcessor {
    /** Starts Verification Processor. */
    fun start(bootConfig: SmartConfig)

    /** Stops Verification Processor. */
    fun stop()
}