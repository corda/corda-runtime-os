package net.corda.membership.certificate.service.impl

import net.corda.virtualnode.ShortHash

internal class NoSuchNode(holdingIdentityId: ShortHash) :
    CertificatesServiceException("No node named $holdingIdentityId")
