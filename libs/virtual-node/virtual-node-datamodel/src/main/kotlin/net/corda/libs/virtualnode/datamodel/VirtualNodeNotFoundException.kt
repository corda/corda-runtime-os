package net.corda.libs.virtualnode.datamodel

class VirtualNodeNotFoundException(holdingIdentityShortHash: String) :
    Exception("Could not find a virtual node with Id of $holdingIdentityShortHash")