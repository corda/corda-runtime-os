package net.corda.membership.impl.p2p.dummy

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.hes.EncryptedDataWithKey
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.crypto.hes.HybridEncryptionParamsProvider
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.security.PublicKey

interface TestEphemeralKeyPairEncryptor : EphemeralKeyPairEncryptor

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [EphemeralKeyPairEncryptor::class, TestEphemeralKeyPairEncryptor::class])
internal class TestEphemeralKeyPairEncryptorImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : TestEphemeralKeyPairEncryptor {
    override fun encrypt(
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        params: HybridEncryptionParamsProvider
    ): EncryptedDataWithKey {
        val ephemeralKey = cryptoOpsClient.generateKeyPair(
            "tenantId",
            PRE_AUTH,
            "alias",
            ECDSA_SECP256R1_CODE_NAME
        )
        val hybridEncryptionParamsProvider = params.get(ephemeralKey, otherPublicKey)
        return EncryptedDataWithKey(
            ephemeralKey,
            plainText,
            HybridEncryptionParams(hybridEncryptionParamsProvider.salt, hybridEncryptionParamsProvider.aad)
        )
    }
}