package net.corda.flow.application.services.interop.binding

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.JavaTokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.FacadeVersions
import java.util.*
import kotlin.reflect.KClass

interface DoesNotBindAFacade

@BindsFacade("org.example.com/another/facade/altogether")
interface BindsAnotherFacade

@BindsFacade("org.corda.interop/platform/tokens")
@FacadeVersions("v2.0")
interface BindsToV2FacadeOnly

class FacadeInterfaceBindingSpec : DescribeSpec({

    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade.json")!!)
    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade_v2.json")!!)

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV1.assertBindingFails(java, expectedMessage)

    describe("Facade interface binding") {

        it("should fail if the interface does not bind any facade") {
            DoesNotBindAFacade::class shouldFailToBindWith "Interface is not annotated with @BindsFacade"
        }

        it("should fail if the interface does not bind the requested facade") {
            BindsAnotherFacade::class shouldFailToBindWith
                    "Mismatch: interface's @BindsFacade annotation declares that it is bound to " +
                    "org.example.com/another/facade/altogether"
        }

        it("should fail if the interface does not bind to this version of the requested facade") {
            BindsToV2FacadeOnly::class shouldFailToBindWith
                    "Mismatch: interface explicitly declares binding to versions [v2.0] of " +
                    "org.corda.interop/platform/tokens, but facade has version v1."
        }

        it("should succeed if the interface explicitly binds to this version of the requested facade") {
            facadeV2.bindTo<BindsToV2FacadeOnly>().facade shouldBe facadeV2
        }
    }

    describe("A Java interface binding") {

        it("should succeed") {
            facadeV1.bindTo<JavaTokensFacade>()
            facadeV2.bindTo<JavaTokensFacade>()
        }
    }

})