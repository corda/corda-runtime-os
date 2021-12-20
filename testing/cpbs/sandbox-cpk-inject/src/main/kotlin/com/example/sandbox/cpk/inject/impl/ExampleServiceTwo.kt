package com.example.sandbox.cpk.inject.impl

import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.services.CordaService
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.DigestAlgorithmName.Companion.SHA2_256
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ ExampleServiceTwo::class ])
class ExampleServiceTwo @Activate constructor(
    @Reference
    private val digestService: DigestService
) : CordaService, CordaServiceInjectable {
    init {
        loggerFor<ExampleServiceTwo>().info("Activated")
    }

    fun apply(data: String): String {
        return digestService.hash(data.toByteArray(), SHA2_256)
            .toHexString()
    }
}
