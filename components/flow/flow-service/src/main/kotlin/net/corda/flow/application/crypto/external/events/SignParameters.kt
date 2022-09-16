package net.corda.flow.application.crypto.external.events

import java.security.PublicKey
import net.corda.v5.crypto.SignatureSpec

data class SignParameters(val bytes: ByteArray, val publicKey: PublicKey, val signatureSpec: SignatureSpec)