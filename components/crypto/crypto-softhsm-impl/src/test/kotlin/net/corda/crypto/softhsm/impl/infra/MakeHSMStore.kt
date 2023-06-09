package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.persistence.HSMStore
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

fun makeHSMStore(wrappingKeyAlias: String = "root"): HSMStore {
    val association = mock<HSMAssociationInfo> {
        on { masterKeyAlias }.thenReturn(wrappingKeyAlias)
    }
    val hsmStore = mock<HSMStore> {
        on { findTenantAssociation(any(), any()) } doReturn association
    }
    return hsmStore
}