package net.corda.crypto.softhsm

fun <R> SigningRepository.consume(block: SigningRepository.()->R ): R = this.use {
    it.block()
}