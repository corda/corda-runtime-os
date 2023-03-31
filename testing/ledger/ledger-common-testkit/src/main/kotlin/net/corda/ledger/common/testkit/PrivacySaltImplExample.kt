package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.PrivacySalt
import kotlin.random.Random

fun getPrivacySalt(): PrivacySalt = PrivacySaltImpl(Random.nextBytes(32))