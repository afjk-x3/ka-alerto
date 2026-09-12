package com.macci.kaalerto.sos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macci.kaalerto.detail.reportedAtLabel
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.displayFormOf
import com.macci.kaalerto.location.fetchCurrentLocation
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * This build's SOS: **a local-only rescue screen that sends nothing, and says so.**
 *
 * The full SOS (feat/event-sourced-roles, build days 8–9) relays a request over the mesh
 * and waits for an acknowledgement. This build has no mesh, no SMS and no server, so no
 * version of it can deliver a request to anyone — and an SOS button that *looks* like it
 * called for help while reaching no one is worse than no button: a person who believes
 * help is coming stops trying everything else. So every control here is something that
 * works with no network at all, and the black strip under the header says plainly that
 * nothing is sent:
 *
 *  - **Tumawag sa 911** — opens the dialer (ACTION_DIAL, no permission needed). Works
 *    wherever there is cell signal, even when data is dead.
 *  - **The rescue card** — design/artboards/RescueCard.dc.html: white at full brightness
 *    with the screen kept on, coordinates as the largest text in the app, the person's
 *    name and home barangay, how many are with them, and a QR carrying the same thing as
 *    plain text (see [rescueCardPayload]) for a rescuer to scan or photograph.
 *  - **Patunugin / Kumurap** — the Morse SOS tone ([SosAlarm]) and a screen strobe, both
 *    started only by hand (§6.4.2: "a family hiding from a hazard may need silence").
 *
 * Never gated on registration: someone installing mid-flood reaches this from the first
 * screen they see. Before registration the card simply has no name on it.
 */
@Composable
fun SosScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarm = remember { SosAlarm() }
    var sounding by remember { mutableStateOf(false) }
    var strobing by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<PeopleCount?>(null) }
    var locating by remember { mutableStateOf(true) }
    var location by remember { mutableStateOf<Location?>(null) }
    val createdAtMs = remember { System.currentTimeMillis() }
    val name = remember {
        if (LocalIdentity.isRegistered(context)) {
            displayFormOf(LocalIdentity.registeredFirstName(context), LocalIdentity.registeredLastName(context))
        } else {
            null
        }
    }
    val barangay = remember { LocalIdentity.homeBarangay(context).takeIf { it.isNotBlank() } }

    // Bounded at 6 s plus a recent last-known fix (location/LocationFetcher.kt), so the
    // card never waits on a phone that cannot get a lock.
    LaunchedEffect(Unit) {
        location = fetchCurrentLocation(context)
        locating = false
    }

    // "Readable through a window" is a brightness claim as much as a contrast one, so the
    // card takes the screen to full brightness and keeps it on — and gives both back on
    // the way out, since nothing else in the app has any business running there.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.setBrightness(1f)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (previous != null) window.setBrightness(previous)
            alarm.stop()
        }
    }

    val info = RescueCardInfo(
        name = name,
        homeBarangay = barangay,
        lat = location?.latitude,
        lon = location?.longitude,
        accuracyMeters = location?.accuracy,
        people = people,
        createdAtMs = createdAtMs,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SosColors.CardBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SosColors.Critical)
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
            ) {
                Text("KAILANGAN NG SAGIP", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
                // The one English line kept on purpose: this card is read by whoever finds
                // the phone, who may not be the resident.
                Text("RESCUE NEEDED · show this screen", fontSize = 15.sp, color = SosColors.CriticalText)
            }

            Text(
                "Walang ipinapadala ang screen na ito — walang makakatanggap nito. " +
                    "Tumawag sa 911, o ipakita ito sa rescuer.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SosColors.CardBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SosColors.CardInk)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )

            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(SosColors.Critical)
                    .clickable { dial911(context) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = SosColors.CardBackground)
                Spacer(Modifier.size(10.dp))
                Text("Tumawag sa 911", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SosColors.CardBackground)
            }

            Section {
                CardLabel("LOKASYON")
                val fix = location
                when {
                    locating -> CardValue("Kinukuha ang lokasyon…")
                    fix == null -> CardValue("Hindi makuha ang lokasyon. Ilarawan ang lugar sa rescuer.")
                    else -> {
                        Coordinate(coord(fix.latitude))
                        Coordinate(coord(fix.longitude))
                        Text(
                            "±${fix.accuracy.roundToInt()} m",
                            fontSize = 15.sp,
                            color = SosColors.CardMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Divider()

            Section {
                CardLabel("SINO")
                Text(
                    name ?: "Hindi pa naka-register",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (name == null) SosColors.CardMuted else SosColors.CardInk,
                )
                barangay?.let { Text("taga-$it", fontSize = 16.sp, color = SosColors.CardInk) }
            }
            Divider()

            Section {
                CardLabel("ILAN KAYO?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    PeopleCount.entries.forEach { option ->
                        val selected = people == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .background(if (selected) SosColors.CardInk else SosColors.CardBackground)
                                .border(2.dp, SosColors.CardInk)
                                .clickable { people = if (selected) null else option },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) SosColors.CardBackground else SosColors.CardInk,
                            )
                        }
                    }
                }
            }
            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QrCode(content = rescueCardPayload(info), modifier = Modifier.size(150.dp))
                Spacer(Modifier.size(16.dp))
                Column {
                    Text("I-scan o kunan ng litrato", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SosColors.CardInk)
                    Text(
                        "Lalabas ang lokasyon, pangalan at oras — kahit walang internet.",
                        fontSize = 14.sp,
                        color = SosColors.CardMuted,
                    )
                }
            }

            Text(
                "Ginawa: ${reportedAtLabel(createdAtMs)}",
                fontSize = 15.sp,
                color = SosColors.CardMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )

            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardButton(
                    label = if (sounding) "Itigil ang tunog" else "Patunugin",
                    filled = true,
                    background = if (sounding) SosColors.Critical else SosColors.CardInk,
                    modifier = Modifier.weight(1f),
                ) {
                    if (sounding) {
                        alarm.stop()
                        sounding = false
                    } else {
                        sounding = true
                        alarm.start(scope) { sounding = false }
                    }
                }
                CardButton(label = "Kumurap", filled = true, background = SosColors.CardInk, modifier = Modifier.weight(1f)) {
                    strobing = true
                }
            }
            CardButton(
                label = "Bumalik",
                filled = false,
                background = SosColors.CardBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                onClick = onBack,
            )
        }

        if (strobing) StrobeOverlay(onStop = { strobing = false })
    }
}

private fun dial911(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))) }
        .onFailure { Toast.makeText(context, "Walang phone app sa device na ito.", Toast.LENGTH_LONG).show() }
}

/**
 * A full-screen white/black strobe to be seen at night. Each phase is 500 ms — two
 * flashes a second, under the three-per-second threshold for photosensitive seizures.
 * Tapping anywhere stops it, which is also the only way to read the card again.
 */
@Composable
private fun StrobeOverlay(onStop: () -> Unit) {
    var light by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            light = !light
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (light) Color.White else Color.Black)
            .clickable(onClick = onStop),
        contentAlignment = Alignment.Center,
    ) {
        Text("Pindutin para itigil", fontSize = 16.sp, color = if (light) Color.Black else Color.White)
    }
}

/**
 * `window.attributes` hands back the live LayoutParams, so assigning a mutated copy is
 * what makes the change an actual change.
 */
private fun Window.setBrightness(value: Float) {
    attributes = WindowManager.LayoutParams().apply {
        copyFrom(attributes)
        screenBrightness = value
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(SosColors.CardInk))
}

@Composable
private fun CardLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = SosColors.CardMuted)
}

@Composable
private fun CardValue(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SosColors.CardInk)
}

/** 34sp monospace — the largest text in the product, because it has to survive distance. */
@Composable
private fun Coordinate(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = SosColors.CardInk)
}

@Composable
private fun CardButton(
    label: String,
    filled: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(background)
            .then(if (filled) Modifier else Modifier.border(2.dp, SosColors.CardInk))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) SosColors.CardBackground else SosColors.CardInk,
        )
    }
}

/**
 * Draws the module grid straight onto a Canvas, module size floored to a whole pixel so
 * every module lands on an exact pixel boundary, with the spec's 4-module quiet zone —
 * many decoders will not attempt a code without it. Ported from feat/event-sourced-roles.
 */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { encodeQr(content) }
    Canvas(modifier = modifier.background(SosColors.CardBackground)) {
        val quietZone = 4
        val total = matrix.size + quietZone * 2
        val module = kotlin.math.floor(minOf(size.width, size.height) / total)
        if (module < 1f) return@Canvas
        val drawn = module * total
        val originX = (size.width - drawn) / 2f + module * quietZone
        val originY = (size.height - drawn) / 2f + module * quietZone
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                drawRect(
                    color = SosColors.CardInk,
                    topLeft = Offset(originX + x * module, originY + y * module),
                    size = Size(module, module),
                )
            }
        }
    }
}
