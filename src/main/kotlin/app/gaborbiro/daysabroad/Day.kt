package app.gaborbiro.daysabroad

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Day(
    val fistTimestamp: ZonedDateTime,
    val firstLat: Long,
    val firstLon: Long,
    var lastLat: Long? = null,
    var lastLon: Long? = null,
    var isLastCoordInside: Boolean? = null,
    var outsideHitCount: Int? = null,
    var insideHitCount: Int? = null
) {
    override fun toString(): String {
        return "Day(date=${fistTimestamp.format(dateTimeFormatter)}, out=$outsideHitCount, in=$insideHitCount)"
    }

    fun status(): DayStatus {
        val enoughHitsInside = insideHitCount != null && insideHitCount!! > DAY_OUTSIDE_THRESHOLD
        val enoughHitsOutside = outsideHitCount != null && outsideHitCount!! > DAY_OUTSIDE_THRESHOLD

        return when {
            enoughHitsInside && enoughHitsOutside -> if (isLastCoordInside == true) DayStatus.TRANSIT_IN else DayStatus.TRANSIT_OUT
            enoughHitsInside -> DayStatus.INSIDE
            enoughHitsOutside -> DayStatus.OUTSIDE
            else -> DayStatus.UNKNOWN
        }
    }

    fun isInside() = status().let { it == DayStatus.INSIDE || it == DayStatus.TRANSIT_IN }
    fun isOutside() = status().let { it == DayStatus.OUTSIDE || it == DayStatus.TRANSIT_OUT }
}

private const val DAY_OUTSIDE_THRESHOLD = 25

enum class DayStatus {
    INSIDE, OUTSIDE, TRANSIT_IN, TRANSIT_OUT, UNKNOWN
}