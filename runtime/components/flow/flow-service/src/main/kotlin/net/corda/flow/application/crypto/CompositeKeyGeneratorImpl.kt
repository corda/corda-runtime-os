package net.corda.flow.application.crypto

import net.corda.crypto.core.CompositeKeyProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PublicKey

/* This component is currently a trivial stub over CompositeKeyProvider, which
 * follows our pattern of having an indirection layer from CorDapp flows to the
 * corda implementation so that we have flexibility to add extra checks or other
 * work at this point.
 */
@Component(
    service = [CompositeKeyGenerator::class, UsedByFlow::class],
    scope = PROTOTYPE
)
class CompositeKeyGeneratorImpl
@Activate
constructor(
    @Reference(service = CompositeKeyProvider::class)
    private val provider: CompositeKeyProvider
) : CompositeKeyGenerator, UsedByFlow, SingletonSerializeAsToken {
    @Suspendable
    override fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey =
        provider.create(keys, threshold)
}
