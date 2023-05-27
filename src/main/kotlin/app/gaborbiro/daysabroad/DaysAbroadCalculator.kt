package app.gaborbiro.daysabroad

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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
        val state = State()

        println("Processing ${System.getProperty("user.dir")}${System.getProperty("file.separator")}$filePath")

        val start = start ?: DEFAULT_START
        val end = end ?: ZonedDateTime.now()

        val startFormatted = dateTimeFormatter.format(start)
        val endFormatted = dateTimeFormatter.format(end)
        println("Target range: $startFormatted - $endFormatted")

        recordsParser.parse(filePath, verbose) { record ->
            processRecord(
                record = record,
                state = state,
                start = start,
                end = end,
                centerLongitude = centerLongitude,
                centerLatitude = centerLatitude,
                radius = radius,
                verbose = verbose
            )
        }

        analyse(state, generateURLsForTransitions)
    }

    private fun processRecord(
        record: Record,
        state: State,
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
            val day = state.dayMap[dayIndex] ?: run {
                Day(fistTimestamp = record.timestamp, firstLat = record.latitude, firstLon = record.longitude)
                    .also { state.dayMap[dayIndex] = it }
            }
            val distance = distanceToCenter(record.longitude, record.latitude, centerLongitude, centerLatitude)

            if (distance > radius) {
                day.outsideCount = (day.outsideCount ?: 0) + 1 // outside
            } else {
                day.insideCount = (day.insideCount ?: 0) + 1 // inside
            }
            day.lastLat = record.latitude
            day.lastLon = record.longitude

            state.lowestTimestamp =
                state.lowestTimestamp?.let { if (record.timestamp < it) record.timestamp else it } ?: record.timestamp
            state.highestTimestamp =
                state.highestTimestamp?.let { if (record.timestamp > it) record.timestamp else it } ?: record.timestamp
        }
    }

    private fun analyse(
        state: State,
        generateURLsForTransitions: Boolean,
    ) {
        val daysNotInside = state.dayMap.count { it.value.status() in arrayOf(DayStatus.OUTSIDE, DayStatus.TRANSIT) }
        val sinceFormatted = dateTimeFormatter.format(state.lowestTimestamp)
        val untilFormatted = dateTimeFormatter.format(state.highestTimestamp)
        val resultingSize = ChronoUnit.DAYS.between(state.lowestTimestamp, state.highestTimestamp) + 1

        println("Result range: $sinceFormatted - $untilFormatted ($resultingSize days)")
        println("Number of days spent outside (including transit days): $daysNotInside")

        val (valid, invalid) = state.dayMap.values.partition { it.status() != DayStatus.UNKNOWN }

        println("\nQuality of data:")
        println(
            "${valid.size} days have valid coordinates" +
                    "\nNumber of days that are not captured: ${resultingSize - state.dayMap.size}" +
                    "\nNumber of days that are captured but do not have enough gps hits: ${invalid.size}"
        )

        if (generateURLsForTransitions) {
            if (state.dayMap.values.any { it.status() == DayStatus.TRANSIT }) {
                println("Transitions:")
                state.dayMap.keys.forEach {
                    val day = state.dayMap[it]!!
                    if (day.status() == DayStatus.TRANSIT) {
                        val fromURL = "https://www.google.com/maps/@%f,%f,9.25z".format(
                            convertLatitude(day.firstLat),
                            convertLongitude(day.firstLon)
                        )
                        val toURL = "https://www.google.com/maps/@%f,%f,9.25z".format(
                            convertLatitude(day.lastLat!!),
                            convertLongitude(day.lastLon!!)
                        )
                        val date = day.fistTimestamp
                        println("$date, $fromURL -> $toURL (in: ${day.insideCount}, out: ${day.outsideCount})")
                    }
                }
            }
        }
    }
}

data class State(
    var lowestTimestamp: ZonedDateTime? = null,
    var highestTimestamp: ZonedDateTime? = null,
    val dayMap: MutableMap<Int, Day> = mutableMapOf(),
)

private fun processLine(line: String, key: String, value: String, record: Record.Builder): Record.Builder {
    val value = when (key) {
        KEY_TIMESTAMP -> line.substring(line.indexOf(':') + 3, line.length - 1)
        else -> {
            when {
                value[0] == '"' -> value.substring(1, value.length - 2)
                value[value.length - 1] == ',' -> value.substring(0, value.length - 1)
                else -> value
            }
        }
    }

    return when (key) {
        KEY_TIMESTAMP -> {
            if (record.timestamp != null) {
                println(record)
                throw IllegalStateException("timestamp expected to be unset")
            }
            record.copy(timestamp = ZonedDateTime.parse(value))
        }

        KEY_LATITUDE -> {
            if (record.latitude != null) {
                println(record)
                throw IllegalStateException("latitude expected to be unset")
            }
            record.copy(latitude = value.toLong())
        }

        KEY_LONGITUDE -> {
            if (record.longitude != null) {
                println(record)
                throw IllegalStateException("longitude expected to be unset")
            }
            record.copy(longitude = value.toLong())
        }

        KEY_ACCURACY -> {
            if (record.accuracy != null) {
                println(record)
                throw IllegalStateException("accuracy expected to be unset")
            }
            record.copy(accuracy = value.toLong())
        }

        else -> record
    }
}

fun distanceToCenter(longitude: Long, latitude: Long, centerLatitude: Double, centerLongitude: Double): Double {
    return distance(convertLongitude(longitude), convertLatitude(latitude), centerLongitude, centerLatitude, 'K')
}

private val DEFAULT_START = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)