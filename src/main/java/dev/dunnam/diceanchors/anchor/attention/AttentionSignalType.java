package dev.dunnam.diceanchors.anchor.attention;

public enum AttentionSignalType {

    PRESSURE_SPIKE(Severity.HIGH),
    HEAT_PEAK(Severity.MEDIUM),
    HEAT_DROP(Severity.MEDIUM),
    CLUSTER_DRIFT(Severity.HIGH);

    private final Severity severity;

    AttentionSignalType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    public enum Severity { LOW, MEDIUM, HIGH }
}
