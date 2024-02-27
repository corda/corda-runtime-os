package net.corda.membership.network.writer.staticnetwork

import net.corda.crypto.cipher.suite.SignatureSpecs
import org.bouncycastle.jce.provider.BouncyCastleProvider

object StaticNetworkUtils {

    val mgmSigningKeyAlgorithm
        get() = "RSA"

    val mgmSignatureSpec
        get() = SignatureSpecs.RSA_SHA256

    val mgmSigningKeyProvider
        get() = BouncyCastleProvider()
}
