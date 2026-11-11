package com.shii.dev.cambix.camera

import java.io.ByteArrayOutputStream

/**
 * Google Motion Photo v1 layout: JPEG + XMP (GCamera/Container) + the MP4 appended after the
 * JPEG end marker. Google Photos and most gallery apps read this as one Live Photo file.
 */
object MotionPhoto {

    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"

    fun buildXmp(mp4Length: Int, presentationUs: Long): String = """
        <?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
        <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="CAMBIX">
         <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
          <rdf:Description rdf:about=""
            xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
            xmlns:Container="http://ns.google.com/photos/1.0/container/"
            xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
            GCamera:MotionPhoto="1"
            GCamera:MotionPhotoVersion="1"
            GCamera:MotionPhotoPresentationTimestampUs="$presentationUs">
           <Container:Directory>
            <rdf:Seq>
             <rdf:li rdf:parseType="Resource">
              <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
             </rdf:li>
             <rdf:li rdf:parseType="Resource">
              <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$mp4Length" Item:Padding="0"/>
             </rdf:li>
            </rdf:Seq>
           </Container:Directory>
          </rdf:Description>
         </rdf:RDF>
        </x:xmpmeta>
        <?xpacket end="w"?>
    """.trimIndent()

    fun build(jpeg: ByteArray, mp4: ByteArray, presentationUs: Long): ByteArray {
        require(jpeg.size > 4 && u8(jpeg, 0) == 0xFF && u8(jpeg, 1) == 0xD8) { "Bukan file JPEG" }
        val payload = (XMP_HEADER + buildXmp(mp4.size, presentationUs)).toByteArray(Charsets.ISO_8859_1)
        val segmentLength = payload.size + 2
        require(segmentLength <= 0xFFFF) { "XMP terlalu besar" }

        val insertAt = afterLeadingAppSegments(jpeg)
        val out = ByteArrayOutputStream(jpeg.size + mp4.size + payload.size + 8)
        out.write(jpeg, 0, insertAt)
        out.write(0xFF)
        out.write(0xE1)
        out.write(segmentLength ushr 8)
        out.write(segmentLength and 0xFF)
        out.write(payload)
        out.write(jpeg, insertAt, jpeg.size - insertAt)
        out.write(mp4)
        return out.toByteArray()
    }

    /** Returns the embedded MP4, or null when the file is a plain JPEG. */
    fun extractMp4(file: ByteArray): ByteArray? {
        val headLen = minOf(file.size, 128 * 1024)
        val head = String(file, 0, headLen, Charsets.ISO_8859_1)
        if (!head.contains("MotionPhoto=\"1\"")) return null
        val length = LENGTH_AFTER_SEMANTIC.find(head)?.groupValues?.get(1)?.toIntOrNull()
            ?: LENGTH_BEFORE_SEMANTIC.find(head)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        if (length <= 8 || length >= file.size) return null
        val start = file.size - length
        val isMp4 = file[start + 4] == 'f'.code.toByte() && file[start + 5] == 't'.code.toByte() &&
            file[start + 6] == 'y'.code.toByte() && file[start + 7] == 'p'.code.toByte()
        if (!isMp4) return null
        return file.copyOfRange(start, file.size)
    }

    private val LENGTH_AFTER_SEMANTIC = Regex("Semantic=\"MotionPhoto\"[^>]*?Length=\"(\\d+)\"")
    private val LENGTH_BEFORE_SEMANTIC = Regex("Length=\"(\\d+)\"[^>]*?Semantic=\"MotionPhoto\"")

    /** Keeps EXIF/ICC segments first so strict readers still find them at the start of the file. */
    private fun afterLeadingAppSegments(jpeg: ByteArray): Int {
        var pos = 2
        while (pos + 4 <= jpeg.size && u8(jpeg, pos) == 0xFF) {
            val marker = u8(jpeg, pos + 1)
            if (marker < 0xE0 || marker > 0xEF) break
            val len = (u8(jpeg, pos + 2) shl 8) or u8(jpeg, pos + 3)
            val next = pos + 2 + len
            if (len < 2 || next > jpeg.size) break
            pos = next
        }
        return pos
    }

    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
}
