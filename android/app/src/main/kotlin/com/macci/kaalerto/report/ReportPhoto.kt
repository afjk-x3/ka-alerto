package com.macci.kaalerto.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * A report's photo never travels by SMS or mesh relay (docs/03-architecture.md's rule,
 * restated in CLAUDE.md's architecture summary) — only its hash rides with the event, in
 * [ReportPhotoPayload]. The photo itself stays in this device's private storage, named
 * by that hash, so a device that already has a matching file can show it and a peer that
 * only received the event (hash, no bytes) has proof a photo exists and nothing to
 * render for it — which is the point, not a bug.
 */
object PhotoStore {
    private const val DIR = "report_photos"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun fileFor(context: Context, hash: String): File = File(dir(context), "$hash.jpg")

    fun exists(context: Context, hash: String): Boolean = fileFor(context, hash).exists()

    /** Saves a captured photo (camera intent result) and returns its content hash. */
    fun storeBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        storeBytes(context, bytes)
    }.getOrNull()

    /** Saves a picked photo (gallery Uri) and returns its content hash. */
    fun storeUri(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        storeBytes(context, bytes)
    }.getOrNull()

    private fun storeBytes(context: Context, bytes: ByteArray): String {
        val hash = sha256(bytes)
        val file = fileFor(context, hash)
        // Content-addressed, so a re-picked identical photo is a no-op write, same
        // spirit as the event table's own content-hash dedup.
        if (!file.exists()) {
            FileOutputStream(file).use { it.write(bytes) }
        }
        return hash
    }

    /** A downsampled preview — reports never need the full-resolution capture on screen. */
    fun loadThumbnail(context: Context, hash: String, maxDimension: Int = 512): Bitmap? = runCatching {
        val file = fileFor(context, hash)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Rides in [com.macci.kaalerto.data.Event.payload] — see [Event.payload]'s own doc for why a JSON field rather than a column. */
@Serializable
data class ReportPhotoPayload(@SerialName("photoHash") val photoHash: String)

private val reportPhotoJson = Json { ignoreUnknownKeys = true }

fun ReportPhotoPayload.encode(): String = reportPhotoJson.encodeToString(ReportPhotoPayload.serializer(), this)

fun decodeReportPhotoPayload(raw: String?): ReportPhotoPayload? =
    raw?.let { runCatching { reportPhotoJson.decodeFromString(ReportPhotoPayload.serializer(), it) }.getOrNull() }
