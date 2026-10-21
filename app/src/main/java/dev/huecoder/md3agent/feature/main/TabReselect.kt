package dev.huecoder.md3agent.feature.main

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter

val LocalTabReselects = staticCompositionLocalOf<SharedFlow<MainTab>> { MutableSharedFlow() }

@Composable
fun ScrollToTopOnReselect(tab: MainTab, listState: LazyListState) {
    val reselects = LocalTabReselects.current
    LaunchedEffect(reselects, tab, listState) {
        reselects.filter { it == tab }.collect { listState.animateScrollToItem(0) }
    }
}
