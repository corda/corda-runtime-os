package net.corda.v5.ledger.obsolete.services.vault

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class StateStatus {
    UNCONSUMED, CONSUMED
}