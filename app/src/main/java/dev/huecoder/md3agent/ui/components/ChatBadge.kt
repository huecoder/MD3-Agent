package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Bug_report
import com.composables.icons.materialsymbols.rounded.Build
import com.composables.icons.materialsymbols.rounded.Calculate
import com.composables.icons.materialsymbols.rounded.Calendar_month
import com.composables.icons.materialsymbols.rounded.Cardiology
import com.composables.icons.materialsymbols.rounded.Celebration
import com.composables.icons.materialsymbols.rounded.Chat
import com.composables.icons.materialsymbols.rounded.Chef_hat
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Directions_car
import com.composables.icons.materialsymbols.rounded.Eco
import com.composables.icons.materialsymbols.rounded.Edit_note
import com.composables.icons.materialsymbols.rounded.Favorite
import com.composables.icons.materialsymbols.rounded.Fitness_center
import com.composables.icons.materialsymbols.rounded.Flight
import com.composables.icons.materialsymbols.rounded.Home
import com.composables.icons.materialsymbols.rounded.Lightbulb
import com.composables.icons.materialsymbols.rounded.Lock
import com.composables.icons.materialsymbols.rounded.Mail
import com.composables.icons.materialsymbols.rounded.Medication
import com.composables.icons.materialsymbols.rounded.Menu_book
import com.composables.icons.materialsymbols.rounded.Movie
import com.composables.icons.materialsymbols.rounded.Music_note
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Payments
import com.composables.icons.materialsymbols.rounded.Pets
import com.composables.icons.materialsymbols.rounded.Photo_camera
import com.composables.icons.materialsymbols.rounded.Psychology
import com.composables.icons.materialsymbols.rounded.Public
import com.composables.icons.materialsymbols.rounded.Restaurant
import com.composables.icons.materialsymbols.rounded.Rocket_launch
import com.composables.icons.materialsymbols.rounded.School
import com.composables.icons.materialsymbols.rounded.Science
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Shopping_cart
import com.composables.icons.materialsymbols.rounded.Smart_toy
import com.composables.icons.materialsymbols.rounded.Sports_esports
import com.composables.icons.materialsymbols.rounded.Terminal
import com.composables.icons.materialsymbols.rounded.Translate
import com.composables.icons.materialsymbols.rounded.Work
import dev.huecoder.md3agent.data.chat.ChatGlyph
import dev.huecoder.md3agent.data.chat.ChatLook
import dev.huecoder.md3agent.data.chat.ChatShape
import dev.huecoder.md3agent.data.chat.ChatTint

data class TintColors(val container: Color, val content: Color)

@Composable
fun ChatBadge(look: ChatLook, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = look.tint.colors()
    val shape = look.shape.polygon.toShape()
    Surface(shape = shape, color = colors.container, contentColor = colors.content, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(look.glyph.icon, contentDescription = null, modifier = Modifier.size(size * ICON_FRACTION))
        }
    }
}

@Composable
fun ChatTint.colors(): TintColors {
    if (this == ChatTint.THEME) {
        return TintColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    val (light, deep) = palette
    return if (isSystemInDarkTheme()) TintColors(deep, light) else TintColors(light, deep)
}

@Composable
fun ChatTint.boldColors(): TintColors {
    if (this == ChatTint.THEME) {
        return TintColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    }
    val (light, deep) = palette
    return if (isSystemInDarkTheme()) TintColors(light, deep) else TintColors(deep, light)
}

private val ChatTint.palette: Pair<Color, Color>
    get() = when (this) {
        ChatTint.THEME, ChatTint.BLUE -> Color(0xFFD8E2FF) to Color(0xFF2A4678)
        ChatTint.RED -> Color(0xFFFFDAD6) to Color(0xFF73332E)
        ChatTint.ORANGE -> Color(0xFFFFDBCA) to Color(0xFF723611)
        ChatTint.AMBER -> Color(0xFFFFDF9E) to Color(0xFF5B4300)
        ChatTint.GREEN -> Color(0xFFBCF0B4) to Color(0xFF235024)
        ChatTint.TEAL -> Color(0xFFA0F2EA) to Color(0xFF005049)
        ChatTint.CYAN -> Color(0xFFBEE9FF) to Color(0xFF004D66)
        ChatTint.INDIGO -> Color(0xFFE0E0FF) to Color(0xFF3B4279)
        ChatTint.PURPLE -> Color(0xFFF0DBFF) to Color(0xFF5A3D71)
        ChatTint.PINK -> Color(0xFFFFD8E8) to Color(0xFF703354)
        ChatTint.GRAY -> Color(0xFFE1E2EC) to Color(0xFF44474F)
    }

val ChatShape.polygon: RoundedPolygon
    get() = when (this) {
        ChatShape.COOKIE -> MaterialShapes.Cookie9Sided
        ChatShape.CLOVER -> MaterialShapes.Clover4Leaf
        ChatShape.FLOWER -> MaterialShapes.Flower
        ChatShape.SUNNY -> MaterialShapes.Sunny
        ChatShape.GEM -> MaterialShapes.Gem
        ChatShape.PUFFY -> MaterialShapes.Puffy
        ChatShape.BURST -> MaterialShapes.SoftBurst
        ChatShape.HEART -> MaterialShapes.Heart
        ChatShape.ARCH -> MaterialShapes.Arch
        ChatShape.PILL -> MaterialShapes.Pill
        ChatShape.CIRCLE -> MaterialShapes.Circle
        ChatShape.SQUARE -> MaterialShapes.Square
        ChatShape.PENTAGON -> MaterialShapes.Pentagon
        ChatShape.GHOST -> MaterialShapes.Ghostish
        ChatShape.BUN -> MaterialShapes.Bun
        ChatShape.FAN -> MaterialShapes.Fan
    }

val ChatGlyph.icon: ImageVector
    get() = when (this) {
        ChatGlyph.CHAT -> MaterialSymbols.Rounded.Chat
        ChatGlyph.CODE -> MaterialSymbols.Rounded.Code
        ChatGlyph.BUG -> MaterialSymbols.Rounded.Bug_report
        ChatGlyph.TERMINAL -> MaterialSymbols.Rounded.Terminal
        ChatGlyph.IDEA -> MaterialSymbols.Rounded.Lightbulb
        ChatGlyph.SCIENCE -> MaterialSymbols.Rounded.Science
        ChatGlyph.MATH -> MaterialSymbols.Rounded.Calculate
        ChatGlyph.BOOK -> MaterialSymbols.Rounded.Menu_book
        ChatGlyph.SCHOOL -> MaterialSymbols.Rounded.School
        ChatGlyph.WRITING -> MaterialSymbols.Rounded.Edit_note
        ChatGlyph.DOCUMENT -> MaterialSymbols.Rounded.Description
        ChatGlyph.TRANSLATE -> MaterialSymbols.Rounded.Translate
        ChatGlyph.MAIL -> MaterialSymbols.Rounded.Mail
        ChatGlyph.CALENDAR -> MaterialSymbols.Rounded.Calendar_month
        ChatGlyph.WORK -> MaterialSymbols.Rounded.Work
        ChatGlyph.MONEY -> MaterialSymbols.Rounded.Payments
        ChatGlyph.SHOPPING -> MaterialSymbols.Rounded.Shopping_cart
        ChatGlyph.FOOD -> MaterialSymbols.Rounded.Restaurant
        ChatGlyph.COOKING -> MaterialSymbols.Rounded.Chef_hat
        ChatGlyph.HEALTH -> MaterialSymbols.Rounded.Cardiology
        ChatGlyph.MEDICINE -> MaterialSymbols.Rounded.Medication
        ChatGlyph.FITNESS -> MaterialSymbols.Rounded.Fitness_center
        ChatGlyph.MIND -> MaterialSymbols.Rounded.Psychology
        ChatGlyph.TRAVEL -> MaterialSymbols.Rounded.Flight
        ChatGlyph.CAR -> MaterialSymbols.Rounded.Directions_car
        ChatGlyph.WORLD -> MaterialSymbols.Rounded.Public
        ChatGlyph.HOME -> MaterialSymbols.Rounded.Home
        ChatGlyph.PETS -> MaterialSymbols.Rounded.Pets
        ChatGlyph.NATURE -> MaterialSymbols.Rounded.Eco
        ChatGlyph.MUSIC -> MaterialSymbols.Rounded.Music_note
        ChatGlyph.MOVIE -> MaterialSymbols.Rounded.Movie
        ChatGlyph.GAME -> MaterialSymbols.Rounded.Sports_esports
        ChatGlyph.PHOTO -> MaterialSymbols.Rounded.Photo_camera
        ChatGlyph.ART -> MaterialSymbols.Rounded.Palette
        ChatGlyph.PARTY -> MaterialSymbols.Rounded.Celebration
        ChatGlyph.LOVE -> MaterialSymbols.Rounded.Favorite
        ChatGlyph.ROCKET -> MaterialSymbols.Rounded.Rocket_launch
        ChatGlyph.SECURITY -> MaterialSymbols.Rounded.Lock
        ChatGlyph.REPAIR -> MaterialSymbols.Rounded.Build
        ChatGlyph.SEARCH -> MaterialSymbols.Rounded.Search
        ChatGlyph.ROBOT -> MaterialSymbols.Rounded.Smart_toy
    }

private const val ICON_FRACTION = 0.5f
