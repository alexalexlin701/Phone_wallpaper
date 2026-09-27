package com.example.videowallpaper.random

import kotlin.random.Random

/** Pure selection policy. Persist last/remaining only after a successful wallpaper write. */
object RotationOrder {
    enum class Mode { SHUFFLE, RANDOM }
    data class Choice(val uri: String, val remaining: List<String>)

    fun next(pool: List<String>, last: String?, remaining: List<String>, mode: Mode,
        random: Random = Random.Default): Choice? {
        val unique = pool.distinct()
        if (unique.isEmpty()) return null
        if (unique.size == 1) return Choice(unique.first(), emptyList())
        if (mode == Mode.RANDOM) {
            return Choice(unique.filter { it != last }.random(random), emptyList())
        }
        var bag = remaining.distinct().filter { it in unique && it != last }
        if (bag.isEmpty()) {
            bag = unique.shuffled(random)
            if (bag.first() == last) {
                val swap = random.nextInt(1, bag.size)
                bag = bag.toMutableList().also { it[0] = bag[swap]; it[swap] = bag[0] }
            }
        }
        return Choice(bag.first(), bag.drop(1))
    }
}
