package com.macci.kaalerto.map

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.route.LatLon
import com.macci.kaalerto.route.RouteOption
import com.macci.kaalerto.route.describeRoute
import com.macci.kaalerto.ui.theme.LocalKaAlertoColors

/** Where the routes panel is in its life. `null` in the map screen means no panel at all. */
data class RouteUi(
    val target: LatLon,
    val status: Status,
    val options: List<RouteOption> = emptyList(),
    val active: Int = 0,
    val origin: LatLon? = null,
) {
    enum class Status { LOCATING, LOADING, DONE, NO_CONNECTION, NO_ROUTE, NO_LOCATION, SERVICE_ERROR }
}

/**
 * Routes send the phone's position and the destination to a third-party routing server, which
 * nothing else in this app does (events only ever go to our own Supabase, redacted). So the
 * first use is preceded by a disclosure the resident has to accept, once.
 */
object RoutePrefs {
    private const val FILE = "kaalerto_routes"
    private const val KEY = "disclosure_accepted"

    fun accepted(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun accept(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

@Composable
fun RouteDisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kailangan ng internet", "This needs the internet")) },
        text = {
            Text(
                tr(
                    "Para makuha ang ruta, ipapadala ang kinaroroonan mo ngayon at ang pupuntahan sa isang serbisyo sa internet na humahanap ng ruta. Hindi kasama ang pangalan mo. Ito lang ang bahagi ng app na nagpapadala ng lokasyon sa labas.",
                    "To get the route, your current location and the destination are sent to an outside service that finds directions. Your name is not included. This is the only part of the app that sends location outside your phone.",
                ),
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(tr("Sige", "Continue")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Kanselahin", "Cancel")) } },
    )
}

/**
 * The route options, one line each. It ranks by the flooded reports a route passes, and says
 * so: a road nobody has reported on looks clear here, which is not the same as being safe.
 */
@Composable
fun RoutePanel(
    ui: RouteUi,
    onPick: (Int) -> Unit,
    onOpenInMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKaAlertoColors.current
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                tr("Mga ruta", "Routes"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = tr("Isara", "Close"))
            }
        }

        when (ui.status) {
            RouteUi.Status.LOCATING -> Note(tr("Hinahanap ang lokasyon mo…", "Finding your location…"))
            RouteUi.Status.LOADING -> Note(tr("Kumukuha ng mga ruta…", "Getting routes…"))
            RouteUi.Status.NO_LOCATION -> Failure(
                tr("Hindi makuha ang lokasyon mo. Buksan ang GPS at subukan ulit.", "Couldn't get your location. Turn on GPS and try again."),
                onOpenInMaps,
            )
            RouteUi.Status.NO_CONNECTION -> Failure(
                tr("Walang internet, kaya walang ruta sa app. Sa mapa lang ang lugar.", "No internet, so no routes in the app. The spot is still on the map."),
                onOpenInMaps,
            )
            RouteUi.Status.NO_ROUTE -> Failure(tr("Walang nakitang ruta sa kalsada papunta roon.", "No road route was found to that spot."), onOpenInMaps)
            RouteUi.Status.SERVICE_ERROR -> Failure(tr("Hindi gumana ang routing server ngayon.", "The routing server didn't respond."), onOpenInMaps)
            RouteUi.Status.DONE -> {
                ui.options.forEachIndexed { i, o ->
                    val selected = i == ui.active
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(
                                if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground) else BorderStroke(1.dp, colors.border),
                            )
                            .clickable { onPick(i) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(describeRoute(o), fontWeight = FontWeight.SemiBold)
                            Text(
                                floodSummary(o),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (o.safest) {
                            Text(
                                tr("Pinakakaunti ang baha", "Fewest floods"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.safeFg,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "Nakabatay lang sa mga naiulat na baha. Ang kalsadang walang ulat ay hindi ibig sabihing ligtas.",
                        "Ranked only by reported floods. A road with no report is not necessarily safe.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun Failure(text: String, onOpenInMaps: () -> Unit) {
    Note(text)
    Text(
        tr("Buksan sa Maps", "Open in Maps"),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onOpenInMaps).padding(vertical = 8.dp),
    )
}

@Composable
private fun floodSummary(o: RouteOption): String {
    if (o.floodCount == 0) return tr("Walang naiulat na baha sa ruta", "No reported floods on this route")
    val parts = buildList {
        if (o.s3 > 0) add(tr("${o.s3} hindi madaanan", "${o.s3} impassable"))
        if (o.s2 > 0) add(tr("${o.s2} hindi madaanan ng sasakyan", "${o.s2} impassable for cars"))
        if (o.sx > 0) add(tr("${o.sx} magkasalungat", "${o.sx} conflicting"))
    }
    return parts.joinToString(" · ")
}
