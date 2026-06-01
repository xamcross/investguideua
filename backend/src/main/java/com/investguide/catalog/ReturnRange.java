package com.investguide.catalog;

/**
 * A reference percentage return range, {@code {min, max}} (SPECIFICATION §6
 * {@code providers.typicalReturnPct}, §5.4 {@code expectedReturnPct}; ticket BE-C1).
 *
 * <p>Immutable value object embedded in {@link Provider}. Percentages are expressed as plain
 * numbers (e.g. {@code 13.0} means 13%). This is reference/marketing data, not money, so a
 * floating-point type is acceptable here — unlike monetary amounts, which are always integer
 * minor units elsewhere in the system.
 */
public record ReturnRange(double min, double max) {
}
