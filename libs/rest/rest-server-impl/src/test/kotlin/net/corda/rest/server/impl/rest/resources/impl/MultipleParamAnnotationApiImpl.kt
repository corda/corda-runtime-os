package net.corda.rest.server.impl.rest.resources.impl

import net.corda.rest.PluggableRestResource
import net.corda.rest.server.impl.rest.resources.MultipleParamAnnotationApi

class MultipleParamAnnotationApiImpl : MultipleParamAnnotationApi, PluggableRestResource<MultipleParamAnnotationApi> {

    override val targetInterface: Class<MultipleParamAnnotationApi>
        get() = MultipleParamAnnotationApi::class.java

    override val protocolVersion: Int
        get() = 2

    override fun test(twoAnnotations: String) {
    }
}
