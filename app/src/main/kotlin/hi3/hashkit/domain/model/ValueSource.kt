package hi3.hashkit.domain.model

/**
 * Provenance of a telemetry value. The UI must always be able to distinguish these;
 * an ESTIMATED value is never rendered as though it were MEASURED.
 */
enum class ValueSource {
    /** Read from a physical sensor on the miner. */
    MEASURED,

    /** Reported by firmware without a known sensor basis. */
    REPORTED,

    /** Derived by this app from other reported values (e.g. efficiency = power / hashrate). */
    CALCULATED,

    /** Guessed from device specs because the firmware reports nothing. */
    ESTIMATED,

    /** Not available from this device/firmware. */
    UNAVAILABLE,
}

/** A value paired with its provenance. */
data class Sourced<T>(val value: T?, val source: ValueSource) {
    companion object {
        fun <T> unavailable(): Sourced<T> = Sourced(null, ValueSource.UNAVAILABLE)
        fun <T> measured(v: T?): Sourced<T> =
            if (v == null) unavailable() else Sourced(v, ValueSource.MEASURED)
        fun <T> reported(v: T?): Sourced<T> =
            if (v == null) unavailable() else Sourced(v, ValueSource.REPORTED)
        fun <T> calculated(v: T?): Sourced<T> =
            if (v == null) unavailable() else Sourced(v, ValueSource.CALCULATED)
        fun <T> estimated(v: T?): Sourced<T> =
            if (v == null) unavailable() else Sourced(v, ValueSource.ESTIMATED)
    }
}
