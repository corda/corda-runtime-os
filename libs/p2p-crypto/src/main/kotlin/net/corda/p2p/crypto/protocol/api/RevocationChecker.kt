package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse

interface RevocationChecker {
    fun checkRevocation(request: RevocationCheckRequest): RevocationCheckResponse
}