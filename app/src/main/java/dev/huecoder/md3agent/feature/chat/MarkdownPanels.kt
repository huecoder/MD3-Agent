package dev.huecoder.md3agent.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check_circle
import com.composables.icons.materialsymbols.rounded.Edit_note
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.composables.icons.materialsymbols.rounded.Feedback
import com.composables.icons.materialsymbols.rounded.Format_quote
import com.composables.icons.materialsymbols.rounded.Help
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Lightbulb
import com.composables.icons.materialsymbols.rounded.Report
import com.composables.icons.materialsymbols.rounded.Science
import com.composables.icons.materialsymbols.rounded.Summarize
import com.composables.icons.materialsymbols.rounded.Warning
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.model.parseMarkdown
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.ui.haptics.rememberHaptics
import dev.huecoder.md3agent.ui.markdown.CalloutFold
import dev.huecoder.md3agent.ui.markdown.CalloutKind
import dev.huecoder.md3agent.ui.markdown.Footnote
import dev.huecoder.md3agent.ui.markdown.MarkdownBlock
import dev.huecoder.md3agent.ui.theme.LayoutMotion
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes

internal val MARKDOWN_BLOCK_CORNER = 18.dp

private val BlockShape = RoundedCornerShape(MARKDOWN_BLOCK_CORNER)

@Composable
internal fun QuoteBlock(model: MarkdownComponentModel) {
    val colors = MaterialTheme.colorScheme
    val components = LocalMarkdownComponents.current
    val bar = colors.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(colors.primaryContainer.copy(alpha = QUOTE_FILL_ALPHA))
            .drawBehind {
                val inset = QUOTE_BAR_INSET.toPx()
                val width = QUOTE_BAR_WIDTH.toPx()
                drawRoundRect(bar, Offset(inset, inset), Size(width, size.height - inset * 2), CornerRadius(width / 2))
            }
            .padding(start = QUOTE_START_PADDING, end = QUOTE_END_PADDING, top = QUOTE_VERTICAL_PADDING, bottom = QUOTE_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(QUOTE_CHILD_GAP),
    ) {
        model.node.children.forEach { child ->
            key(child.startOffset) {
                when (child.type) {
                    MarkdownElementTypes.BLOCK_QUOTE -> QuoteBlock(MarkdownComponentModel(model.content, child, model.typography))
                    MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.BLOCK_QUOTE -> Unit
                    else -> MarkdownElement(child, components, model.content, includeSpacer = false)
                }
            }
        }
    }
}

@Composable
internal fun RoundedRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = RULE_VERTICAL_PADDING)
            .height(RULE_THICKNESS)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
fun CalloutView(block: MarkdownBlock.Callout, modifier: Modifier = Modifier, body: @Composable (List<MarkdownBlock>) -> Unit) {
    val kind = block.kind
    val palette = calloutPalette(kind)
    val hasBody = block.body.isNotEmpty()
    FoldPanel(
        foldable = block.fold != CalloutFold.NONE && hasBody,
        initiallyOpen = block.fold != CalloutFold.CLOSED,
        container = palette.container,
        chevron = palette.accent,
        modifier = modifier,
        header = {
            Icon(calloutIcon(kind), contentDescription = null, tint = palette.accent, modifier = Modifier.size(CALLOUT_ICON_SIZE))
            Text(
                block.title ?: calloutTitle(block),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = palette.accent,
                modifier = Modifier.weight(1f),
            )
        },
        showBody = hasBody,
        body = { body(block.body) },
    )
}

@Composable
fun DetailsView(block: MarkdownBlock.Details, modifier: Modifier = Modifier, body: @Composable (List<MarkdownBlock>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FoldPanel(
        foldable = block.body.isNotEmpty(),
        initiallyOpen = block.isOpen,
        container = colors.surfaceContainerHigh.copy(alpha = DETAILS_FILL_ALPHA),
        chevron = colors.onSurfaceVariant,
        outline = colors.outlineVariant.copy(alpha = DETAILS_OUTLINE_ALPHA),
        modifier = modifier,
        header = {
            Text(
                block.summary ?: stringResource(R.string.details_default_summary),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
        },
        showBody = block.body.isNotEmpty(),
        body = { body(block.body) },
    )
}

@Composable
private fun FoldPanel(
    foldable: Boolean,
    initiallyOpen: Boolean,
    container: Color,
    chevron: Color,
    modifier: Modifier,
    header: @Composable RowScope.() -> Unit,
    showBody: Boolean,
    body: @Composable () -> Unit,
    outline: Color = Color.Unspecified,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "fold-chevron")
    val border = if (outline == Color.Unspecified) Modifier else Modifier.border(PANEL_OUTLINE_WIDTH, outline, BlockShape)
    Column(modifier.fillMaxWidth().background(container, BlockShape).then(border).clip(BlockShape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (foldable) {
                        Modifier.clickable {
                            haptics.tick()
                            open = !open
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = PANEL_HORIZONTAL_PADDING, vertical = PANEL_HEADER_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(PANEL_HEADER_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            header()
            if (foldable) {
                Icon(
                    MaterialSymbols.Rounded.Expand_more,
                    contentDescription = stringResource(if (open) R.string.common_collapse else R.string.common_expand),
                    tint = chevron,
                    modifier = Modifier.rotate(turn),
                )
            }
        }
        if (showBody) {
            AnimatedVisibility(
                visible = open || !foldable,
                enter = expandVertically(LayoutMotion.settle()) + fadeIn(),
                exit = shrinkVertically(LayoutMotion.settle()) + fadeOut(),
            ) {
                Box(Modifier.padding(start = PANEL_HORIZONTAL_PADDING, end = PANEL_HORIZONTAL_PADDING, bottom = PANEL_BODY_BOTTOM_PADDING)) { body() }
            }
        }
    }
}

@Composable
fun FootnotesView(reply: ReplyMarkdown, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FOOTNOTE_GAP)) {
        RoundedRule()
        reply.footnotes.forEach { note ->
            key(note.number) { FootnoteRow(note, reply) }
        }
    }
}

@Composable
private fun FootnoteRow(note: Footnote, reply: ReplyMarkdown) {
    val parsed = remember(note.markdown) { parseMarkdown(note.markdown) }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().bringIntoViewRequester(reply.targets.requester(note.number)),
        horizontalArrangement = Arrangement.spacedBy(FOOTNOTE_NUMBER_GAP),
    ) {
        Box(
            Modifier.size(FOOTNOTE_BADGE_SIZE).clip(CircleShape).background(colors.primary.copy(alpha = FOOTNOTE_BADGE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(note.number.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        MarkdownContent(parsed, PlainAnnotator, reply, Modifier.weight(1f), compact = true)
    }
}

private class CalloutPalette(val accent: Color, container: Color) {
    val container: Color = container.copy(alpha = CALLOUT_FILL_ALPHA)
}

@Composable
private fun calloutPalette(kind: CalloutKind?): CalloutPalette {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        CalloutKind.TIP, CalloutKind.SUCCESS, CalloutKind.WARNING -> CalloutPalette(colors.tertiary, colors.tertiaryContainer)
        CalloutKind.QUESTION, CalloutKind.EXAMPLE, CalloutKind.QUOTE -> CalloutPalette(colors.secondary, colors.secondaryContainer)
        CalloutKind.CAUTION -> CalloutPalette(colors.error, colors.errorContainer)
        else -> CalloutPalette(colors.primary, colors.primaryContainer)
    }
}

private fun calloutIcon(kind: CalloutKind?): ImageVector = when (kind) {
    CalloutKind.INFO -> MaterialSymbols.Rounded.Info
    CalloutKind.TIP -> MaterialSymbols.Rounded.Lightbulb
    CalloutKind.IMPORTANT -> MaterialSymbols.Rounded.Feedback
    CalloutKind.WARNING -> MaterialSymbols.Rounded.Warning
    CalloutKind.CAUTION -> MaterialSymbols.Rounded.Report
    CalloutKind.SUCCESS -> MaterialSymbols.Rounded.Check_circle
    CalloutKind.QUESTION -> MaterialSymbols.Rounded.Help
    CalloutKind.EXAMPLE -> MaterialSymbols.Rounded.Science
    CalloutKind.QUOTE -> MaterialSymbols.Rounded.Format_quote
    CalloutKind.SUMMARY -> MaterialSymbols.Rounded.Summarize
    CalloutKind.NOTE, null -> MaterialSymbols.Rounded.Edit_note
}

@Composable
private fun calloutTitle(block: MarkdownBlock.Callout): String = when (block.kind) {
    CalloutKind.NOTE -> stringResource(R.string.callout_note)
    CalloutKind.INFO -> stringResource(R.string.callout_info)
    CalloutKind.TIP -> stringResource(R.string.callout_tip)
    CalloutKind.IMPORTANT -> stringResource(R.string.callout_important)
    CalloutKind.WARNING -> stringResource(R.string.callout_warning)
    CalloutKind.CAUTION -> stringResource(R.string.callout_caution)
    CalloutKind.SUCCESS -> stringResource(R.string.callout_success)
    CalloutKind.QUESTION -> stringResource(R.string.callout_question)
    CalloutKind.EXAMPLE -> stringResource(R.string.callout_example)
    CalloutKind.QUOTE -> stringResource(R.string.callout_quote)
    CalloutKind.SUMMARY -> stringResource(R.string.callout_summary)
    null -> block.type.replaceFirstChar(Char::titlecase)
}

private const val QUOTE_FILL_ALPHA = 0.4f
private const val CALLOUT_FILL_ALPHA = 0.55f
private const val DETAILS_FILL_ALPHA = 0.6f
private const val DETAILS_OUTLINE_ALPHA = 0.7f
private const val FOOTNOTE_BADGE_ALPHA = 0.18f
private const val HALF_TURN = 180f
private val QUOTE_BAR_INSET = 8.dp
private val QUOTE_BAR_WIDTH = 4.dp
private val QUOTE_START_PADDING = 20.dp
private val QUOTE_END_PADDING = 14.dp
private val QUOTE_VERTICAL_PADDING = 10.dp
private val QUOTE_CHILD_GAP = 6.dp
private val RULE_VERTICAL_PADDING = 6.dp
private val RULE_THICKNESS = 2.dp
private val PANEL_HORIZONTAL_PADDING = 14.dp
private val PANEL_HEADER_VERTICAL_PADDING = 12.dp
private val PANEL_HEADER_GAP = 10.dp
private val PANEL_BODY_BOTTOM_PADDING = 12.dp
private val PANEL_OUTLINE_WIDTH = 1.dp
private val CALLOUT_ICON_SIZE = 22.dp
private val FOOTNOTE_GAP = 8.dp
private val FOOTNOTE_NUMBER_GAP = 8.dp
private val FOOTNOTE_BADGE_SIZE = 22.dp
