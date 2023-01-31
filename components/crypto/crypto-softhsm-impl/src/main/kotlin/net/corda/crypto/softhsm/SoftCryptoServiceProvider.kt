package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.CryptoServiceProvider
import net.corda.crypto.component.impl.LifecycleNameProvider

interface SoftCryptoServiceProvider : CryptoServiceProvider, LifecycleNameProvider