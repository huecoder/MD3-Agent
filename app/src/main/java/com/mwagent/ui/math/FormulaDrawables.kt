package com.mwagent.ui.math

import android.content.Context
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

internal object FormulaDrawables {
    private const val MAX_CACHED = 96

    private data class Key(val formula: MathFormula, val sizePx: Float, val color: Int)

    private class Entry(val drawable: JLatexMathDrawable?)

    private val cache = LruCache<Key, Entry>(MAX_CACHED)
    private var initialized = false

    fun of(context: Context, formula: MathFormula, sizePx: Float, color: Int): JLatexMathDrawable? {
        val key = Key(formula, sizePx, color)
        return (cache[key] ?: Entry(build(context, formula, sizePx, color)).also { cache.put(key, it) }).drawable
    }

    private fun build(context: Context, formula: MathFormula, sizePx: Float, color: Int): JLatexMathDrawable? {
        ensureInitialized(context)
        return try {
            JLatexMathDrawable.builder(formula.latex)
                .textSize(sizePx)
                .color(color)
                .align(if (formula.isDisplay) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
                .build()
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        JLatexMathAndroid.init(context.applicationContext)
        initialized = true
    }
}
