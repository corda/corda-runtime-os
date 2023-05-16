package com.r3.corda.testing.interop

import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.binding.QualifiedWith

@BindsFacade("org.corda.interop/platform/tokens")
@FacadeVersions("v1.0", "v2.0")
interface SampleTokensFacade {
    @BindsFacadeMethod("hello")
    fun getHello(greeting: String): @QualifiedWith("greeting") InteropAction<String>

}
