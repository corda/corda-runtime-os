package net.corda.membership.impl.registration

import net.corda.v5.base.types.MemberX500Name

internal sealed class MemberRole {
    companion object {
        fun extractRolesFromContext(context: Map<String, String>): Collection<MemberRole> {
            val roleNames = context.entries.filter {
                it.key.startsWith("corda.roles.")
            }.map {
                it.value
            }.toSet()
            val roles: Collection<MemberRole> = roleNames.map { roleName ->
                when (roleName) {
                    "notary" -> {
                        readNotary(context)
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid role: $roleName")
                    }
                }
            }
            if (!roles.any { it is Notary }) {
                val notaryKeys = context.keys.filter {
                    it.startsWith("corda.notary")
                }
                if (notaryKeys.isNotEmpty()) {
                    throw IllegalArgumentException("The keys $notaryKeys are only valid with notary role.")
                }
            }
            return roles
        }

        private fun readNotary(context: Map<String, String>): Notary {
            val serviceName = context["corda.notary.service.name"] ?: throw IllegalArgumentException("Notary must have a service name")
            val plugin = context["corda.notary.service.plugin"] ?: throw IllegalArgumentException("Notary must have a service plugin")
            return Notary(
                serviceName = MemberX500Name.parse(serviceName),
                plugin = plugin,
            )
        }
    }
    data class Notary(
        val serviceName: MemberX500Name,
        val plugin: String,
    ) : MemberRole()
}
