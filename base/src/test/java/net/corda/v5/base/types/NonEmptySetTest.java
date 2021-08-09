package net.corda.v5.base.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class NonEmptySetTest {

    private final Set<String> nonEmptySet = NonEmptySet.copyOf(List.of(
            "Test 1",
            "Test 2",
            "Test 3",
            "Test 4",
            "Test 5",
            "Test 6",
            "Test 7",
            "Test 8",
            "Test 9",
            "Test 10"
    ));

    @Test
    @DisplayName("AddAll is unsupported")
    void AddAllIsUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> nonEmptySet.addAll(List.of("A", "B", "C")));
    }

    @Test
    @DisplayName("Add is unsupported")
    void AddIsUnsupported() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> nonEmptySet.add("A"));
    }

    @Test
    @DisplayName("ContainsAll returns true for a subset")
    void ContainsAllSubset() {
        Assertions.assertTrue(nonEmptySet.containsAll(List.of("Test 2", "Test 4", "Test 10")));
    }

    @Test
    @DisplayName("ContainsAll returns false for elements not in the set")
    void ContainsAllNotInSet() {
        Assertions.assertFalse(nonEmptySet.containsAll(List.of("Test 12", "Test 14", "Test 110")));
    }

    @Test
    @DisplayName("ContainsAll returns true for empty inputs")
    void ContainsAllEmptyInputs() {
        Assertions.assertTrue(nonEmptySet.containsAll(List.of()));
    }

    @Test
    @DisplayName("ContainsAll returns true for same set")
    void ContainsAllSameSet() {
        Assertions.assertTrue(nonEmptySet.containsAll(nonEmptySet));
    }

    @Test
    @DisplayName("ContainsAll returns false for only some overlap")
    void ContainsAllNotAll() {
        Assertions.assertFalse(nonEmptySet.containsAll(List.of("Test 1", "Test 2", "Test 0")));
    }

    @Test
    @DisplayName("Contains returns true when element in set")
    void ContainsReturnsTrue() {
        Assertions.assertTrue(nonEmptySet.contains("Test 1"));
    }

    @Test
    @DisplayName("Contains returns false when element not in set")
    void ContainsReturnsFalse() {
        Assertions.assertFalse(nonEmptySet.contains("Not in set"));
    }

    @Test
    @DisplayName("Can create a NonEmptySet with null as an element")
    void CreateWithNull() {
        ArrayList<String> input = new ArrayList<>();
        input.add("A");
        input.add(null);
        Assertions.assertDoesNotThrow(() -> NonEmptySet.copyOf(input));
    }

    @Test
    @DisplayName("Equals returns false when compared with null")
    void EqualsNullReturnsFalse() {
        Assertions.assertFalse(nonEmptySet.equals(null));
    }

    @Test
    @DisplayName("Equals returns true when compared with self")
    void EqualsSelfReturnsTrue() {
        Assertions.assertTrue(nonEmptySet.equals(nonEmptySet));
    }

    @Test
    @DisplayName("Equals returns false when compared with something of a different type")
    void EqualsSomethingElseReturnsFalse() {
        Assertions.assertFalse(nonEmptySet.equals("Different type"));
    }

}
