package com.example.sandbox.cpk1

import com.example.sandbox.library.Wrapper
import com.example.sandbox.library.WrapperFactory
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

/** Invokes methods on an implementation class from a non-exported package of another bundle. */
@Suppress("unused")
@Component(name = "invoke.private.impl.flow")
class InvokePrivateImplFlow: Flow<String> {
    override fun call() = WrapperFactory.create().data
}

/** Creates a class that uses a generic from a non-exported package of another bundle. */
@Suppress("unused")
@Component(name = "private.impl.as.generic.flow")
class PrivateImplAsGenericFlow: Flow<String> {
    override fun call(): String {
        val wrapper = WrapperFactory.create()
        return GenericClass(wrapper).call()
    }
}

/** A class that takes a [Wrapper] as a generic. */
private class GenericClass<T : Wrapper>(private val wrapper: T) {
    fun call() = wrapper.data
}