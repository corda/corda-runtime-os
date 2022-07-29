package net.corda.crypto.softhsm

import net.corda.crypto.component.impl.LifecycleNameProvider
import net.corda.v5.cipher.suite.CryptoServiceProvider

interface SoftCryptoServiceProvider : CryptoServiceProvider<SoftCryptoServiceConfig>, LifecycleNameProvider