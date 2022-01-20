package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.v5.cipher.suite.CryptoServiceProvider

/**
 * Factory which has [Lifecycle] to create new instances of the crypto service.
 */
interface CryptoServiceProviderWithLifecycle<T: Any> : CryptoServiceProvider<T>, Lifecycle