package net.corda.ledger.common.testkit

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl

fun getPrivacySaltImpl(): PrivacySaltImpl = PrivacySaltImpl("1".repeat(32).toByteArray())