package net.corda.crypto.service

import net.corda.v5.cipher.suite.CryptoServiceProvider

interface SoftCryptoServiceProvider : CryptoServiceProvider<SoftCryptoServiceConfig>, LifecycleNameProvider