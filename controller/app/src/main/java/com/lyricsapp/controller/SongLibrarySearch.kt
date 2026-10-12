package com.lyricsapp.controller

import com.lyricsapp.shared.Song
import java.text.Normalizer
import java.util.Locale

enum class SongSortOption(val label: String) {
    RELEVANCE("Best match"),
    TITLE_ASC("Title A–Z"),
    TITLE_DESC("Title Z–A"),
    AUTHOR_ASC("Author A–Z"),
    RECENTLY_UPDATED("Recently updated"),
    RECENTLY_ADDED("Recently added")
}

/** Fuzzy title/author search that tolerates a small number of typing mistakes. */
fun filterAndSortSongs(
    songs: List<Song>,
    query: String,
    sort: SongSortOption
): List<Song> {
    val normalizedQuery = normalizeSongSearch(query)
    val matches = songs.mapNotNull { song ->
        val title = normalizeSongSearch(song.title)
        val author = normalizeSongSearch(song.author.orEmpty())
        val score = if (normalizedQuery.isBlank()) 0 else songSearchScore(normalizedQuery, title, author) ?: return@mapNotNull null
        song to score
    }
    val comparator = when (sort) {
        SongSortOption.RELEVANCE -> compareBy<Pair<Song, Int>> { it.second }
            .thenBy { it.first.title.lowercase(Locale.ROOT) }
        SongSortOption.TITLE_ASC -> compareBy<Pair<Song, Int>> { it.first.title.lowercase(Locale.ROOT) }
        SongSortOption.TITLE_DESC -> compareByDescending<Pair<Song, Int>> { it.first.title.lowercase(Locale.ROOT) }
        SongSortOption.AUTHOR_ASC -> compareBy<Pair<Song, Int>> { it.first.author.orEmpty().lowercase(Locale.ROOT) }
            .thenBy { it.first.title.lowercase(Locale.ROOT) }
        SongSortOption.RECENTLY_UPDATED -> compareByDescending<Pair<Song, Int>> { it.first.updatedAt }
            .thenBy { it.first.title.lowercase(Locale.ROOT) }
        SongSortOption.RECENTLY_ADDED -> compareByDescending<Pair<Song, Int>> { it.first.createdAt }
            .thenBy { it.first.title.lowercase(Locale.ROOT) }
    }
    return matches.sortedWith(comparator).map { it.first }
}

private fun songSearchScore(query: String, title: String, author: String): Int? {
    val fields = listOf(title, author).filter(String::isNotBlank)
    if (fields.any { it == query }) return 0
    if (fields.any { it.startsWith(query) }) return 10
    if (fields.any { it.contains(query) }) return 20

    val queryTokens = query.split(Regex("\\s+")).filter(String::isNotBlank)
    val candidateTokens = fields.flatMap { it.split(Regex("[^\\p{L}\\p{N}]+")) }.filter(String::isNotBlank)
    if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return null

    var totalDistance = 0
    for (token in queryTokens) {
        val best = candidateTokens.mapNotNull { candidate ->
            when {
                candidate.startsWith(token) -> 0
                else -> editDistance(token, candidate).takeIf { it <= maxOf(1, token.length / 3) }
            }
        }.minOrNull() ?: return null
        totalDistance += best
    }
    return 100 + totalDistance
}

private fun normalizeSongSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

private fun editDistance(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        for (j in right.indices) {
            val substitution = if (left[i] == right[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + substitution
            )
        }
        previous = current
    }
    return previous[right.length]
}
