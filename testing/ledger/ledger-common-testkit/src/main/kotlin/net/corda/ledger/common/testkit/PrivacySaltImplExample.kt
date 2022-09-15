package net.corda.ledger.common.testkit

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl

class PrivacySaltImplExample {
    companion object{
        fun getPrivacySaltImpl(): PrivacySaltImpl = PrivacySaltImpl("1".repeat(32).toByteArray())
    }
}