package com.shii.dev.cambix.camera

class EncodedSample(val data: ByteArray, val ptsUs: Long, val isKey: Boolean)

class RawClip(val id: Int, val samples: List<EncodedSample>, val anchorUs: Long)

/**
 * Keeps the last [keepUs] of encoded H.264 samples. A clip request copies everything from the
 * keyframe at or before (now - pre) and then keeps collecting until [post] microseconds have passed.
 * Samples are never decoded here, so the ring costs a few MB at most.
 */
class ClipRing(private val keepUs: Long) {

    private class Collector(
        val id: Int,
        val anchorUs: Long,
        val endUs: Long,
        val head: List<EncodedSample>,
    ) {
        val tail = ArrayList<EncodedSample>()
    }

    private val lock = Any()
    private val samples = ArrayDeque<EncodedSample>()
    private val collectors = ArrayList<Collector>()

    /** Returns the clips that finished with this sample. */
    fun add(sample: EncodedSample): List<RawClip> {
        val done = ArrayList<RawClip>()
        synchronized(lock) {
            samples.addLast(sample)
            while (samples.size > 1 && samples.first().ptsUs < sample.ptsUs - keepUs) {
                samples.removeFirst()
            }
            val iterator = collectors.iterator()
            while (iterator.hasNext()) {
                val c = iterator.next()
                c.tail.add(sample)
                if (sample.ptsUs >= c.endUs) {
                    iterator.remove()
                    done.add(RawClip(c.id, c.head + c.tail, c.anchorUs))
                }
            }
        }
        return done
    }

    /** False when the ring holds no usable keyframe yet. */
    fun begin(id: Int, preUs: Long, postUs: Long): Boolean {
        synchronized(lock) {
            val anchor = samples.lastOrNull()?.ptsUs ?: return false
            val target = anchor - preUs
            var start = -1
            for (i in samples.size - 1 downTo 0) {
                val s = samples[i]
                if (s.isKey && s.ptsUs <= target) {
                    start = i
                    break
                }
            }
            if (start < 0) start = samples.indexOfFirst { it.isKey }
            if (start < 0) return false
            val head = samples.subList(start, samples.size).toList()
            collectors.add(Collector(id, anchor, anchor + postUs, head))
            return true
        }
    }

    fun clear() {
        synchronized(lock) {
            samples.clear()
            collectors.clear()
        }
    }
}
