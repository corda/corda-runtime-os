package net.corda.membership.client

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.exceptions.CordaRuntimeException

class CouldNotFindEntityException(val entity: Entity, holdingIdentityShortHash: ShortHash) :
    CordaRuntimeException("Could not find $entity: $holdingIdentityShortHash")

enum class Entity(private val label: String) {
    MEMBER("member"),
    VIRTUAL_NODE("virtual node");

    override fun toString(): String = label
}
