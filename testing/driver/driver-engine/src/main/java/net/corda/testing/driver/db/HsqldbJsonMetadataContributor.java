package net.corda.testing.driver.db;

import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.spi.MetadataBuilderContributor;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.jetbrains.annotations.NotNull;

import static org.hibernate.type.StandardBasicTypes.BOOLEAN;
import static org.hibernate.type.StandardBasicTypes.STRING;

final class HsqldbJsonMetadataContributor implements MetadataBuilderContributor {
    @Override
    public void contribute(@NotNull MetadataBuilder metadataBuilder) {
        metadataBuilder
            .applySqlFunction("JsonFieldAsObject", new StandardSQLFunction("JsonFieldAsObject", STRING))
            .applySqlFunction("JsonFieldAsText", new StandardSQLFunction("JsonFieldAsText", STRING))
            .applySqlFunction("HasJsonKey", new StandardSQLFunction("HasJsonKey", BOOLEAN));
    }
}
