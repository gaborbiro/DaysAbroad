package app.gaborbiro.daysabroad

import java.time.ZonedDateTime

interface RecordsParser {
    fun parse(
        filePath: String,
        verbose: Boolean,
        onRecordReady: (Record) -> Unit
    )
}

class Record private constructor(
    val timestamp: ZonedDateTime,
    val latitude: Long,
    val longitude: Long,
    val accuracy: Long,
) {

    data class Builder(
        val timestamp: ZonedDateTime? = null,
        val latitude: Long? = null,
        val longitude: Long? = null,
        val accuracy: Long? = null,
    ) {
        fun ready() = latitude != null && longitude != null && timestamp != null && accuracy != null

        fun build() = Record(timestamp!!, latitude!!, longitude!!, accuracy!!)
    }

    override fun toString(): String {
        return "Record(timestamp=$timestamp, latitude=$latitude, longitude=$longitude, accuracy=$accuracy)"
    }
}
