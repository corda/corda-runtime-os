package net.corda.crypto.client

import net.corda.crypto.CryptoOpsClient
import net.corda.lifecycle.Lifecycle

interface CryptoOpsClientComponent : CryptoOpsClient, Lifecycle