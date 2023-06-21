package app.gaborbiro.daysabroad

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class DaysAbroadCalculator(private val recordsParser: RecordsParser) {

    fun countDaysAbroad(
        filePath: String,
        start: ZonedDateTime? = null,
        end: ZonedDateTime? = null,
        centerLongitude: Double,
        centerLatitude: Double,
        radius: Int,
        verbose: Boolean = false,
        generateURLsForTransitions: Boolean = false,
    ) {
        val result = Result()

        println("\nProcessing ${System.getProperty("user.dir")}${System.getProperty("file.separator")}$filePath")

        val start = start ?: DEFAULT_START
        val end = end ?: ZonedDateTime.now()

        val startFormatted = dateTimeFormatter.format(start)
        val endFormatted = dateTimeFormatter.format(end)
        println("Target range: $startFormatted - $endFormatted")
        val c = CharArray(23) { ' ' }
        c[0] = '\r'
        c[1] = '['
        c[22] = ']'
        recordsParser.parse(filePath, verbose, { progress ->
            for(i in 2 until (progress * 20).toInt() + 2) {
                c[i] = '.'
            }
            print(String(c) + " ${(progress * 100).toInt()}%")
        }) { record ->
            processRecord(
                record = record,
                result = result,
                start = start,
                end = end,
                centerLongitude = centerLongitude,
                centerLatitude = centerLatitude,
                radius = radius,
                verbose = verbose
            )
        }

        analyse(result, generateURLsForTransitions)
    }

    private fun processRecord(
        record: Record,
        result: Result,
        start: ZonedDateTime? = null,
        end: ZonedDateTime? = null,
        centerLongitude: Double,
        centerLatitude: Double,
        radius: Int,
        verbose: Boolean = false,
    ) {
        if (record.accuracy > MIN_ACCURACY_METERS) {
            if (verbose) {
                println(
                    "Low accuracy:" +
                            "\nlongitude: ${convertLongitude(record.longitude)}, " +
                            "\nlatitude: ${convertLatitude(record.latitude)}, " +
                            "\ntime: ${record.timestamp}, " +
                            "\naccuracy: ${record.accuracy}"
                )
            }
            return
        }

        if (record.timestamp >= start &&
            record.timestamp <= end &&
            (record.latitude == 374219983L && record.longitude == -1220840000L)
                .not()
        ) {
            val dayIndex = record.timestamp.dayIndex()
            val day = result.dayMap[dayIndex] ?: run {
                Day(
                    fistTimestamp = record.timestamp,
                    firstLat = record.latitude,
                    firstLon = record.longitude
                )
                    .also { result.dayMap[dayIndex] = it }
            }
            val distance =
                distanceToCenter(record.longitude, record.latitude, centerLongitude, centerLatitude)

            if (distance > radius) {
                day.outsideHitCount = (day.outsideHitCount ?: 0) + 1 // outside
                day.isLastCoordInside = false
            } else {
                day.insideHitCount = (day.insideHitCount ?: 0) + 1 // inside
                day.isLastCoordInside = true
            }
            day.lastLat = record.latitude
            day.lastLon = record.longitude

            result.lowestTimestamp =
                result.lowestTimestamp?.let { if (record.timestamp < it) record.timestamp else it }
                    ?: record.timestamp
            result.highestTimestamp =
                result.highestTimestamp?.let { if (record.timestamp > it) record.timestamp else it }
                    ?: record.timestamp
        }
    }

    private fun analyse(
        result: Result,
        generateURLsForTransitions: Boolean,
    ) {
        println("\n\n================= Results =================")
        printStats(result)

        if (generateURLsForTransitions) {
            printTransitions(result)
        }
        val periods = getPeriods(result)
        printPeriods(periods)

        println("\nLast contiguous 365 days inside:")
        val insideYear = getLastContiguousPeriod(periods, 365, 15)
        printPeriods(insideYear)
    }

    private fun printStats(result: Result) {
        val daysOutside = result.dayMap.count { it.value.isOutside() }
        val sinceFormatted = dateTimeFormatter.format(result.lowestTimestamp)
        val untilFormatted = dateTimeFormatter.format(result.highestTimestamp)
        val resultingSize =
            ChronoUnit.DAYS.between(result.lowestTimestamp, result.highestTimestamp) + 1

        println("\nResult range: $sinceFormatted - $untilFormatted ($resultingSize days)")
        println("Number of days spent outside (including transit out days): $daysOutside")

        val (valid, invalid) = result.dayMap.values.partition { it.status() != DayStatus.UNKNOWN }

        println("\nQuality of data:")
        println(
            "${valid.size} days have valid coordinates" +
                    "\nNumber of days that are not captured: ${resultingSize - result.dayMap.size}" +
                    "\nNumber of days that are captured but do not have enough gps hits: ${invalid.size}"
        )
    }

    private fun printTransitions(result: Result) {
        println("\nTransition days:")
        if (result.dayMap.values.any { it.status() == DayStatus.TRANSIT_IN || it.status() == DayStatus.TRANSIT_OUT }) {
            result.dayMap.keys.forEach {
                val day = result.dayMap[it]!!
                if (day.status() == DayStatus.TRANSIT_IN || day.status() == DayStatus.TRANSIT_OUT) {
                    val fromURL = "https://www.google.com/maps/place/@%f,%f,15z".format(
                        convertLatitude(day.firstLat),
                        convertLongitude(day.firstLon)
                    )
                    val toURL = "https://www.google.com/maps/place/@%f,%f,15z".format(
                        convertLatitude(day.lastLat!!),
                        convertLongitude(day.lastLon!!)
                    )
                    val date = day.fistTimestamp.toLocalDate()
                    println("\n$date: ${day.insideHitCount} inside, ${day.outsideHitCount} outside\n$fromURL -> $toURL")
                }
            }
        } else {
            println("none")
        }
    }

    private fun printPeriods(periods: List<Pair<Day, Day>>) {
        println()
        periods.forEach {
            val start = it.first.fistTimestamp
            val startStr = dateFormatter.format(start)
            val end = it.second.fistTimestamp
            val endStr = dateFormatter.format(end)
            val status = it.first.status().let {
                when (it) {
                    DayStatus.TRANSIT_OUT -> DayStatus.OUTSIDE
                    DayStatus.TRANSIT_IN -> DayStatus.INSIDE
                    else -> it
                }
            }
            val days = (ChronoUnit.HOURS.between(start, end) / 24.0).roundToInt() + 1

            println("$startStr\t$endStr\t$status\t$days")
        }
    }

    private fun getPeriods(result: Result): List<Pair<Day, Day>> {
        return result.dayMap.values.toList()
            .sortedBy { it.fistTimestamp }
            .fold(mutableListOf()) { acc: MutableList<Pair<Day, Day>>, day: Day ->
                if (acc.isNotEmpty()) {
                    val finalLastStatus = when (val lastStatus = acc.last().second.status()) {
                        DayStatus.TRANSIT_IN -> DayStatus.INSIDE
                        DayStatus.TRANSIT_OUT -> DayStatus.OUTSIDE
                        else -> lastStatus
                    }
                    val finalDayStatus = when (val dayStatus = day.status()) {
                        DayStatus.TRANSIT_IN -> DayStatus.INSIDE
                        DayStatus.TRANSIT_OUT -> DayStatus.OUTSIDE
                        else -> dayStatus
                    }
                    if (finalLastStatus == finalDayStatus) {
                        acc[acc.size - 1] = acc.last().first to day
                    } else {
                        acc.add(day to day)
                    }
                } else {
                    acc.add(day to day)
                }
                acc
            }
    }

    private fun getLastContiguousPeriod(
        periods: List<Pair<Day, Day>>,
        days: Int,
        maxGapDays: Int
    ): List<Pair<Day, Day>> {
        val size: (Pair<Day, Day>) -> Int = { (start, end) ->
            (ChronoUnit.HOURS.between(
                start.fistTimestamp,
                end.fistTimestamp
            ) / 24.0).roundToInt() + 1
        }

        var done = false
        return periods.reversed()
            .fold(mutableListOf()) { acc: MutableList<Pair<Day, Day>>, period ->
                if (acc.isEmpty()) {
                    if (period.first.isInside()) {
                        acc.add(0, period)
                    }
                    if (acc.sumOf { size(it) } >= days) {
                        done = true
                    }
                } else if (!done) {
                    if (period.first.isInside()) {
                        acc.add(0, period)
                    } else {
                        if (size(period) > maxGapDays) {
                            acc.clear()
                        } else {
                            acc.add(0, period)
                        }
                    }
                    if (acc.sumOf { size(it) } >= days) {
                        done = true
                    }
                }
                acc
            }
    }
}

data class Result(
    var lowestTimestamp: ZonedDateTime? = null,
    var highestTimestamp: ZonedDateTime? = null,
    val dayMap: MutableMap<Int, Day> = mutableMapOf(),
) {
    override fun toString(): String {
        return "Result(lowestTimestamp=${lowestTimestamp?.format(dateTimeFormatter)}, " +
                "highestTimestamp=${highestTimestamp?.format(dateTimeFormatter)}, " +
                "dayMap=$dayMap)"
    }
}

fun distanceToCenter(
    longitude: Long,
    latitude: Long,
    centerLatitude: Double,
    centerLongitude: Double
): Double {
    return distance(
        convertLongitude(longitude),
        convertLatitude(latitude),
        centerLongitude,
        centerLatitude,
        'K'
    )
}

private val DEFAULT_START = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)