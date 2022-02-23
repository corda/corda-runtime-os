package net.corda.crypto.stub.delegated.signing

import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

interface SigningCryptoService : LifecycleWithDominoTile, DelegatedSigner
