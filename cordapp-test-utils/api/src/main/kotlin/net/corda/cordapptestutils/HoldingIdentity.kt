package net.corda.cordapptestutils

import net.corda.cordapptestutils.exceptions.ServiceConfigurationException
import net.corda.cordapptestutils.factories.HoldingIdentityFactory
import net.corda.v5.base.types.MemberX500Name
import java.util.ServiceLoader

interface HoldingIdentity {

    val member: MemberX500Name

    companion object {
        private val factory by lazy {
            ServiceLoader.load(HoldingIdentityFactory::class.java).firstOrNull() ?:
                throw ServiceConfigurationException(HoldingIdentityFactory::class.java)
        }

        fun create(commonName: String): HoldingIdentity {

            return factory.create(
                MemberX500Name.parse(
                "CN=$commonName, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB"))
        }

        fun create(member : MemberX500Name): HoldingIdentity {
            return factory.create(member)
        }
    }
}