package net.corda.p2p.linkmanager

interface IdentityDataForwarder {
    fun identityAdded(identity: LinkManagerNetworkMap.HoldingIdentity)
}
