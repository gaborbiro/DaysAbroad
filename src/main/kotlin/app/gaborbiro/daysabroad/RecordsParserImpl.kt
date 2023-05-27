package app.gaborbiro.daysabroad

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import java.io.BufferedReader
import java.io.FileReader
import java.time.ZonedDateTime

class RecordsParserImpl : RecordsParser {

    override fun parse(
        filePath: String,
        verbose: Boolean,
        onRecordReady: (Record) -> Unit
    ) {
        val jsonReader = JsonFactory().createParser(BufferedReader(FileReader(filePath)))
        assert(jsonReader.nextToken() == JsonToken.START_OBJECT) { "File is expected to start with Json object" }
        assert(jsonReader.nextToken().name == "locations") { "File is expected to start with 'locations'" }
        assert(jsonReader.nextToken() == JsonToken.START_ARRAY) { "'locations' object is expected to start with array" }
        var builder = Record.Builder()
        var level = 1
        do {
            val token = jsonReader.nextToken()

            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                level++
                continue
            }

            if (token == JsonToken.FIELD_NAME && level == 2) {
                val name = jsonReader.text
                when (name) {
                    KEY_TIMESTAMP -> {
                        jsonReader.nextToken()
                        builder = builder.copy(timestamp = ZonedDateTime.parse(jsonReader.text))
                    }

                    KEY_LATITUDE -> {
                        jsonReader.nextToken()
                        builder = builder.copy(latitude = jsonReader.longValue)
                    }

                    KEY_LONGITUDE -> {
                        jsonReader.nextToken()
                        builder = builder.copy(longitude = jsonReader.longValue)
                    }

                    KEY_ACCURACY -> {
                        jsonReader.nextToken()
                        builder = builder.copy(accuracy = jsonReader.longValue)
                    }
                }
                continue
            }

            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                if (level == 2) {
                    if (builder.ready()) {
                        onRecordReady(builder.build())
                        builder = Record.Builder()
                    } else if (verbose) {
                        println("Malformed record: $builder")
                    }
                }
                level--
            }
        } while (level > 0)
    }
}