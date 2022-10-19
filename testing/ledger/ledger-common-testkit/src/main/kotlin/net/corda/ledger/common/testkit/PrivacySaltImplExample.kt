package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.v5.ledger.common.transaction.PrivacySalt

fun getPrivacySalt(): PrivacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())