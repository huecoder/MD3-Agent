package dev.huecoder.md3agent.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.huecoder.md3agent.ui.navigation.SlideBackHandler
import dev.huecoder.md3agent.ui.navigation.SlideEdge
import dev.huecoder.md3agent.ui.navigation.rememberSlideBack
import dev.huecoder.md3agent.ui.navigation.slideBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    val back = rememberSlideBack()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.slideBack(back, SlideEdge.BOTTOM),
        sheetState = sheetState,
        containerColor = containerColor,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        SlideBackHandler(enabled = sheetState.isVisible, back = back) {
            sheetState.hide()
            onDismissRequest()
        }
        content()
    }
}
