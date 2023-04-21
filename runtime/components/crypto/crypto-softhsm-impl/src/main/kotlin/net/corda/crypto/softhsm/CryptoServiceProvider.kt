package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.libs.configuration.SmartConfig

// Obtain a crypto service given its configuration.
// The reason this interface exists rather than reference SoftCryptoServiceProvider is so that
// we can provide an alternative implementation inline in code in  TestServicesFactory, so that
// the TestServiceFactory can control the instances of crypto services.
//
// This interface is used as a type for dependency injection in CryptoServiceFactoryImpl
fun interface CryptoServiceProvider {
    fun getInstance(config: SmartConfig): CryptoService
}
