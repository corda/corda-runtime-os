package net.corda.libs.packaging.core

import net.corda.v5.crypto.SecureHash

interface Identifier {
    val name: String
    val version: String
    val signerSummaryHash: SecureHash?
}