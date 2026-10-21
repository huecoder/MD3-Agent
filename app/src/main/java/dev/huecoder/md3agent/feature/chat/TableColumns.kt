package dev.huecoder.md3agent.feature.chat

object TableColumns {
    fun widths(minimums: List<Int>, preferred: List<Int>, available: Int): List<Int> {
        if (preferred.isEmpty() || available <= 0) return preferred
        val wanted = preferred.sum()
        val narrowest = minimums.sum()
        return when {
            wanted <= available -> distribute(preferred, preferred, available - wanted)
            narrowest <= available -> distribute(minimums, preferred.zip(minimums) { most, least -> most - least }, available - narrowest)
            else -> preferred
        }
    }

    private fun distribute(base: List<Int>, weights: List<Int>, spare: Int): List<Int> {
        val total = weights.sum()
        if (total == 0) return base
        val shares = weights.map { (spare.toLong() * it / total).toInt() }
        val leftover = spare - shares.sum()
        return base.mapIndexed { index, width -> width + shares[index] + if (index == base.lastIndex) leftover else 0 }
    }
}
