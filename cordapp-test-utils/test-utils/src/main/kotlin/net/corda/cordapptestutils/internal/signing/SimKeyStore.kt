package net.corda.cordapptestutils.internal.signing

import net.corda.cordapptestutils.crypto.HsmCategory
import java.security.PublicKey

interface SimKeyStore {
    fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey
    fun getParameters(publicKey: PublicKey): KeyParameters?
}
