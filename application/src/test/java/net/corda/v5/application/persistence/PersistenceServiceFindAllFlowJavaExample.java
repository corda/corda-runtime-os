package net.corda.v5.application.persistence;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

import javax.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PersistenceServiceFindAllFlowJavaExample implements ClientStartableFlow {

    // For JPA Entity:
    @CordaSerializable
    @Entity
    @Table(name = "DOGS")
    @NamedQuery(name = "find_by_name_and_age", query = "SELECT d FROM Dog d WHERE d.name = :name AND d.age <= :maxAge")
    static class Dog {
        @Id
        private UUID id;
        @Column(name = "DOG_NAME", length = 50, nullable = false, unique = false)
        private String name;
        @Column(name = "DOG_AGE")
        private Integer age;

        // getters and setters
        // ...
    }

    @CordaInject
    public PersistenceService persistenceService;

    @Override
    @NotNull
    public String call(@NotNull RestRequestBody requestBody) {
        // create a named query setting parameters one-by-one, that returns the second page of up to 100 records
        ParameterizedQuery<Dog> pagedQuery = persistenceService
                .query("find_by_name_and_age", Dog.class)
                .setParameter("name", "Felix")
                .setParameter("maxAge", 5)
                .setLimit(100)
                .setOffset(200);

        // execute the query and return the results as a List
        List<Dog> result1 = pagedQuery.execute();

        // create a named query setting parameters as Map, that returns the second page of up to 100 records
        ParameterizedQuery<Dog> paramQuery = persistenceService
                .query("find_by_name_and_age", Dog.class)
                .setParameters(Map.of("name", "Felix", "maxAge", 5))
                .setLimit(100)
                .setOffset(200);

        // execute the query and return the results as a List
        List<Dog> result2 = pagedQuery.execute();

        return "success";
    }
}
