package com.example.voicerecorderlocation.tracking

import kotlin.math.cos
import kotlin.math.hypot

/**
 * GPS + IMU sensor-fusion Kalman filter.
 *
 * Each horizontal axis (North and East) runs an independent 2-state filter:
 *   state = [position_degrees, velocity_m_per_s]
 *
 * Prediction step  — driven by IMU (accelerometer in world frame, 50 Hz).
 *   pos_new = pos + vel*dt/mpd + 0.5*acc*dt²/mpd
 *   vel_new = vel + acc*dt
 *   P_new   = F*P*F' + Q        (Wiener velocity process noise)
 *
 * Update step — GPS measurement (1–2 Hz).
 *   innovation = gps_pos − pos_pred
 *   K  = P*H' / (H*P*H' + R)   where H=[1,0], R=accuracy²
 *   x  = x + K*innovation
 *   P  = (I − K*H)*P
 *
 * Without IMU data the filter degrades gracefully to a position-smoothing
 * GPS-only filter; just never call predictImu().
 *
 * @param sigmaAccMs2  Expected accelerometer noise / bias (m/s²).
 *                     Increase if the smoothed path still jumps; decrease if
 *                     GPS updates are over-trusted and the path looks stiff.
 */
class ImuAidedKalmanFilter(private val sigmaAccMs2: Double = 0.5) {

    private class Axis {
        var pos = 0.0
        var vel = 0.0       // m/s
        // Upper-triangle of symmetric 2×2 covariance P
        var p00 = 1e6       // large initial position uncertainty
        var p01 = 0.0
        var p11 = 100.0     // initial velocity uncertainty (~10 m/s)
        var init = false
    }

    private val north = Axis()
    private val east  = Axis()

    private var currentLat     = 0.0
    private var lastImuNs      = Long.MIN_VALUE

    val isInitialised: Boolean get() = north.init

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called on every IMU reading (typically 50 Hz).
     * [aNorth] and [aEast] are linear acceleration in m/s² in the
     * world (Earth) frame, gravity already removed.
     */
    fun predictImu(aNorth: Float, aEast: Float, timestampNs: Long) {
        if (lastImuNs == Long.MIN_VALUE) {
            lastImuNs = timestampNs
            return
        }
        if (!north.init) {
            lastImuNs = timestampNs
            return
        }
        val dt = ((timestampNs - lastImuNs) / 1e9).coerceIn(0.0, 0.5)
        lastImuNs = timestampNs
        if (dt <= 0.0) return

        // Zero-velocity update (ZUPT): when horizontal acceleration is near zero the
        // device is most likely stationary. Without this, integrating accelerometer
        // noise produces a phantom velocity that drifts the position between GPS fixes.
        val stationary = hypot(aNorth.toDouble(), aEast.toDouble()) < ZUPT_ACCEL_THRESHOLD

        predictAxis(north, aNorth.toDouble(), dt, METERS_PER_DEG_LAT, stationary)
        predictAxis(east,  aEast.toDouble(),  dt, metersPerDegLng(), stationary)
    }

    /**
     * Called on every accepted GPS fix.
     * Returns the Kalman-filtered (lat, lng) to store.
     */
    fun updateGps(
        lat: Double,
        lng: Double,
        accuracy: Float,
        timestampMs: Long,
        vNorth: Double? = null,
        vEast: Double? = null,
        speedAccuracy: Float = 2f
    ): Pair<Double, Double> {
        currentLat = lat
        val r = accuracy.toDouble().coerceAtLeast(1.0).let { it * it }

        if (!north.init) {
            // Seed velocity from the Doppler reading when available — gives the
            // filter a correct heading/speed from the very first fix.
            north.apply { pos = lat; vel = vNorth ?: 0.0; p00 = r; p01 = 0.0; p11 = 4.0; init = true }
            east.apply  { pos = lng; vel = vEast ?: 0.0; p00 = r; p01 = 0.0; p11 = 4.0; init = true }
            lastImuNs = timestampMs * 1_000_000L
            return Pair(lat, lng)
        }

        // 1) Position measurement update (lat/lng).
        updateAxis(north, lat, r)
        updateAxis(east,  lng, r)

        // 2) Velocity (Doppler) measurement update — GPS speed/bearing decomposed into
        //    North/East. Doppler velocity is far more accurate than differencing positions,
        //    so this sharpens the heading and keeps the track from lagging on turns.
        if (vNorth != null && vEast != null) {
            val rv = speedAccuracy.toDouble().coerceAtLeast(0.5).let { it * it }
            updateVelocityAxis(north, vNorth, rv)
            updateVelocityAxis(east,  vEast,  rv)
        }
        return Pair(north.pos, east.pos)
    }

    fun reset() {
        north.init = false
        east.init  = false
        lastImuNs  = Long.MIN_VALUE
    }

    // ── Private math ──────────────────────────────────────────────────────────

    private fun predictAxis(axis: Axis, acc: Double, dt: Double, mpd: Double, stationary: Boolean) {
        val dtm = dt / mpd          // converts velocity (m/s → deg/s)

        // State prediction
        axis.pos = axis.pos + axis.vel * dtm + 0.5 * acc * dt * dtm
        axis.vel = axis.vel + acc * dt
        if (stationary) axis.vel *= ZUPT_DAMPING   // bleed off phantom velocity when still

        // Covariance prediction  P = F*P*F' + Q
        // Wiener velocity model: Q = σ² * [[dt⁴/4/mpd², dt³/2/mpd], [dt³/2/mpd, dt²]]
        val s2 = sigmaAccMs2 * sigmaAccMs2
        val dt2 = dt * dt; val dt3 = dt2 * dt; val dt4 = dt3 * dt
        val qpp = s2 * dt4 / 4.0 / (mpd * mpd)
        val qpv = s2 * dt3 / 2.0 / mpd
        val qvv = s2 * dt2

        val p00n = axis.p00 + 2.0 * axis.p01 * dtm + axis.p11 * dtm * dtm + qpp
        val p01n = axis.p01 + axis.p11 * dtm + qpv
        val p11n = axis.p11 + qvv
        axis.p00 = p00n; axis.p01 = p01n; axis.p11 = p11n
    }

    private fun updateAxis(axis: Axis, measurement: Double, r: Double) {
        val s  = axis.p00 + r               // innovation variance
        val k0 = axis.p00 / s               // Kalman gain → position
        val k1 = axis.p01 / s               // Kalman gain → velocity
        val dz = measurement - axis.pos     // innovation

        axis.pos += k0 * dz
        axis.vel += k1 * dz

        // Covariance update  P = (I − K*H)*P  with H = [1, 0].
        // p11 depends on the OLD p01, so snapshot it before overwriting.
        val p01Old = axis.p01
        axis.p00 = (1.0 - k0) * axis.p00
        axis.p11 = axis.p11 - k1 * p01Old
        axis.p01 = (1.0 - k0) * p01Old
    }

    /** Measurement update against the velocity state (H = [0, 1]). */
    private fun updateVelocityAxis(axis: Axis, vMeasured: Double, rv: Double) {
        val s  = axis.p11 + rv          // innovation variance
        val k0 = axis.p01 / s           // velocity innovation → position
        val k1 = axis.p11 / s           // velocity innovation → velocity
        val dz = vMeasured - axis.vel

        axis.pos += k0 * dz
        axis.vel += k1 * dz

        // Covariance update  P = (I − K*H)*P  with H = [0, 1]. Snapshot old entries.
        val p01Old = axis.p01
        val p11Old = axis.p11
        axis.p00 = axis.p00 - k0 * p01Old
        axis.p01 = p01Old - k0 * p11Old
        axis.p11 = (1.0 - k1) * p11Old
    }

    private fun metersPerDegLng(): Double =
        METERS_PER_DEG_LAT * cos(Math.toRadians(currentLat))

    companion object {
        private const val METERS_PER_DEG_LAT = 111_111.0
        // Horizontal accel below this (m/s²) is treated as "stationary" for ZUPT.
        private const val ZUPT_ACCEL_THRESHOLD = 0.3
        // Per-step velocity decay applied while stationary (~50 Hz → quick decay to 0).
        private const val ZUPT_DAMPING = 0.7
    }
}
