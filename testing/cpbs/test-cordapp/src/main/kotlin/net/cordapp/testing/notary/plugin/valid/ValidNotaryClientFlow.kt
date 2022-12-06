package net.cordapp.testing.notary.plugin.valid

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow

class ValidNotaryClientFlow : PluggableNotaryClientFlow {
    private companion object {
        val log = loggerFor<ValidNotaryClientFlow>()
    }

    @Suspendable
    override fun call(): List<DigitalSignatureAndMetadata> {
        log.info("Valid notary client flow started, returning empty signatures.")
        return emptyList()
    }
}
