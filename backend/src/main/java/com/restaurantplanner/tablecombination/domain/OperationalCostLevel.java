package com.restaurantplanner.tablecombination.domain;

public enum OperationalCostLevel {
    LOW(8),
    MEDIUM(24),
    HIGH(48);

    private final int scorePenalty;

    OperationalCostLevel(int scorePenalty) {
        this.scorePenalty = scorePenalty;
    }

    public int scorePenalty() {
        return scorePenalty;
    }
}
