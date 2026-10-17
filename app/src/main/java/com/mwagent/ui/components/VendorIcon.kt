package com.mwagent.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.toShape
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Network_intelligence
import com.mwagent.R
import com.mwagent.llm.models.ModelFamily
import com.mwagent.llm.models.ModelVendor

@get:DrawableRes
val ModelVendor.iconRes: Int
    get() = when (this) {
        ModelVendor.ANTHROPIC -> R.drawable.ic_vendor_anthropic
        ModelVendor.OPENAI -> R.drawable.ic_vendor_openai
        ModelVendor.GOOGLE -> R.drawable.ic_vendor_gemini
        ModelVendor.META -> R.drawable.ic_vendor_meta
        ModelVendor.MISTRAL -> R.drawable.ic_vendor_mistral
        ModelVendor.DEEPSEEK -> R.drawable.ic_vendor_deepseek
        ModelVendor.ALIBABA -> R.drawable.ic_vendor_qwen
        ModelVendor.MOONSHOT -> R.drawable.ic_vendor_moonshot
        ModelVendor.ZAI -> R.drawable.ic_vendor_zai
        ModelVendor.XAI -> R.drawable.ic_vendor_xai
        ModelVendor.PERPLEXITY -> R.drawable.ic_vendor_perplexity
        ModelVendor.MINIMAX -> R.drawable.ic_vendor_minimax
        ModelVendor.BYTEDANCE -> R.drawable.ic_vendor_bytedance
        ModelVendor.XIAOMI -> R.drawable.ic_vendor_xiaomi
        ModelVendor.STEPFUN -> R.drawable.ic_vendor_stepfun
        ModelVendor.NVIDIA -> R.drawable.ic_vendor_nvidia
        ModelVendor.COHERE -> R.drawable.ic_vendor_cohere
        ModelVendor.MICROSOFT -> R.drawable.ic_vendor_microsoft
    }

@get:DrawableRes
val ModelFamily.iconRes: Int
    get() = when (this) {
        ModelFamily.GEMMA -> R.drawable.ic_vendor_gemma
        else -> vendor.iconRes
    }

@Composable
fun VendorGlyph(family: ModelFamily?, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    if (family == null) {
        Icon(MaterialSymbols.Rounded.Network_intelligence, contentDescription = null, tint = tint, modifier = modifier.size(size))
    } else {
        Icon(painterResource(family.iconRes), contentDescription = family.vendor.displayName, tint = tint, modifier = modifier.size(size))
    }
}

@Composable
fun VendorBadge(
    family: ModelFamily?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: RoundedPolygon = BadgePolygon,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(shape = shape.toShape(), color = containerColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            VendorGlyph(family, size = size * GLYPH_FRACTION, tint = contentColor)
        }
    }
}

private const val GLYPH_FRACTION = 0.5f
