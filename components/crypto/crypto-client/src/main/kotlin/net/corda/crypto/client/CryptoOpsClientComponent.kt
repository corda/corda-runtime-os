package net.corda.crypto.client

import net.corda.crypto.clients.CryptoOpsClient
import net.corda.lifecycle.Lifecycle

interface CryptoOpsClientComponent : CryptoOpsClient, Lifecycle