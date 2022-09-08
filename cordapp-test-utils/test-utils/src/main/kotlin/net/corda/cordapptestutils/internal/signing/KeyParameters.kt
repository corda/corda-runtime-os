package net.corda.cordapptestutils.internal.signing

import net.corda.cordapptestutils.crypto.HsmCategory

data class KeyParameters(val alias: String, val hsmCategory: HsmCategory, val scheme: String)