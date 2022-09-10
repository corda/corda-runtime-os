package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory

data class KeyParameters(val alias: String, val hsmCategory: HsmCategory, val scheme: String)