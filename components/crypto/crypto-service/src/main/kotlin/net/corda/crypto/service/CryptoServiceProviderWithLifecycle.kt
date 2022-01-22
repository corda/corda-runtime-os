package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.v5.cipher.suite.CryptoServiceProvider

/**
 * Marker interface for component implementing [CryptoServiceProvider] to specify that they support
 * the [Lifecycle] as well.
 */
interface CryptoServiceProviderWithLifecycle : Lifecycle