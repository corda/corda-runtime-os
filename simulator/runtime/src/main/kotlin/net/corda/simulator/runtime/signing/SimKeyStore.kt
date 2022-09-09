package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import java.security.PublicKey

interface SimKeyStore {
    fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey
    fun getParameters(publicKey: PublicKey): KeyParameters?
}
