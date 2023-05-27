package app.gaborbiro.daysabroad

import java.io.BufferedReader
import java.io.FileReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

fun Array<String>.getArg(name: String): String? {
    return if (contains(name)) {
        val index = lastIndexOf(name)
        get(index + 1)
    } else {
        null
    }
}

fun convertLongitude(value: Long) = (if (value > 1800000000) value - 4294967296 else value) / 10.0.pow(7.0)
fun convertLatitude(value: Long) = (if (value > 900000000) value - 4294967296 else value) / 10.0.pow(7.0)

fun distance(lon1: Double, lat1: Double, lon2: Double, lat2: Double, unit: Char): Double {
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

fun Double.deg2rad() = this * Math.PI / 180.0
fun Double.rad2deg() = this * 180.0 / Math.PI

val dateTimeFormatter = DateTimeFormatter.ofPattern("YYYY/MMM/d HH:mm a")
val dateFormatter = DateTimeFormatter.ofPattern("YYYY/MMM/d")

fun ZonedDateTime.dayIndex() = year * 1000 + dayOfYear
