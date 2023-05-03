package com.r3.corda.testing.interop

import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.binding.QualifiedWith

@BindsFacade("/com/r3/tokens/sample")
@FacadeVersions("v1.0")
interface SampleTokensFacade {
    @BindsFacadeMethod("say-hello")
    fun getHello(greeting: String): @QualifiedWith("greeting") InteropAction<String>

    @BindsFacadeMethod("get-balance")
    fun getBalance(greeting: String): @QualifiedWith("greeting") InteropAction<String>
}
