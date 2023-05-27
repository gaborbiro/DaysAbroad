package app.gaborbiro.daysabroad

import java.time.ZonedDateTime

class Day(
    val fistTimestamp: ZonedDateTime,
    val firstLat: Long,
    val firstLon: Long,
    var lastLat: Long? = null,
    var lastLon: Long? = null,
    var outsideCount: Int? = null,
    var insideCount: Int? = null
) {
    override fun toString(): String {
        return "Day(outside=$outsideCount, inside=$insideCount)"
    }

    fun status(): DayStatus {
        val enoughDaysInside = insideCount != null && insideCount!! > DAY_OUTSIDE_THRESHOLD
        val enoughDaysOutside = outsideCount != null && outsideCount!! > DAY_OUTSIDE_THRESHOLD

        return when {
            enoughDaysInside && enoughDaysOutside -> DayStatus.TRANSIT
            enoughDaysInside -> DayStatus.INSIDE
            enoughDaysOutside -> DayStatus.OUTSIDE
            else -> DayStatus.UNKNOWN
        }
    }
}

private const val DAY_OUTSIDE_THRESHOLD = 25

enum class DayStatus {
    INSIDE, OUTSIDE, TRANSIT, UNKNOWN
}