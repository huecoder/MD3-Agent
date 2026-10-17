package dev.huecoder.md3agent.assistant.glow

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlowShader {
    private val shader = RuntimeShader(GLOW_SOURCE)

    val brush = ShaderBrush(shader)

    fun configure(look: GlowLook, stops: List<Color>, size: Size, pixelsPerDp: Float, cornerRadiusDp: Float) {
        shader.setFloatUniform("uSize", size.width, size.height)
        for (index in 0 until GlowPalette.MAX_STOPS) {
            val stop = stops[index.coerceAtMost(stops.lastIndex)]
            shader.setFloatUniform("uColor$index", stop.red, stop.green, stop.blue)
        }
        shader.setFloatUniform("uCount", stops.size.toFloat())
        shader.setFloatUniform("uDp", pixelsPerDp)
        shader.setFloatUniform("uRadius", cornerRadiusDp)
        shader.setFloatUniform("uFlow", look.flow)
        shader.setFloatUniform("uSpread", look.spread)
        shader.setFloatUniform("uBlobs", look.blobs)
        shader.setFloatUniform("uBlobAmt", look.blobAmount)
        shader.setFloatUniform("uSat", look.saturation)
        shader.setFloatUniform("uDots", look.dots)
        shader.setFloatUniform("uThick", look.thickness)
        shader.setFloatUniform("uSpan", SWEEP_SPAN)
    }

    fun advance(seconds: Float, envelope: Float, band: Float? = null) {
        shader.setFloatUniform("uTime", seconds)
        shader.setFloatUniform("uEnv", envelope)
        shader.setFloatUniform("uSweep", if (band == null) 0f else 1f)
        shader.setFloatUniform("uBand", band ?: 0f)
    }

    private companion object {
        const val SWEEP_SPAN = 0.3f

        val GLOW_SOURCE = """
            uniform float2 uSize;
            uniform float3 uColor0;
            uniform float3 uColor1;
            uniform float3 uColor2;
            uniform float3 uColor3;
            uniform float3 uColor4;
            uniform float uCount;
            uniform float uDp;
            uniform float uTime;
            uniform float uEnv;
            uniform float uRadius;
            uniform float uFlow;
            uniform float uSpread;
            uniform float uBlobs;
            uniform float uBlobAmt;
            uniform float uSat;
            uniform float uDots;
            uniform float uThick;
            uniform float uSweep;
            uniform float uBand;
            uniform float uSpan;

            const float TAU = 6.2831853;
            const float CORE_WIDTH = 1.8;
            const float MID_WIDTH = 12.0;
            const float HALO_WIDTH = 52.0;
            const float HALO_GAIN = 0.5;
            const float BOTTOM_BIAS = 0.6;
            const float WARP = 0.04;
            const float BLOB_SHARPNESS = 2.0;
            const float ADDITIVE_SHARE = 0.3;
            const float DIM = 0.3;
            const float SWEEP_GAIN = 1.3;
            const float DOT_PITCH = 5.4;
            const float DOT_COS = 0.88295;
            const float DOT_SIN = 0.46947;
            const float DOT_SIZE = 0.27;
            const float DOT_LIGHTEN = 0.3;

            float roundRectDistance(float2 p, float2 halfSize, float radius) {
              float2 q = abs(p) - halfSize + float2(radius);
              return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
            }

            float stopWeight(float index, float x) {
              if (index >= uCount) return 0.0;
              float distanceToStop = abs(x - index);
              distanceToStop = min(distanceToStop, uCount - distanceToStop);
              float weight = max(1.0 - distanceToStop, 0.0);
              return weight * weight * (3.0 - 2.0 * weight);
            }

            float3 palette(float t) {
              float x = fract(t) * uCount;
              float w0 = stopWeight(0.0, x);
              float w1 = stopWeight(1.0, x);
              float w2 = stopWeight(2.0, x);
              float w3 = stopWeight(3.0, x);
              float w4 = stopWeight(4.0, x);
              float3 sum = uColor0 * w0 + uColor1 * w1 + uColor2 * w2 + uColor3 * w3 + uColor4 * w4;
              return sum / max(w0 + w1 + w2 + w3 + w4, 0.0001);
            }

            float3 tune(float3 color) {
              float luma = dot(color, float3(0.299, 0.587, 0.114));
              return clamp(mix(float3(luma), color, uSat), 0.0, 1.0);
            }

            float dotCoverage(float2 position, float intensity) {
              float2 rotated = float2(DOT_COS * position.x + DOT_SIN * position.y, DOT_COS * position.y - DOT_SIN * position.x) / DOT_PITCH;
              float2 local = fract(rotated) - 0.5;
              float radius = DOT_SIZE * sqrt(clamp(intensity, 0.0, 1.0));
              float edge = 0.7 / (DOT_PITCH * uDp);
              return 1.0 - smoothstep(radius - edge, radius + edge, length(local));
            }

            half4 main(float2 fragCoord) {
              float2 position = fragCoord / uDp;
              float2 size = uSize / uDp;
              float2 halfSize = size * 0.5;
              float2 fromCenter = position - halfSize;

              float inward = max(-roundRectDistance(fromCenter, halfSize, uRadius), 0.0);
              float v = clamp(position.y / size.y, 0.0, 1.0);
              float low = v * v * (3.0 - 2.0 * v);
              float bias = mix(1.0 - BOTTOM_BIAS, 1.0, low);
              float widen = mix(1.0 - 0.4 * BOTTOM_BIAS, 1.0 + 0.4 * BOTTOM_BIAS, low);

              float around = atan(fromCenter.y / halfSize.y, fromCenter.x / halfSize.x) / TAU;
              around += WARP * (sin(around * TAU * 2.0 + uTime * 0.7 + inward * 0.03) + 0.6 * sin(around * TAU * 3.0 - uTime * 0.5));

              float flow = uTime * uFlow;
              float3 haloColor = tune(palette(around - flow * 0.8));
              float3 midColor = tune(palette(around - flow + uSpread));
              float3 coreColor = tune(palette(around - flow * 1.25 + uSpread * 2.0));

              float core = exp(-inward / (CORE_WIDTH * uThick));
              float mid = exp(-inward / (MID_WIDTH * uThick * widen));
              float halo = exp(-inward / (HALO_WIDTH * uThick * widen));

              float blob = pow(0.5 + 0.5 * cos((around * uBlobs - uTime * uFlow * 2.0) * TAU), BLOB_SHARPNESS);
              float shimmer = mix(1.0, 0.2 + 0.8 * blob, uBlobAmt);

              float lit = mix(1.0, exp(-((v - uBand) * (v - uBand)) / (uSpan * uSpan)), uSweep);
              float gain = mix(bias, 1.0, uSweep) * shimmer * uEnv * lit * mix(1.0, SWEEP_GAIN, uSweep);
              float3 light = (haloColor * HALO_GAIN * halo + midColor * mid + coreColor * core) * gain;

              float raw = max(light.r, max(light.g, light.b));
              float peak = 1.0 - exp(-raw * 1.2);
              float3 rgb = light * (peak / max(raw, 0.0001));
              float alpha = peak * (1.0 - ADDITIVE_SHARE);

              float veil = DIM * uEnv * lit * mix(0.4, 1.0, v) * (0.3 + 0.7 * exp(-inward / (HALO_WIDTH * uThick * 1.6)));
              alpha = alpha + veil * (1.0 - alpha);

              float3 hue = rgb / max(peak, 0.0001);
              float3 dotColor = mix(hue, float3(1.0), DOT_LIGHTEN);
              float dotAlpha = uDots * dotCoverage(position, peak) * pow(peak, 0.7) * 0.9;
              rgb = dotColor * dotAlpha + rgb * (1.0 - dotAlpha);
              alpha = dotAlpha + alpha * (1.0 - dotAlpha);

              float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
              rgb += (grain - 0.5) / 255.0 * step(0.003, alpha);

              return half4(half3(rgb), half(alpha));
            }
        """.trimIndent()
    }
}
