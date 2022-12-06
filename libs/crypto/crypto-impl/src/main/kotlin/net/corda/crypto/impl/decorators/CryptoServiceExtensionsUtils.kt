package net.corda.crypto.impl.decorators

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions

val CryptoService.requiresWrappingKey: Boolean get() =
    extensions.contains(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)

val CryptoService.supportsKeyDelete: Boolean get() =
    extensions.contains(CryptoServiceExtensions.DELETE_KEYS)

val CryptoService.supportsSharedSecretDerivation: Boolean get() =
    extensions.contains(CryptoServiceExtensions.SHARED_SECRET_DERIVATION)