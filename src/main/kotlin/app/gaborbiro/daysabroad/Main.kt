package app.gaborbiro.daysabroad

import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


fun main(args: Array<String>) {
    Main().main(args)
}

class Main {

    private val calculator: DaysAbroadCalculator by lazy { DaysAbroadCalculator(RecordsParserImpl()) }

    fun main(args: Array<String>) {
        BufferedReader(InputStreamReader(System.`in`)).use { reader ->
            checkFilePath(args)
            println()
            println("Press enter to exit")
            reader.readLine()
        }
    }

    private fun checkFilePath(
        args: Array<String>,
    ) {
        val filePath = args.getArg("-f")

        filePath?.let {
            checkDates(args, filePath)
        } ?: run {
            println("No input file (-f) specified")
        }
    }

    private fun checkDates(args: Array<String>, filePath: String) {
        val start = parseConsoleDate(args.getArg("-s"))
        val end = parseConsoleDate(args.getArg("-e"))
        checkCenterPoint(args, filePath, start, end)
    }

    private fun checkCenterPoint(
        args: Array<String>,
        filePath: String,
        start: ZonedDateTime? = null,
        end: ZonedDateTime? = null,
    ) {
        val (longitude, latitude) = args.getArg("-l")
            ?.let {
                val latLon = it.split(",")
                latLon[0].toDouble() to latLon[1].toDouble()
            }
            ?: (null to null)

        checkRadius(
            args = args,
            filePath = filePath,
            start = start,
            end = end,
            centerLongitude = longitude ?: DEFAULT_LON_CENTRE,
            centerLatitude = latitude ?: DEFAULT_LAT_CENTRE,
        )
    }

    private fun checkRadius(
        args: Array<String>,
        filePath: String,
        start: ZonedDateTime? = null,
        end: ZonedDateTime? = null,
        centerLongitude: Double,
        centerLatitude: Double,
    ) {
        val radius = args.getArg("-r")?.toInt()

        calculator.countDaysAbroad(
            filePath = filePath,
            start = start,
            end = end,
            centerLongitude = centerLongitude,
            centerLatitude = centerLatitude,
            radius = radius ?: DEFAULT_THRESHOLD_KM,
            verbose = args.contains("-v"),
            generateURLsForTransitions = args.contains("-t"),
        )
    }
}
