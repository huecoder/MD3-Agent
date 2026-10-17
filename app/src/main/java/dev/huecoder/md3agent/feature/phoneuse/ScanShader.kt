package dev.huecoder.md3agent.feature.phoneuse

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ScanShader {
    private val shader = RuntimeShader(SCAN_SOURCE)

    val brush = ShaderBrush(shader)

    fun configure(size: Size, pixelsPerDp: Float, cornerRadiusDp: Float) {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uDp", pixelsPerDp)
        shader.setFloatUniform("uRadius", cornerRadiusDp)
    }

    fun advance(progress: Float, seconds: Float) {
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uTime", seconds)
    }

    private companion object {
        val SCAN_SOURCE = """
            uniform float2 uSize;
            uniform float uDp;
            uniform float uRadius;
            uniform float uProgress;
            uniform float uTime;

            const float TAU = 6.2831853;
            const float OVERSHOOT = 0.2;
            const float LEAD_WIDTH = 3.5;
            const float HALO_WIDTH = 26.0;
            const float TRAIL_SHARE = 0.34;
            const float EDGE_WIDTH = 40.0;
            const float LINE_PITCH = 3.2;
            const float FADE_IN = 0.07;
            const float FADE_OUT_FROM = 0.84;

            float roundRectDistance(float2 p, float2 halfSize, float radius) {
              float2 q = abs(p) - halfSize + float2(radius);
              return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
            }

            float3 iridescent(float t) {
              return 0.55 + 0.45 * cos(TAU * (t + float3(0.0, 0.33, 0.67)));
            }

            half4 main(float2 fragCoord) {
              float2 position = fragCoord / uDp;
              float2 size = uSize / uDp;
              float inward = max(-roundRectDistance(position - size * 0.5, size * 0.5, uRadius), 0.0);

              float eased = uProgress * uProgress * (3.0 - 2.0 * uProgress);
              float beam = -size.y * OVERSHOOT + size.y * (1.0 + 2.0 * OVERSHOOT) * eased;
              float behind = beam - position.y;

              float lead = exp(-(behind * behind) / (LEAD_WIDTH * LEAD_WIDTH));
              float halo = exp(-(behind * behind) / (HALO_WIDTH * HALO_WIDTH));
              float trail = behind > 0.0 ? exp(-behind / (size.y * TRAIL_SHARE)) : 0.0;
              float edge = exp(-inward / EDGE_WIDTH);

              float rows = 0.4 + 0.6 * step(0.5, fract(position.y / LINE_PITCH));
              float strength = edge * (trail * rows * 1.15 + lead * 1.5 + halo * 0.45) + lead * 0.3 + halo * 0.06;
              strength *= smoothstep(0.0, FADE_IN, uProgress) * (1.0 - smoothstep(FADE_OUT_FROM, 1.0, uProgress));

              float hue = position.y / size.y * 0.9 + position.x / size.x * 0.35 + uTime * 0.4 - inward * 0.004;
              float3 color = mix(iridescent(hue), float3(1.0), 0.2 + 0.55 * lead);
              float alpha = clamp(strength, 0.0, 1.0);
              return half4(half3(color * alpha), half(alpha * 0.8));
            }
        """.trimIndent()
    }
}
