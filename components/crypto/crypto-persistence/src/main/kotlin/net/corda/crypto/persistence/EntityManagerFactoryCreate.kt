package net.corda.crypto.persistence

import javax.persistence.EntityManager

interface EntityManagerFactoryCreate {
    fun createEntityManager(): EntityManager?
}