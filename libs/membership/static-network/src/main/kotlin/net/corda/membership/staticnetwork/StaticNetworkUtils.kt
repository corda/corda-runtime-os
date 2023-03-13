package net.corda.membership.staticnetwork

import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider

object StaticNetworkUtils {

    val mgmSigningKeyAlgorithm
        get() = "RSA"

    val mgmSignatureSpec
        get() = SignatureSpec.RSA_SHA256

    val mgmSigningKeyProvider
        get() = BouncyCastleProvider()
}