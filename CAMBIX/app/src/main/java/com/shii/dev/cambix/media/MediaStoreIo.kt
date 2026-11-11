package com.shii.dev.cambix.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {
    private const val PHOTO_DIR = "Pictures/CAMBIX"
    private const val VIDEO_DIR = "Movies/CAMBIX"

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun saveImage(context: Context, bytes: ByteArray, displayName: String, mime: String = "image/jpeg"): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PHOTO_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("MediaStore menolak entri foto")
        try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Tidak bisa membuka file tujuan")
            out.use { it.write(bytes) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** The writer streams a DNG straight into the MediaStore entry, so a 25 MB RAW is never held twice in memory. */
    fun saveRaw(context: Context, displayName: String, writer: (java.io.OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
            put(MediaStore.MediaColumns.RELATIVE_PATH, PHOTO_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("MediaStore menolak entri RAW")
        try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Tidak bisa membuka file tujuan")
            out.use { writer(it) }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun saveVideo(context: Context, source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, VIDEO_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("MediaStore menolak entri video")
        try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Tidak bisa membuka file tujuan")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }
}

class MediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val dateMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
) {
    /** CAMBIX names Live Photo files with a LIVE tag, so the grid never has to open the files. */
    val isLive: Boolean get() = !isVideo && name.contains("_LIVE_")
    val isRaw: Boolean get() = !isVideo && name.endsWith(".dng", ignoreCase = true)
    val isEdit: Boolean get() = !isVideo && name.contains("_EDIT")
}

object MediaQuery {
    fun loadAll(context: Context): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        items += query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/CAMBIX%", false)
        items += query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/CAMBIX%", true)
        return items.sortedByDescending { it.dateMs }
    }

    fun latest(context: Context): Uri? = loadAll(context).firstOrNull()?.uri

    private fun query(context: Context, collection: Uri, path: String, video: Boolean): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(path),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            while (c.moveToNext()) {
                out.add(
                    MediaItem(
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        name = c.getString(nameCol) ?: "",
                        isVideo = video,
                        dateMs = c.getLong(dateCol) * 1000L,
                        sizeBytes = c.getLong(sizeCol),
                        width = c.getInt(wCol),
                        height = c.getInt(hCol),
                    )
                )
            }
        }
        return out
    }
}
