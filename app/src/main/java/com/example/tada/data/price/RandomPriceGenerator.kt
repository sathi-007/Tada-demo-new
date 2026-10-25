package com.example.tada.data.price

import com.example.tada.domain.repository.PriceGenerator
import javax.inject.Inject
import kotlin.random.Random

class RandomPriceGenerator @Inject constructor() : PriceGenerator {
    override fun nextPrice(): Int = nextPrice(Random.Default)

    companion object {
        const val MIN_INCLUSIVE = 1000
        const val MAX_INCLUSIVE = 10000

        fun nextPrice(random: Random): Int = random.nextInt(MIN_INCLUSIVE, MAX_INCLUSIVE + 1)
    }
}
