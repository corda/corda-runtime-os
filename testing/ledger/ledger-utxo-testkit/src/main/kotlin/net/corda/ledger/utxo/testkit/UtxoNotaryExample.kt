package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party

private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
val utxoNotaryExample = Party(notaryX500Name, publicKeyExample)

val anotherUtxoNotaryExample = Party(
    MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
    anotherPublicKeyExample
)