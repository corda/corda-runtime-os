package net.corda.libs.virtualnode.common.exception

class VirtualNodeNotFoundException(holdingIdentityShortHash: String) :
    Exception("Could not find a virtual node with Id of $holdingIdentityShortHash")