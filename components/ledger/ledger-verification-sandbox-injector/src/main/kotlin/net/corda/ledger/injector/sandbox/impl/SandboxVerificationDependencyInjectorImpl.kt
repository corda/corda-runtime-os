package net.corda.ledger.injector.sandbox.impl

import net.corda.ledger.injector.sandbox.SandboxVerificationDependencyInjector
import net.corda.sandboxgroupcontext.service.impl.SandboxDependencyInjectorImpl
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.lang.reflect.Field

class SandboxVerificationDependencyInjectorImpl(
    singletons: Map<SingletonSerializeAsToken, List<String>>,
    closeable: AutoCloseable
) : SandboxVerificationDependencyInjector, SandboxDependencyInjectorImpl(singletons, closeable) {

    override fun injectServices(contract: Contract) {
        val requiredFields = contract::class.java.getFieldsForInjection()
        val mismatchedFields = requiredFields.filterNot { serviceTypeMap.containsKey(it.type) }
        if (mismatchedFields.any()) {
            val fields = mismatchedFields.joinToString(separator = ", ", transform = Field::getName)
            throw IllegalArgumentException(
                "No registered types could be found for the following field(s) '$fields'"
            )
        }

        requiredFields.forEach { field ->
            field.isAccessible = true
            if (field.get(contract) == null) {
                field.set(
                    contract,
                    serviceTypeMap[field.type]
                )
            }
        }
    }
}