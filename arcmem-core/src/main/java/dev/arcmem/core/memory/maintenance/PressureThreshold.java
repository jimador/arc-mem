package dev.arcmem.core.memory.maintenance;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record PressureThreshold(
        @DecimalMin("0.0") @DecimalMax("1.0") double lightSweep,
        @DecimalMin("0.0") @DecimalMax("1.0") double fullSweep
) {
    @AssertTrue(message = "fullSweep must be greater than lightSweep")
    public boolean isFullSweepGreaterThanLight() {
        return fullSweep > lightSweep;
    }
}
