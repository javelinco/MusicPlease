package com.javelinco.localmusicplayer.ui.navigation

internal enum class Destination {
    HOME,
    LIBRARY,
    MORE,
    NOW_PLAYING,
    QUEUE,
    MUSIC_FOLDERS,
    BACKUP,
    SETTINGS,
}

internal fun screenHeaderTitle(destination: Destination, homeHasSession: Boolean): String =
    when (destination) {
        Destination.HOME -> if (homeHasSession) "Now playing" else "Recently played"
        Destination.LIBRARY -> "Library"
        Destination.MORE -> "More"
        Destination.NOW_PLAYING -> "Now playing"
        Destination.QUEUE -> "Queue"
        Destination.MUSIC_FOLDERS -> "Music folders & scanning"
        Destination.BACKUP -> "Backup & restore"
        Destination.SETTINGS -> "Appearance"
    }

internal data class NavigationHistory(
    val current: Destination? = null,
    val previous: List<Destination> = emptyList(),
) {
    val resolvedCurrent: Destination get() = current ?: Destination.LIBRARY

    fun navigateTo(target: Destination): NavigationHistory {
        val source = resolvedCurrent
        return if (target == source) this
        else NavigationHistory(target, previous + source)
    }

    fun goBack(): NavigationHistory = if (previous.isNotEmpty()) {
        NavigationHistory(previous.last(), previous.dropLast(1))
    } else {
        NavigationHistory(Destination.HOME)
    }
}

internal fun saveNavigationHistory(history: NavigationHistory): List<String> =
    listOf(history.current?.name.orEmpty()) + history.previous.map { it.name }

internal fun restoreNavigationHistory(values: List<String>): NavigationHistory = NavigationHistory(
    current = values.firstOrNull()?.takeIf { it.isNotEmpty() }?.let(Destination::valueOf),
    previous = values.drop(1).map(Destination::valueOf),
)
