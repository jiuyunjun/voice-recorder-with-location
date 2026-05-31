package com.example.voicerecorderlocation.tracking

/**
 * 1-D Kalman filter applied independently to latitude and longitude.
 *
 * Model:
 *   State  x = position (degrees)
 *   Measurement z = GPS reading
 *   Measurement noise R = accuracy² (metres converted to degrees via METERS_PER_DEGREE)
 *   Process noise Q grows with elapsed time: Q = (q_mps * dt)²
 *     where q_mps is the expected positional uncertainty per second due to movement.
 *
 * References: the "Kalman filter for GPS tracking" approach used widely in mobile apps.
 */
class GpsKalmanFilter(
    private val qMetersPerSecond: Double = 3.0
) {
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1.0     // negative = not yet initialised
    private var lastTimestampMs = 0L

    val isInitialised: Boolean get() = variance >= 0

    fun process(
        rawLat: Double,
        rawLng: Double,
        accuracyMeters: Float,
        timestampMs: Long
    ): Pair<Double, Double> {
        val accuracy = accuracyMeters.toDouble().coerceAtLeast(1.0)

        if (variance < 0) {
            lat = rawLat
            lng = rawLng
            variance = accuracy * accuracy
            lastTimestampMs = timestampMs
            return Pair(lat, lng)
        }

        // --- Predict step: uncertainty grows with time ---
        val dtSeconds = ((timestampMs - lastTimestampMs) / 1_000.0).coerceAtLeast(0.0)
        if (dtSeconds > 0) {
            variance += dtSeconds * qMetersPerSecond * qMetersPerSecond
        }
        lastTimestampMs = timestampMs

        // --- Update step ---
        val r = accuracy * accuracy          // measurement noise variance
        val k = variance / (variance + r)    // Kalman gain  [0 = ignore measurement, 1 = trust only measurement]

        lat += k * (rawLat - lat)
        lng += k * (rawLng - lng)
        variance = (1.0 - k) * variance

        return Pair(lat, lng)
    }

    fun reset() {
        variance = -1.0
    }
}
