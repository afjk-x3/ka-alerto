package com.macci.kaalerto.map

/** What the offline-pack banner says — kept out of the composable so it can be unit-tested. */
data class PackBannerCopy(val headline: String, val detail: String?, val showProgress: Boolean)

private const val REPORTING_STILL_WORKS = "Gumagana pa rin ang pag-uulat."

/**
 * Honest status, per the project's rule against implying a capability the app does not
 * have. A half-downloaded pack must not look like a working offline map — that is the one
 * claim that cannot break on stage.
 *
 * **A download that cannot start must not read as one that is at 0%.** On a first run with
 * no network the banner sat at "Downloading offline map · 0% / 0 tiles (estimating total)"
 * over a moving bar indefinitely — the spinner `docs/03-architecture.md` §6.4.4 forbids —
 * even though the app already knew it was offline. Offline, it now says it is waiting (or
 * paused, if some tiles made it), shows no bar, and names what still works: filing a report
 * never needed tiles. The download itself resumes on its own once a connection returns.
 */
fun packBannerCopy(state: PackState, isOnline: Boolean): PackBannerCopy {
    val pct = (state as? PackState.Downloading)?.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return when (state) {
        PackState.Unknown -> PackBannerCopy("Tinitingnan ang offline na mapa…", null, showProgress = false)
        PackState.Absent -> PackBannerCopy(
            "Wala pang offline na mapa",
            if (isOnline) "Sinisimulan ang download." else "Kailangan ng koneksyon nang isang beses. $REPORTING_STILL_WORKS",
            showProgress = false,
        )
        is PackState.Downloading -> when {
            isOnline -> PackBannerCopy(
                "Dina-download ang mapa$pct",
                "${state.completedTiles} tile${if (!state.isPrecise) " (tinatantiya ang kabuuan)" else ""}",
                showProgress = true,
            )
            state.completedTiles == 0L -> PackBannerCopy(
                "Naghihintay ng koneksyon",
                "Hindi pa na-download ang mapa. $REPORTING_STILL_WORKS",
                showProgress = false,
            )
            else -> PackBannerCopy(
                "Nakahinto ang download$pct",
                "Itutuloy kapag may koneksyon. $REPORTING_STILL_WORKS",
                showProgress = false,
            )
        }
        is PackState.Ready -> PackBannerCopy(
            "Handa na ang offline na mapa",
            "${state.tileCount} tile · gumagana kahit walang signal",
            showProgress = false,
        )
        is PackState.Failed -> PackBannerCopy("Hindi na-download ang mapa", state.reason, showProgress = false)
    }
}
