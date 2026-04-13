package com.github.legiontube.db

import androidx.room.TypeConverter
import com.github.legiontube.api.JsonHelper
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.Path

object Converters {
    @TypeConverter
    fun localDateToString(localDate: LocalDate?): String? = localDate?.toString()

    @TypeConverter
    fun stringToLocalDate(string: String?): LocalDate? = string?.let { LocalDate.parse(it) }

    @TypeConverter
    fun pathToString(path: Path?): String? = path?.toString()

    @TypeConverter
    fun stringToPath(string: String?): Path? = string?.let { Path(it) }

    @TypeConverter
    fun stringListToJson(value: List<String>): String = JsonHelper.json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = JsonHelper.json.decodeFromString<List<String>>(value)
}
