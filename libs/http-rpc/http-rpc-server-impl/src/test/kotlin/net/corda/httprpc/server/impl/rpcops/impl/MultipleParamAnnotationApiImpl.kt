package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.server.impl.rpcops.MultipleParamAnnotationApi
import net.corda.v5.httprpc.api.PluggableRPCOps

class MultipleParamAnnotationApiImpl : MultipleParamAnnotationApi, PluggableRPCOps<MultipleParamAnnotationApi> {

    override val targetInterface: Class<MultipleParamAnnotationApi>
        get() = MultipleParamAnnotationApi::class.java

    override val protocolVersion: Int
        get() = 2

    override fun test(twoAnnotations: String) {
    }
}