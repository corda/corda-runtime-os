package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature

/**
 * A wrapper around a digital signature used for notarisation requests.
 *
 * The [platformVersion] is required so the notary can verify the signature against the right version of serialized
 * bytes of the [NotarisationRequest]. Otherwise, the request may be rejected.
 */
@CordaSerializable
data class NotarisationRequestSignature(
    val digitalSignature: DigitalSignature.WithKey,
    val platformVersion: Int
)
