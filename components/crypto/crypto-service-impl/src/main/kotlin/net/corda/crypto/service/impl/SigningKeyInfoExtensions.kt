package net.corda.crypto.service.impl

import java.nio.ByteBuffer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.SigningKeyInfo
import net.corda.data.crypto.wire.CryptoSigningKey

fun SigningKeyInfo.toCryptoSigningKey(keyEncodingService: KeyEncodingService): CryptoSigningKey = CryptoSigningKey(
    this.id.value,
    this.tenantId,
    this.category,
    this.alias,
    this.hsmAlias,
    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(this.publicKey)),
    this.schemeCodeName,
    this.wrappingKeyAlias,
    this.encodingVersion,
    this.externalId,
    this.timestamp
)
