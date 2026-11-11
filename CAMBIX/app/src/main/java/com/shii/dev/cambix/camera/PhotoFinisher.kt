package com.shii.dev.cambix.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.shii.dev.cambix.grade.ColorCube
import com.shii.dev.cambix.grade.Grader
import com.shii.dev.cambix.grade.Look
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Bakes a Look into a freshly captured JPEG. ImageDecoder applies the EXIF rotation, so the result is upright. */
object PhotoFinisher {
    fun apply(jpeg: ByteArray, look: Look, cube: ColorCube?): ByteArray {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(jpeg))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val graded = Grader.render(pixels, w, h, look, cube)
            bitmap.setPixels(graded, 0, w, 0, 0, w, h)
            val out = ByteArrayOutputStream(jpeg.size)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
