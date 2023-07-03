package net.corda.interop.web3j

import net.corda.interop.web3j.internal.DelegatingWeb3JService
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.web3j.protocol.Web3j

@Component(service = [UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class CordaWeb3jWrapper(
    private val web3j: Web3j = Web3j.build(DelegatingWeb3JService())
): Web3j by web3j, UsedByFlow, SingletonSerializeAsToken {

    override fun shutdown() {
        // No op
    }
}