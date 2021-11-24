package net.corda.dependency.injection

interface DependencyInjectionBuilderFactory {
    fun create(): DependencyInjectionBuilder
}