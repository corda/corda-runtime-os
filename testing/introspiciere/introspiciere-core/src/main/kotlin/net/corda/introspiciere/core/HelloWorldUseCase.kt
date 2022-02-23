package net.corda.introspiciere.core

class HelloWorldUseCase(private val presenter: Presenter<String>) : UseCase<Unit> {
    override fun execute(input: Unit) {
        presenter.present("Hello world!!")
    }
}