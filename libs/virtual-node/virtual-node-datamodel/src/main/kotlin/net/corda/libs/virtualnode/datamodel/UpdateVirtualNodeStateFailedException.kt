package net.corda.libs.virtualnode.datamodel

class UpdateVirtualNodeStateFailedException(holdingIdentityShortHash: String, state: String) :
    Exception("Failed to update state of virtual node with Id of $holdingIdentityShortHash to $state")