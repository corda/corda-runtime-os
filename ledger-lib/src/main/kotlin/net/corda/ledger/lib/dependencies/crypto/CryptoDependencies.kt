package net.corda.ledger.lib.dependencies.crypto

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.cipher.suite.impl.SignatureVerificationServiceImpl
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.merkle.impl.MerkleProofFactoryImpl
import net.corda.crypto.merkle.impl.MerkleProofProviderImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.crypto.softhsm.impl.SoftCryptoService
import net.corda.flow.application.crypto.SignatureSpecServiceImpl
import net.corda.ledger.lib.dependencies.signing.SigningDependencies.wrappingRepositoryFactory

object CryptoDependencies {
    // ------------------------------------------------------------------------
    // ALL GOOD
    val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    val platformDigestService = PlatformDigestServiceImpl(cipherSchemeMetadata)
    val digestService = DigestServiceImpl(
        PlatformDigestServiceImpl(cipherSchemeMetadata),
        null
    )

    val signatureSpecService = SignatureSpecServiceImpl(cipherSchemeMetadata)

    val merkleProofProvider = MerkleProofProviderImpl()
    val merkleTreeProvider = MerkleTreeProviderImpl(digestService)

    val signatureVerificationService = SignatureVerificationServiceImpl(
        cipherSchemeMetadata,
        digestService
    )

    val merkleProofFactory = MerkleProofFactoryImpl()

    // ------------------------------------------------------------------------
}