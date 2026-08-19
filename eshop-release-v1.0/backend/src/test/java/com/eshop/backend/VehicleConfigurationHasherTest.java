package com.eshop.backend;

import com.eshop.backend.vehicle.VehicleConfigurationHasher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VehicleConfigurationHasherTest {

    @Test
    void emptySelectionUsesStableSentinel() {
        assertEquals("NONE", VehicleConfigurationHasher.hash(null));
        assertEquals("NONE", VehicleConfigurationHasher.hash(List.of()));
    }

    @Test
    void hashIgnoresOrderAndDuplicates() {
        String left = VehicleConfigurationHasher.hash(List.of(9L, 3L, 3L, 1L));
        String right = VehicleConfigurationHasher.hash(List.of(1L, 9L, 3L));
        assertEquals(left, right);
        assertNotEquals("NONE", left);
    }
}
