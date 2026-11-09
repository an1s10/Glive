package com.anis.glive.data

data class Category(val id: String, val name: String, val lang: Lang)

data class Channel(
    val id: Int,
    val num: Int,
    val name: String,
    val logo: String,
    val categoryId: String,
    val epgId: String,
    val archive: Boolean,
    val archiveDays: Int,
    val lang: Lang,
)

data class Movie(
    val id: Int,
    val name: String,
    val poster: String,
    val categoryId: String,
    val rating: Double,
    val added: Long,
    val ext: String,
    val lang: Lang,
)

data class Series(
    val id: Int,
    val name: String,
    val poster: String,
    val categoryId: String,
    val rating: Double,
    val added: Long,
    val plot: String,
    val year: String,
    val lang: Lang,
)

data class Program(
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val hasArchive: Boolean,
    val startRaw: String,
) {
    fun isNow(nowSec: Long) = nowSec in start until end
    fun isPast(nowSec: Long) = end <= nowSec
    fun progress(nowSec: Long): Float =
        if (end <= start) 0f else ((nowSec - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    val durationMin: Int get() = ((end - start) / 60).toInt().coerceAtLeast(1)
}

data class MovieInfo(
    val plot: String,
    val genre: String,
    val duration: String,
    val rating: Double,
    val backdrop: String,
    val year: String,
    val cast: String,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val plot: String,
    val duration: String,
    val image: String,
)

data class SeriesInfo(
    val plot: String,
    val genre: String,
    val rating: Double,
    val backdrop: String,
    val year: String,
    val seasons: Map<Int, List<Episode>>,
)

data class Account(val server: String, val user: String, val pass: String) {
    val base: String
        get() {
            var s = server.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s
        }
}
