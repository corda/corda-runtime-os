package net.corda.flow.application.services.impl.interop.binding.creation

import net.corda.flow.application.services.impl.interop.binding.FacadeInterfaceBinding
import net.corda.flow.application.services.impl.interop.binding.internal.InterfaceBindingContext
import net.corda.v5.application.interop.facade.Facade

/**
 * Public entry-point for binding JVM interfaces to [Facade]s. A Java developer wishing to bind a facade to a JVM
 * interface will write:
 *
 * ```java
 * FacadeInterfaceBinding binding = FacadeInterfaceBindings.bind(facade, MyInterface.class);
 * ```
 */
object FacadeInterfaceBindings {

    /**
     * Bind a [Facade] to a JVM interface.
     * @param facade The [Facade] to bind.
     * @param boundInterface The [Class] to bind the facade to.
     * @return A [FacadeInterfaceBinding] which maps methods in the bound interface to methods in the facade.
     */
    @JvmStatic
    fun bind(facade: Facade, boundInterface: Class<*>): FacadeInterfaceBinding {
        val context = InterfaceBindingContext(facade, boundInterface)

        return context.createBinding()
    }
}

/**
 * Kotlin-only utility function that enables the developer to write `facade.bindTo<InterfaceType>()` as a shorthand for
 * `FacadeInterfaceBindings.bind(facade, InterfaceType::class.java)`
 */
inline fun <reified T : Any> Facade.bindTo() = FacadeInterfaceBindings.bind(this, T::class.java)