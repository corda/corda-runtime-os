package net.corda.ledger.utxo

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Component(service = [VisibilityChecker::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class VisibilityCheckerImpl @Activate constructor(
    @Reference(service = SigningService::class)
    private val signingService: SigningService
) : VisibilityChecker, SingletonSerializeAsToken, UsedByFlow {

    override fun containsMySigningKeys(keys: Iterable<PublicKey>): Boolean {
        return signingService.findMySigningKeys(keys.toSet()).values.isNotEmpty()
    }
}