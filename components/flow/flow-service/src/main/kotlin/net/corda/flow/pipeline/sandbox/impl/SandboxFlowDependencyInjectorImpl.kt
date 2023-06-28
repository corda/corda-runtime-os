package net.corda.flow.pipeline.sandbox.impl

import net.corda.flow.pipeline.sandbox.SandboxFlowDependencyInjector
import net.corda.sandboxgroupcontext.service.impl.SandboxDependencyInjectorImpl
import net.corda.v5.application.flows.Flow
import net.corda.v5.serialization.SingletonSerializeAsToken
import java.lang.reflect.Field

class SandboxFlowDependencyInjectorImpl(
    singletons: Map<SingletonSerializeAsToken, List<String>>,
    closeable: AutoCloseable
) : SandboxFlowDependencyInjector, SandboxDependencyInjectorImpl(singletons, closeable) {

    override fun injectServices(flow: Flow) {
        val requiredFields = flow::class.java.getFieldsForInjection()
        val mismatchedFields = requiredFields.filterNot { serviceTypeMap.containsKey(it.type) }
        if (mismatchedFields.any()) {
            val fields = mismatchedFields.joinToString(separator = ", ", transform = Field::getName)
            throw IllegalArgumentException(
                "No registered types could be found for the following field(s) '$fields'"
            )
        }

        requiredFields.forEach { field ->
            field.isAccessible = true
            if (field.get(flow) == null) {
                field.set(
                    flow,
                    serviceTypeMap[field.type]
                )
            }
        }
    }

}
