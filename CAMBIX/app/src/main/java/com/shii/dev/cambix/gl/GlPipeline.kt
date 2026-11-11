package com.shii.dev.cambix.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.shii.dev.cambix.grade.ColorCube
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.grade.ToneParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MonitorFlags(val zebra: Boolean = false, val peaking: Boolean = false, val falseColor: Boolean = false)

/**
 * Camera -> OES texture -> fragment shader -> [dest]. Nothing here touches the camera session; the
 * engine just hands [cameraSurface] to Camera2 in place of the TextureView's own surface.
 * start() returns null on any failure so the engine can fall back to the direct path.
 */
class GlPipeline(
    private val dest: SurfaceTexture,
    private val bufferWidth: Int,
    private val bufferHeight: Int,
) {
    private val thread = HandlerThread("cambix-gl").also { it.start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var camTexId = 0
    private var lutTexId = 0
    private var cameraTexture: SurfaceTexture? = null
    private var cameraSurfaceInternal: Surface? = null
    private val stMatrix = FloatArray(16)
    private val size = IntArray(1)
    private var released = false
    private var frameCount = 0

    @Volatile
    private var look: Look = Look.NEUTRAL

    @Volatile
    private var flags = MonitorFlags()

    @Volatile
    private var pendingCube: ColorCube? = null

    @Volatile
    private var cubeDirty = true

    private var lutSize = 0
    private val uniforms = HashMap<String, Int>()

    val cameraSurface: Surface? get() = cameraSurfaceInternal

    fun start(): Surface? {
        val ready = CountDownLatch(1)
        var ok = false
        handler.post {
            ok = try {
                initGl()
                true
            } catch (e: Exception) {
                teardownGl()
                false
            }
            ready.countDown()
        }
        if (!ready.await(3, TimeUnit.SECONDS) || !ok) {
            release()
            return null
        }
        return cameraSurfaceInternal
    }

    fun update(newLook: Look, cube: ColorCube?, monitor: MonitorFlags) {
        look = newLook
        flags = monitor
        if (cube !== pendingCube) {
            pendingCube = cube
            cubeDirty = true
        }
    }

    /** Blocks briefly so the EGL surface is gone before the engine reuses the same SurfaceTexture. */
    fun release() {
        if (released) return
        released = true
        val done = CountDownLatch(1)
        handler.post {
            teardownGl()
            done.countDown()
        }
        done.await(2, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL display tidak tersedia" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL gagal diinisialisasi" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "EGL config tidak cocok" }
        val config = configs[0]!!
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Konteks GL gagal dibuat" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, dest, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Surface GL gagal dibuat" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL makeCurrent gagal" }

        program = buildProgram(VERTEX, FRAGMENT)
        for (name in UNIFORM_NAMES) uniforms[name] = GLES20.glGetUniformLocation(program, name)

        val ids = IntArray(2)
        GLES20.glGenTextures(2, ids, 0)
        camTexId = ids[0]
        lutTexId = ids[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTexId)
        setupTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        setupTexture(GLES20.GL_TEXTURE_2D)

        val tex = SurfaceTexture(camTexId)
        tex.setDefaultBufferSize(bufferWidth, bufferHeight)
        tex.setOnFrameAvailableListener({ drawFrame() }, handler)
        cameraTexture = tex
        cameraSurfaceInternal = Surface(tex)
    }

    private fun setupTexture(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun uploadCube(cube: ColorCube?) {
        if (cube == null) {
            lutSize = 0
            return
        }
        // A 65-point strip would be 4225 px wide, past what some GPUs accept.
        val c = if (cube.size > 33) cube.resampled(33) else cube
        val n = c.size
        val bytes = ByteBuffer.allocateDirect(n * n * n * 4)
        var i = 0
        for (b in 0 until n) {
            for (g in 0 until n) {
                for (r in 0 until n) {
                    bytes.put(((c.data[i] * 255f + 0.5f).toInt().coerceIn(0, 255)).toByte())
                    bytes.put(((c.data[i + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)).toByte())
                    bytes.put(((c.data[i + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)).toByte())
                    bytes.put(0xFF.toByte())
                    i += 3
                }
            }
        }
        bytes.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        // Layout: x = blue * n + red, y = green.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n * n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        lutSize = n
    }

    private fun drawFrame() {
        if (released || eglSurface == EGL14.EGL_NO_SURFACE) return
        val tex = cameraTexture ?: return
        try {
            tex.updateTexImage()
            tex.getTransformMatrix(stMatrix)
        } catch (e: Exception) {
            return
        }
        if (cubeDirty) {
            cubeDirty = false
            uploadCube(pendingCube)
        }

        val w = queryDim(EGL14.EGL_WIDTH)
        val h = queryDim(EGL14.EGL_HEIGHT)
        if (w <= 0 || h <= 0) return
        GLES20.glViewport(0, 0, w, h)
        GLES20.glUseProgram(program)

        val l = look
        val tone = ToneParams(l)
        val f = flags
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTexId)
        GLES20.glUniform1i(u("uCam"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glUniform1i(u("uLut"), 1)
        GLES20.glUniformMatrix4fv(u("uSt"), 1, false, stMatrix, 0)
        GLES20.glUniform1f(u("uExpGain"), tone.exposureGain)
        GLES20.glUniform3f(u("uWb"), tone.wbR, tone.wbG, tone.wbB)
        GLES20.glUniform1f(u("uBlack"), tone.black)
        GLES20.glUniform1f(u("uWhite"), tone.white)
        GLES20.glUniform1f(u("uShadows"), tone.shadows)
        GLES20.glUniform1f(u("uHighlights"), tone.highlights)
        GLES20.glUniform1f(u("uContrast"), tone.contrast)
        GLES20.glUniform1f(u("uFade"), tone.fade)
        GLES20.glUniform1f(u("uSat"), l.saturation)
        GLES20.glUniform1f(u("uVibrance"), l.vibrance)
        GLES20.glUniform1f(u("uVignette"), l.vignette)
        GLES20.glUniform1f(u("uGrain"), l.grain)
        GLES20.glUniform1f(u("uSeed"), (frameCount++ % 97).toFloat())
        val lutOn = lutSize > 0 && l.lutId != null && l.lutIntensity > 0.001f
        GLES20.glUniform1f(u("uLutOn"), if (lutOn) l.lutIntensity.coerceIn(0f, 1f) else 0f)
        GLES20.glUniform1f(u("uLutSize"), lutSize.toFloat().coerceAtLeast(2f))
        GLES20.glUniform1f(u("uZebra"), if (f.zebra) 1f else 0f)
        GLES20.glUniform1f(u("uPeaking"), if (f.peaking) 1f else 0f)
        GLES20.glUniform1f(u("uFalseColor"), if (f.falseColor) 1f else 0f)
        GLES20.glUniform2f(u("uTexel"), 1f / bufferWidth, 1f / bufferHeight)

        val pos = GLES20.glGetAttribLocation(program, "aPos")
        val tc = GLES20.glGetAttribLocation(program, "aTex")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, QUAD_POS)
        GLES20.glEnableVertexAttribArray(tc)
        GLES20.glVertexAttribPointer(tc, 2, GLES20.GL_FLOAT, false, 0, QUAD_TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(tc)
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    private fun u(name: String): Int = uniforms[name] ?: -1

    private fun queryDim(attr: Int): Int {
        EGL14.eglQuerySurface(display, eglSurface, attr, size, 0)
        return size[0]
    }

    private fun teardownGl() {
        try {
            cameraTexture?.setOnFrameAvailableListener(null)
        } catch (_: Exception) {
        }
        cameraSurfaceInternal?.release()
        cameraSurfaceInternal = null
        cameraTexture?.release()
        cameraTexture = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (camTexId != 0) GLES20.glDeleteTextures(2, intArrayOf(camTexId, lutTexId), 0)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        program = 0
        camTexId = 0
        eglSurface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Link shader gagal: ${GLES20.glGetProgramInfoLog(p)}" }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compile(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "Compile shader gagal: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }

    companion object {
        private val UNIFORM_NAMES = listOf(
            "uCam", "uLut", "uSt", "uExpGain", "uWb", "uBlack", "uWhite", "uShadows", "uHighlights", "uContrast",
            "uFade", "uSat", "uVibrance", "uVignette", "uGrain", "uSeed", "uLutOn", "uLutSize", "uZebra",
            "uPeaking", "uFalseColor", "uTexel",
        )

        private fun floats(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also {
                it.put(v)
                it.position(0)
            }

        private val QUAD_POS = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val QUAD_TEX = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        private const val VERTEX = """
attribute vec4 aPos;
attribute vec2 aTex;
uniform mat4 uSt;
varying vec2 vTex;
varying vec2 vPos;
void main() {
  gl_Position = aPos;
  vTex = (uSt * vec4(aTex, 0.0, 1.0)).xy;
  vPos = aTex;
}
"""

        // The tone() body mirrors ToneParams.channel() in Look.kt. Keep them in step.
        private const val FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 vTex;
varying vec2 vPos;
uniform samplerExternalOES uCam;
uniform sampler2D uLut;
uniform float uExpGain;
uniform vec3 uWb;
uniform float uBlack;
uniform float uWhite;
uniform float uShadows;
uniform float uHighlights;
uniform float uContrast;
uniform float uFade;
uniform float uSat;
uniform float uVibrance;
uniform float uVignette;
uniform float uGrain;
uniform float uSeed;
uniform float uLutOn;
uniform float uLutSize;
uniform float uZebra;
uniform float uPeaking;
uniform float uFalseColor;
uniform vec2 uTexel;

float lumaOf(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

float tone(float x, float wb) {
  float lin = pow(max(x, 0.0), 2.2) * uExpGain * wb;
  float v = clamp(pow(max(lin, 0.0), 1.0 / 2.2), 0.0, 1.0);
  v = clamp((v - uBlack) / (uWhite - uBlack), 0.0, 1.0);
  v += uShadows * 0.6 * (1.0 - v) * (1.0 - v) * sqrt(v);
  v += uHighlights * 0.6 * v * v * sqrt(1.0 - v);
  v = clamp(v, 0.0, 1.0);
  if (uContrast >= 0.0) {
    v = v + (v * v * (3.0 - 2.0 * v) - v) * uContrast;
  } else {
    v = v + (0.5 + (v - 0.5) * 0.5 - v) * (-uContrast);
  }
  v = uFade * 0.14 + v * (1.0 - uFade * 0.18);
  return clamp(v, 0.0, 1.0);
}

vec3 lutSample(vec3 c) {
  float n = uLutSize;
  float b = clamp(c.b, 0.0, 1.0) * (n - 1.0);
  float b0 = floor(b);
  float b1 = min(b0 + 1.0, n - 1.0);
  float fb = b - b0;
  float rx = clamp(c.r, 0.0, 1.0) * (n - 1.0) + 0.5;
  float gy = (clamp(c.g, 0.0, 1.0) * (n - 1.0) + 0.5) / n;
  vec3 a = texture2D(uLut, vec2((b0 * n + rx) / (n * n), gy)).rgb;
  vec3 d = texture2D(uLut, vec2((b1 * n + rx) / (n * n), gy)).rgb;
  return mix(a, d, fb);
}

void main() {
  vec3 raw = texture2D(uCam, vTex).rgb;
  vec3 c = vec3(tone(raw.r, uWb.r), tone(raw.g, uWb.g), tone(raw.b, uWb.b));
  float luma = lumaOf(c);

  float mx = max(c.r, max(c.g, c.b));
  float mn = min(c.r, min(c.g, c.b));
  float sl = (mx - mn) / (mx + 0.0001);
  float vibAmt = uVibrance >= 0.0 ? uVibrance * (1.0 - sl) : uVibrance;
  float f = (1.0 + uSat) * (1.0 + vibAmt);
  c = clamp(vec3(luma) + (c - vec3(luma)) * f, 0.0, 1.0);

  if (uLutOn > 0.001) {
    c = mix(c, clamp(lutSample(c), 0.0, 1.0), uLutOn);
  }

  if (uVignette != 0.0) {
    vec2 q = (vPos - 0.5) * 2.0;
    float d = length(q) / 1.4142;
    float t = clamp((d - 0.35) / 0.65, 0.0, 1.0);
    float vv = t * t * (3.0 - 2.0 * t);
    float k = uVignette >= 0.0 ? 1.0 - uVignette * vv * 0.8 : 1.0 + (-uVignette) * vv * 0.8;
    c *= k;
  }

  if (uGrain > 0.0) {
    float n = fract(sin(dot(gl_FragCoord.xy + vec2(uSeed), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    float lm = lumaOf(c);
    c += n * uGrain * 0.12 * (1.0 - abs(2.0 * lm - 1.0) * 0.6);
  }

  c = clamp(c, 0.0, 1.0);
  float outLuma = lumaOf(c);

  if (uFalseColor > 0.5) {
    if (outLuma < 0.04) c = vec3(0.45, 0.1, 0.65);
    else if (outLuma < 0.2) c = vec3(0.1, 0.25, 0.8);
    else if (outLuma < 0.42) c = vec3(0.1, 0.55, 0.3);
    else if (outLuma < 0.58) c = vec3(0.5);
    else if (outLuma < 0.75) c = vec3(0.9, 0.55, 0.6);
    else if (outLuma < 0.93) c = vec3(0.95, 0.9, 0.3);
    else c = vec3(0.95, 0.15, 0.1);
  }

  if (uZebra > 0.5 && outLuma > 0.95) {
    if (mod(floor((gl_FragCoord.x + gl_FragCoord.y) / 8.0), 2.0) < 1.0) {
      c = mix(c, vec3(1.0, 0.2, 0.2), 0.65);
    }
  }

  if (uPeaking > 0.5) {
    float l0 = lumaOf(raw);
    float lx = lumaOf(texture2D(uCam, vTex + vec2(uTexel.x * 1.5, 0.0)).rgb);
    float ly = lumaOf(texture2D(uCam, vTex + vec2(0.0, uTexel.y * 1.5)).rgb);
    float edge = abs(l0 - lx) + abs(l0 - ly);
    if (edge > 0.14) c = mix(c, vec3(0.2, 1.0, 0.45), 0.9);
  }

  gl_FragColor = vec4(c, 1.0);
}
"""
    }
}
