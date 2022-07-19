package net.corda.crypto.service.softhsm

import net.corda.crypto.service.LifecycleNameProvider
import net.corda.v5.cipher.suite.CryptoServiceProvider

interface SoftCryptoServiceProvider : CryptoServiceProvider<SoftCryptoServiceConfig>, LifecycleNameProvider