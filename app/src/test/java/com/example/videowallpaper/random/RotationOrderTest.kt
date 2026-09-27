package com.example.videowallpaper.random

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RotationOrderTest {
    @Test fun shuffleVisitsEveryImageAndDoesNotRepeatAtCycleBoundary() {
        val pool = (1..50).map(Int::toString)
        val random = Random(7)
        var last: String? = null
        var bag = emptyList<String>()
        repeat(100) {
            val cycle = mutableSetOf<String>()
            repeat(pool.size) {
                val choice = RotationOrder.next(pool, last, bag, RotationOrder.Mode.SHUFFLE, random)!!
                assertNotEquals(last, choice.uri)
                assertTrue(cycle.add(choice.uri))
                last = choice.uri
                bag = choice.remaining
            }
            assertEquals(pool.toSet(), cycle)
        }
    }
    @Test fun randomNeverRepeatsWithTwoOrMoreImages() {
        val random = Random(21)
        var last: String? = null
        repeat(1000) {
            val choice = RotationOrder.next(listOf("a", "b", "c"), last, emptyList(), RotationOrder.Mode.RANDOM, random)!!
            assertNotEquals(last, choice.uri)
            last = choice.uri
        }
    }
    @Test fun emptySingleAndDuplicatePoolsAreSafe() {
        assertNull(RotationOrder.next(emptyList(), null, emptyList(), RotationOrder.Mode.SHUFFLE))
        for (mode in RotationOrder.Mode.entries) {
            assertEquals("a", RotationOrder.next(listOf("a", "a"), "a", emptyList(), mode)!!.uri)
        }
    }
    @Test fun restoredQueueAndRemovedFilesAreRespected() {
        val result = RotationOrder.next(listOf("a", "c", "d"), "a", listOf("b", "c", "d"), RotationOrder.Mode.SHUFFLE)!!
        assertEquals("c", result.uri)
        assertEquals(listOf("d"), result.remaining)
    }
}
