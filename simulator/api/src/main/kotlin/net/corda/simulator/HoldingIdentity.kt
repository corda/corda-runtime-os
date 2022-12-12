package net.corda.simulator

import net.corda.simulator.exceptions.ServiceConfigurationException
import net.corda.simulator.factories.HoldingIdentityFactory
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.types.MemberX500Name
import java.util.ServiceLoader

/**
 * A holding identity with which to create virtual nodes.
 */
@DoNotImplement
interface HoldingIdentity {

    /**
     * The [MemberX500Name] with which this identity was created.
     */
    val member: MemberX500Name

    companion object {
        private val factory by lazy {
            ServiceLoader.load(HoldingIdentityFactory::class.java).firstOrNull() ?:
                throw ServiceConfigurationException(HoldingIdentityFactory::class.java)
        }

        /**
         * Creates a holding identity using the provided string as a [MemberX500Name] common name, with other
         * name elements set to defaults.
         *
         * @param commonName The string to use as the common name.
         * @return A [HoldingIdentity] whose [MemberX500Name] has the given common name.
         */
        @JvmStatic
        fun create(commonName: String): HoldingIdentity {
            return factory.create(
                MemberX500Name.parse("CN=$commonName, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB")
            )
        }

        /**
         * Creates a holding identity using the provided member.
         *
         * @param member The member for which to create a holding identity.
         * @return A [HoldingIdentity].
         */
        @JvmStatic
        fun create(member: MemberX500Name): HoldingIdentity {
            return factory.create(member)
        }
    }
}