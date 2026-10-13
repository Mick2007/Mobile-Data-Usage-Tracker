package com.example.dataguard.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Supported units for setting network data quotas and limits.
 * Uses the binary standard: 1 GB = 1024 MB = 1,073,741,824 bytes.
 */
enum class DataLimitUnit(val label: String, val multiplierBytes: Long) {
    MB("MB", 1024L * 1024L),
    GB("GB", 1024L * 1024L * 1024L);

    companion object {
        fun fromString(raw: String?): DataLimitUnit {
            return when (raw?.trim()?.uppercase(Locale.US)) {
                "GB" -> GB
                else -> MB
            }
        }
    }
}

object DataUnitUtils {
    const val BYTES_PER_MB: Long = 1024L * 1024L
    const val BYTES_PER_GB: Long = 1024L * 1024L * 1024L

    /**
     * Converts a user-entered numeric quantity in the given unit into canonical bytes.
     */
    fun limitToBytes(value: Double, unit: DataLimitUnit): Long {
        if (value <= 0.0 || value.isNaN() || value.isInfinite()) return 0L
        return (value * unit.multiplierBytes.toDouble()).toLong().coerceAtLeast(0L)
    }

    /**
     * Converts canonical bytes back into the given unit quantity.
     */
    fun bytesToLimitValue(bytes: Long, unit: DataLimitUnit): Double {
        if (bytes <= 0L) return 0.0
        return bytes.toDouble() / unit.multiplierBytes.toDouble()
    }

    /**
     * Formats a double value with up to 3 decimal places, removing redundant trailing zeroes.
     * E.g.:
     *   1000.0 -> "1000"
     *   6.800  -> "6.8"
     *   29.300 -> "29.3"
     *   0.9765 -> "0.977"
     */
    fun formatLimitValue(value: Double): String {
        if (value <= 0.0) return "0"
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("0.###", symbols).apply {
            isGroupingUsed = false
            maximumFractionDigits = 3
            minimumFractionDigits = 0
        }
        return df.format(value)
    }

    /**
     * Validates input text for positive numeric values.
     * Returns a pair of parsed value (if valid) and an error message (if invalid).
     */
    fun validateInput(text: String): Pair<Double?, String?> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Pair(null, "Limit cannot be empty")
        }
        val parsed = trimmed.toDoubleOrNull()
        if (parsed == null) {
            return Pair(null, "Please enter a valid number")
        }
        if (parsed <= 0.0) {
            return Pair(null, "Limit must be greater than 0")
        }
        return Pair(parsed, null)
    }

    /**
     * Converts a value between units while preserving precision up to 3 decimal places.
     */
    fun convertValue(value: Double, fromUnit: DataLimitUnit, toUnit: DataLimitUnit): Double {
        if (fromUnit == toUnit) return value
        return when {
            fromUnit == DataLimitUnit.MB && toUnit == DataLimitUnit.GB -> value / 1024.0
            fromUnit == DataLimitUnit.GB && toUnit == DataLimitUnit.MB -> value * 1024.0
            else -> value
        }
    }
}
