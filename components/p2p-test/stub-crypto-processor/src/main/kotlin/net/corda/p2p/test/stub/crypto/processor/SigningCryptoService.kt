package net.corda.p2p.test.stub.crypto.processor

import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

interface SigningCryptoService : LifecycleWithDominoTile, DelegatedSigner
