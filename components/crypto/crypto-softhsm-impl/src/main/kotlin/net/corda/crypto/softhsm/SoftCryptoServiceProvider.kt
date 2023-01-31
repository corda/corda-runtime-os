package net.corda.crypto.softhsm

import net.corda.crypto.component.impl.CryptoServiceProvider
import net.corda.crypto.component.impl.LifecycleNameProvider

interface SoftCryptoServiceProvider : CryptoServiceProvider<SoftCryptoServiceConfig>, LifecycleNameProvider