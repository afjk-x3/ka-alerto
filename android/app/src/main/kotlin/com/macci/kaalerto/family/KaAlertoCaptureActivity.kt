package com.macci.kaalerto.family

import android.os.Bundle
import android.widget.ImageButton
import com.journeyapps.barcodescanner.CaptureActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.tr

/**
 * The only reason this subclass exists: zxing-android-embedded's stock [CaptureActivity]
 * has no visible way to leave it — only the system back gesture. `res/layout/zxing_capture.xml`
 * (an app-module override of the library's own layout of the same name) adds a back
 * button at `R.id.kaalerto_scanner_back_button`, an id this app module declares itself
 * (this module builds with `android.nonTransitiveRClass=true`, so a library's own ids —
 * even ones it reserves but never places, like `zxing_back_button` — are not visible from
 * this app's generated `R` class). This class's only job is wiring that view's click to
 * [finish], which `family/QrScannerScreen.kt`'s `ScanContract` caller reads as a cancel —
 * exactly what the system back press already does, just also reachable by touch.
 *
 * Registered in AndroidManifest.xml under this class's own name (not an override of the
 * library's activity entry), and launched instead of the stock class via
 * `ScanOptions.setCaptureActivity` in `QrScannerScreen.kt`.
 */
class KaAlertoCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<ImageButton>(R.id.kaalerto_scanner_back_button)?.apply {
            contentDescription = tr(LanguagePrefs.get(this@KaAlertoCaptureActivity), "Bumalik", "Back")
            setOnClickListener { finish() }
        }
    }
}
