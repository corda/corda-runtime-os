package net.corda.crypto.delegated.signing

import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

enum class Hash(val hashName: String, private val saltLength: Int) : SigningParameter {
    SHA256("SHA-256", 32),
    SHA384("SHA-384", 48),
    SHA512("SHA-512", 64);

    val rsaParameter by lazy {
        PSSParameterSpec(
            hashName,
            "MGF1",
            MGF1ParameterSpec(hashName),
            saltLength,
            1
        )
    }

    val ecName = "${name}withECDSA"
}
