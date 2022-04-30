package net.corda.crypto.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.Encryptor
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoService
import net.corda.crypto.service.impl.hsm.soft.SoftCryptoServiceProviderImpl
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

private val serializer = ObjectMapper()

fun createSoftHSMConfig(
    schemeMetadata: CipherSchemeMetadata,
    encryptor: Encryptor,
    tenantId: String,
    passphrase: String,
) = HSMConfig(
    HSMInfo(
        UUID.randomUUID().toString(),
        Instant.now(),
        1,
        "default",
        "Standard Soft HSM configuration for '$tenantId'",
        SoftCryptoServiceProviderImpl.SERVICE_NAME,
        null,
        CryptoConsts.HsmCategories.all(),
        0,
        5000,
        SoftCryptoService.produceSupportedSchemes(schemeMetadata).map { it.codeName }
    ),
    ByteBuffer.wrap(
        encryptor.encrypt(
            serializer.writeValueAsBytes(
                SoftCryptoServiceConfig(
                    passphrase = passphrase,
                    salt = ByteArray(32).apply { schemeMetadata.secureRandom.nextBytes(this) }.toHex()
                )
            )
        )
    )
)