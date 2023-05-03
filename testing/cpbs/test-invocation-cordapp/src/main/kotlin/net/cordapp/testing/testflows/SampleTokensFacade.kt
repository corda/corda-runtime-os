package net.cordapp.testing.testflows

import net.corda.v5.application.interop.binding.*

@BindsFacade("/com/r3/tokens/sample")
@FacadeVersions("v1.0")
interface SampleTokensFacade {
    @BindsFacadeMethod("hello")
    fun getHello(greeting: String): @QualifiedWith("greeting") InteropAction<String>

    @BindsFacadeMethod("get-balance")
    fun getBalance(greeting: String): @QualifiedWith("greeting") InteropAction<String>
}