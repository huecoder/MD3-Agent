package com.mwagent.ui.text

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = resolve(LocalResources.current)

fun UiText.resolve(context: Context): String = resolve(context.resources)

private fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Resource -> resources.getString(id, *resolvedArgs(args, resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolvedArgs(args, resources))
    is UiText.Raw -> value
}

private fun resolvedArgs(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
