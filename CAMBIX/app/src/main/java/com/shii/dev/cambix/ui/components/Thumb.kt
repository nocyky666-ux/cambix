package com.shii.dev.cambix.ui.components

import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shii.dev.cambix.ui.theme.Cx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<String, ImageBitmap>(80)

/** Square thumbnail from MediaStore; shows a flat placeholder while loading or when there is none. */
@Composable
fun MediaThumb(uri: Uri?, modifier: Modifier = Modifier, sizePx: Int = 320) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        if (uri == null) {
            value = null
            return@produceState
        }
        val key = "$uri@$sizePx"
        val cached = thumbCache.get(key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(sizePx, sizePx), null).asImageBitmap()
                thumbCache.put(key, bmp)
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier.background(Cx.Graphite), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            CxIconView(CxIcon.Photos, tint = Cx.OnGlassDim, size = 24.dp)
        }
    }
}
