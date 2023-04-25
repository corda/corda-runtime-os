package net.cordapp.testing.testflows

import org.corda.weft.binding.api.BindsFacade
import org.corda.weft.binding.api.BindsFacadeMethod
import org.corda.weft.binding.api.FacadeVersions
import org.corda.weft.binding.api.InteropAction
import org.corda.weft.binding.api.QualifiedWith

@BindsFacade("/com/r3/tokens/sample")
@FacadeVersions("v1.0")
interface SampleTokensFacade {
    @BindsFacadeMethod("hello")
    fun getHello(greeting: String): @QualifiedWith("greeting") InteropAction<String>

    @BindsFacadeMethod("get-balance")
    fun getBalance(greeting: String): @QualifiedWith("greeting") InteropAction<String>
}