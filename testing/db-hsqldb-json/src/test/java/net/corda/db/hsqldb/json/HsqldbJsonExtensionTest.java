package net.corda.db.hsqldb.json;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HsqldbJsonExtensionTest {
    @Test
    void testExtractFieldByName() {
        final String json = "{\"a\": \"Hello World!\", \"b\": 100, \"c\": true}";

        assertEquals("Hello World!", HsqldbJsonExtension.fieldByName(json, "a"));
        assertEquals("100", HsqldbJsonExtension.fieldByName(json, "b"));
        assertEquals("true", HsqldbJsonExtension.fieldByName(json, "c"));
        assertThat(HsqldbJsonExtension.fieldByName(json, "z")).isNull();
        assertThat(HsqldbJsonExtension.fieldByName(null, "a")).isNull();
    }

    @Test
    void testExtractFieldByIndex() {
        final String json = "[\"Hello World!\", 100, true]";

        assertEquals("Hello World!", HsqldbJsonExtension.fieldByIndex(json, 0));
        assertEquals("100", HsqldbJsonExtension.fieldByIndex(json, 1));
        assertEquals("true", HsqldbJsonExtension.fieldByIndex(json, 2));
        assertThat(HsqldbJsonExtension.fieldByIndex(json, 1000)).isNull();
        assertThat(HsqldbJsonExtension.fieldByIndex(null, 0)).isNull();
    }

    @Test
    void testExtractObjectByName() {
        final String json = "{\"a\": \"Hello World!\", \"b\": 100, \"c\": { \"d\": true }, \"e\": [ 1, 2 ]}";

        assertEquals("\"Hello World!\"", HsqldbJsonExtension.objectByName(json, "a"));
        assertEquals("100", HsqldbJsonExtension.objectByName(json, "b"));
        assertEquals("{\"d\": true}", HsqldbJsonExtension.objectByName(json, "c"));
        assertEquals("[1, 2]", HsqldbJsonExtension.objectByName(json, "e"));
        assertThat(HsqldbJsonExtension.objectByName(json, "z")).isNull();
        assertThat(HsqldbJsonExtension.objectByName(null, "a")).isNull();
    }

    @Test
    void testExtractObjectByIndex() {
        final String json = "[\"Hello World!\", 100, { \"d\": true }, [ 2, 3 ] ]";

        assertEquals("\"Hello World!\"", HsqldbJsonExtension.objectByIndex(json, 0));
        assertEquals("100", HsqldbJsonExtension.objectByIndex(json, 1));
        assertEquals("{\"d\": true}", HsqldbJsonExtension.objectByIndex(json, 2));
        assertEquals("[2, 3]", HsqldbJsonExtension.objectByIndex(json, 3));
        assertThat(HsqldbJsonExtension.objectByIndex(json, 1000)).isNull();
        assertThat(HsqldbJsonExtension.objectByIndex(null, 0)).isNull();
    }

    @Test
    void testObjectHasKeyByName() {
        final String json = "{\"a\": \"Hello World!\", \"b\": 100, \"c\": { \"d\": true }}";

        assertThat(HsqldbJsonExtension.hasKeyByName(json, "a")).isNotNull().isTrue();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "b")).isNotNull().isTrue();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "c")).isNotNull().isTrue();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "z")).isNotNull().isFalse();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "d")).isNotNull().isFalse();
        assertThat(HsqldbJsonExtension.hasKeyByName(null, "a")).isNull();
    }

    @Test
    void testObjectArrayHasKeyByName() {
        final String json = "[ { \"a\": 1 }, { \"b\": 2 }, { \"c\": 3 } ]";

        assertThat(HsqldbJsonExtension.hasKeyByName(json, "a")).isNotNull().isTrue();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "d")).isNotNull().isFalse();
        assertThat(HsqldbJsonExtension.hasKeyByName(null, "b")).isNull();
    }

    @Test
    void testValueArrayHasKeyByName() {
        final String json = "[ \"a\", \"b\", \"c\" ]";

	    assertThat(HsqldbJsonExtension.hasKeyByName(json, "a")).isNotNull().isTrue();
        assertThat(HsqldbJsonExtension.hasKeyByName(json, "d")).isNotNull().isFalse();
    }
}
