package com.nagorithm.app.effort

import com.nagorithm.app.health.SportCategory

/**
 * Fraction of heart-rate reserve assumed for a session with no usable heart rate.
 *
 * Swimming is the case that forced this: most people, including anyone using a chest strap, have
 * no swim-capable heart rate monitor, so a swim arrives as duration and distance only. Discarding
 * those sessions would be worse than estimating them — it would tell a swimmer they did nothing.
 * Zone 3 is the honest default for a deliberate pool session.
 *
 * The assumption runs through the same TRIMP maths as a measured ride rather than a flat score per
 * hour, so it scales with the individual's own reserve and threshold instead of asserting an
 * absolute number that would mean different things for different people.
 */
fun assumedReserveWithoutHeartRate(sport: SportCategory): Double = when (sport) {
    // Zone 3. Nobody swims laps easily, and few swim at threshold for the whole session.
    SportCategory.SWIMMING -> 0.65
    SportCategory.RUNNING -> 0.65
    SportCategory.CYCLING, SportCategory.ROWING -> 0.60
    SportCategory.WALKING -> 0.40
    SportCategory.OTHER -> 0.55
}
