import java.io.BufferedReader
import java.io.FileReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

fun main(args: Array<String>) {
    Main().main(args)
}

class Main {

    fun main(args: Array<String>) {
        args.getArg("-s")?.let {
            calculateDaysOutside(it, args)
        } ?: run {
            println("Options:")
            println("-s <file path>: Required. Days spent outside of UK. File path must point to google location history takeout file in json format. (See https://takeout.google.com/settings/takeout)")
            println("-t <int>: Optional (default: 5 years). Number of days in the past to analyse")
            println("-l <float>,<float>: Optional (default: $DEFAULT_LON_CENTRE,$DEFAULT_LAT_CENTRE). Centroid, with a radius of $DEFAULT_THRESHOLD_KM km")
            println("-r <int>: Optional (default: $DEFAULT_THRESHOLD_KM km)")
            println("-d: Print debug information")
        }
    }

    private fun calculateDaysOutside(filePath: String, args: Array<String>) {
        args.getArg("-t")?.let {
            calculateDaysOutside(filePath, it.toLong(), args)
        } ?: run {
            calculateDaysOutside(filePath, DEFAULT_PERIOD_DAYS, args)
        }
    }

    private fun calculateDaysOutside(filePath: String, days: Long, args: Array<String>) {
        args.getArg("-l")?.let {
            val latLon = it.split(",")
            if (latLon.size == 2) {
                val longitude = latLon[0].toDouble()
                val latitude = latLon[1].toDouble()
                calculateDaysOutside(filePath, days, longitude, latitude, args)
            }
        } ?: run {
            calculateDaysOutside(filePath, days, DEFAULT_LON_CENTRE, DEFAULT_LAT_CENTRE, args)
        }
    }

    private fun calculateDaysOutside(
        filePath: String,
        days: Long,
        centerLongitude: Double,
        centerLatitude: Double,
        args: Array<String>
    ) {
        args.getArg("-r")?.let {
            calculateDaysOutside(filePath, days, centerLongitude, centerLatitude, it.toInt(), args)
        } ?: run {
            calculateDaysOutside(filePath, days, centerLongitude, centerLatitude, DEFAULT_THRESHOLD_KM, args)
        }
    }

    private fun calculateDaysOutside(
        filePath: String,
        days: Long,
        centerLongitude: Double,
        centerLatitude: Double,
        radius: Int,
        args: Array<String>
    ) {
        var tokens: List<String>
        var key: String
        var value: String
        var timestamp: Long? = null
        var lastTimestamp: Long? = null
        var latitude: Long? = null
        var longitude: Long? = null
        var distance: Double
        var accuracy: Long? = null

        val since: OffsetDateTime = Instant.now().atOffset(ZoneOffset.UTC).minusDays(days - 1)
        val sinceMillis = since.toInstant().toEpochMilli()
        val dayMap: MutableMap<Int, Day> = mutableMapOf()

        println("Processing '$filePath'...")
        val reader = BufferedReader(FileReader(filePath))
        reader.readLine()
        reader.readLine()
        reader.forEachLine { line ->
            if (run { tokens = line.split(":"); tokens.size } == 2) {
                key = tokens[0].trim().let {
                    it.substring(1, it.length - 1)
                }
                if (key in arrayOf(KEY_LATITUDE, KEY_LONGITUDE, KEY_TIMESTAMP, KEY_ACCURACY)) {
                    value = tokens[1].trim().let {
                        when {
                            it[0] == '"' -> it.substring(1, it.length - 2)
                            it[it.length - 1] == ',' -> it.substring(0, it.length - 1)
                            else -> it
                        }
                    }
                    when (key) {
                        KEY_TIMESTAMP -> timestamp = value.toLong()
                        KEY_LATITUDE -> latitude = value.toLong()
                        KEY_LONGITUDE -> longitude = value.toLong()
                        KEY_ACCURACY -> accuracy = value.toLong()
                    }
                    arrayOf(latitude, longitude, timestamp, accuracy).runOrNull { (lat, lon, time, acc) ->
                        if (acc > 1000) {
                            if (args.contains("-d")) {
                                println(
                                    "Low accuracy coordinate ignored (lon: ${convertLongitude(lon)}, lat: ${
                                        convertLatitude(
                                            lat
                                        )
                                    }, time: $time, accuracy: $acc)"
                                )
                            }
                            return@runOrNull
                        }
                        distance = distanceToHome(lon, lat, centerLongitude, centerLatitude)
                        if (time >= sinceMillis && !(lat == 374219983L && lon == -1220840000L)) {
                            val dayIndex = TimeUnit.MILLISECONDS.toDays(time).toInt()
                            val day = dayMap[dayIndex] ?: run {
                                Day().also { dayMap[dayIndex] = it }
                            }
                            if (distance > radius) {
                                day.outside = (day.outside ?: 0) + 1 // outside
                            } else {
                                day.inside = (day.inside ?: 0) + 1 // inside
                            }
                        }
                        latitude = null
                        longitude = null
                        lastTimestamp = timestamp
                        timestamp = null
                    }
                }
            }
        }

        val daysOutside = dayMap.count { it.value.run { inside == null || inside!! < 10 } }
        val lastDate: OffsetDateTime = Instant.ofEpochMilli(lastTimestamp!!).atOffset(ZoneOffset.UTC)
        val formatter = DateTimeFormatter.ofPattern("YYYY/MMM/d HH:mm a")
        val sinceFormatted = formatter.format(since)
        val untilFormatted = formatter.format(lastDate)
        println("Between $sinceFormatted and $untilFormatted")
        println("Number of days spent entirely outside are $daysOutside")
        println("Number of days that had valid coordinates: ${dayMap.size}. Missing ${days - dayMap.size} days.")
    }
}

// Jonezys Snack Van, 0SU, York Rd, Knaresborough, UK
private const val DEFAULT_LON_CENTRE = 54.0085726
private const val DEFAULT_LAT_CENTRE = -1.4301757
private const val DEFAULT_THRESHOLD_KM = 381
private const val DEFAULT_PERIOD_DAYS = 5 * 365L

fun convertLongitude(value: Long) = (if (value > 1800000000) value - 4294967296 else value) / 10.0.pow(7.0)
fun convertLatitude(value: Long) = (if (value > 900000000) value - 4294967296 else value) / 10.0.pow(7.0)

fun distanceToHome(longitude: Long, latitude: Long, centerLatitude: Double, centerLongitude: Double): Double {
    return distance(convertLongitude(longitude), convertLatitude(latitude), centerLongitude, centerLatitude, 'K')
}

private fun distance(lon1: Double, lat1: Double, lon2: Double, lat2: Double, unit: Char): Double {
    val theta = lon1 - lon2
    var dist =
        sin(lat1.deg2rad()) * sin(lat2.deg2rad()) + cos(lat1.deg2rad()) * cos(lat2.deg2rad()) * cos(theta.deg2rad())
    dist = acos(dist).rad2deg()
    return when (unit) {
        'K' -> dist * 1.609344
        'N' -> dist * 0.8684
        else -> throw IllegalArgumentException("Only 'K' and 'N' are allowed")
    } * 60.0 * 1.1515
}

fun Array<Long?>.runOrNull(runnable: (Array<Long>) -> Unit): Unit? {
    return if (this.all { it != null }) {
        runnable.invoke(this.map { it!! }.toTypedArray())
    } else {
        null
    }
}

private fun Double.deg2rad() = this * Math.PI / 180.0
private fun Double.rad2deg() = this * 180.0 / Math.PI

class Day(var outside: Int? = null, var inside: Int? = null
) {
    override fun toString(): String {
        return "Day(outside=$outside, inside=$inside)"
    }
}

class LonLat(
    val longitude: Double,
    val latitude: Double
)

private const val KEY_TIMESTAMP = "timestampMs"
private const val KEY_LATITUDE = "latitudeE7"
private const val KEY_LONGITUDE = "longitudeE7"
private const val KEY_ACCURACY = "accuracy"

fun Array<String>.verify(name: String) = contains(name)

fun Array<String>.getArg(name: String): String? {
    return if (contains(name)) {
        val index = lastIndexOf(name)
        get(index + 1)
    } else {
        null
    }
}