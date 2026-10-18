package dev.huecoder.md3agent.feature.onboarding.sims

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Description
import com.composables.icons.materialsymbols.rounded.Image
import kotlinx.coroutines.delay

private val LINES = listOf("$ ls", "notes.md  plot.py  data.csv", "$ python plot.py", "saved chart.png")

@Composable
fun CodeScene(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(emptyList<String>()) }
    var typing by remember { mutableStateOf("") }
    var chart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            shown = emptyList()
            typing = ""
            chart = false
            delay(HOLD_MS)
            LINES.forEach { line ->
                if (line.startsWith("$")) {
                    typeInto(line, CHAR_MS) { typing = line.take(it) }
                    delay(LINE_MS)
                    typing = ""
                } else {
                    delay(LINE_MS)
                }
                shown = shown + line
                if (line.startsWith("saved")) chart = true
            }
            delay(HOLD_MS * 3)
        }
    }

    SceneFrame(modifier) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(FILES_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FileRow("notes.md", false)
                FileRow("plot.py", false)
                FileRow("data.csv", false)
                if (chart) FileRow("chart.png", true)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(TERMINAL).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                shown.forEach { TerminalLine(it) }
                if (typing.isNotEmpty()) TerminalLine(typing)
            }
        }
    }
}

@Composable
private fun FileRow(name: String, highlighted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (highlighted) MaterialSymbols.Rounded.Image else MaterialSymbols.Rounded.Description,
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TerminalLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = if (text.startsWith("$")) TERMINAL_PROMPT else TERMINAL_TEXT)
}

private val TERMINAL = Color(0xFF14161B)
private val TERMINAL_TEXT = Color(0xFFC8CCD6)
private val TERMINAL_PROMPT = Color(0xFF8FD6A6)
private val FILES_WIDTH = 92.dp
private const val HOLD_MS = 700L
private const val LINE_MS = 380L
private const val CHAR_MS = 55L
