package com.example.on_device_qwen

import org.junit.Assert.*
import org.junit.Test

class ScrollAndFollowModeTests {

    /**
     * Simulates the exact state machine implemented in ChatScreen.kt
     * for smooth scrolling and follow mode.
     */
    class ScrollFollowController {
        var userScrolledAway: Boolean = false
        var autoScrollTriggerCount: Int = 0

        fun onScrollStateChanged(isScrollInProgress: Boolean, canScrollForward: Boolean) {
            // When user actively drags or scrolls upward away from the bottom
            if (isScrollInProgress && canScrollForward) {
                userScrolledAway = true
            } else if (!canScrollForward) {
                // Reached the very bottom
                userScrolledAway = false
            }
        }

        fun onTokenStreamChunkArrived() {
            // Auto-scroll ONLY when user has not intentionally scrolled away to read earlier messages
            if (!userScrolledAway) {
                autoScrollTriggerCount++
            }
        }

        fun onJumpToBottomClicked() {
            userScrolledAway = false
            autoScrollTriggerCount++
        }
    }

    @Test
    fun testInitialStateFollowsStreamingTokens() {
        val controller = ScrollFollowController()
        assertFalse("Initial state should not consider user scrolled away", controller.userScrolledAway)

        // 10 token chunks arrive
        repeat(10) {
            controller.onTokenStreamChunkArrived()
        }

        assertEquals("All 10 chunks should trigger auto-scroll when user is following stream", 10, controller.autoScrollTriggerCount)
    }

    @Test
    fun testUserScrollingAwaySuspendsAutoScroll() {
        val controller = ScrollFollowController()

        // Stream starts
        controller.onTokenStreamChunkArrived()
        assertEquals(1, controller.autoScrollTriggerCount)

        // User drags list up to read previous turn (isScrollInProgress = true, canScrollForward = true)
        controller.onScrollStateChanged(isScrollInProgress = true, canScrollForward = true)
        assertTrue("User scrolled away must be true after scrolling up", controller.userScrolledAway)

        // 15 new token chunks arrive while user is reading earlier context
        repeat(15) {
            controller.onTokenStreamChunkArrived()
        }

        // Auto-scroll should NOT have fired during user inspection
        assertEquals("Auto-scroll must be suspended while reading earlier messages", 1, controller.autoScrollTriggerCount)

        // User clicks 'Jump to latest' button
        controller.onJumpToBottomClicked()
        assertFalse("Jump to bottom must re-engage follow mode", controller.userScrolledAway)
        assertEquals("Jump to bottom triggers immediate scroll", 2, controller.autoScrollTriggerCount)

        // Subsequent tokens continue auto-scrolling
        controller.onTokenStreamChunkArrived()
        assertEquals(3, controller.autoScrollTriggerCount)
    }

    @Test
    fun testReachingBottomNaturallyReEngagesFollowMode() {
        val controller = ScrollFollowController()

        // Scrolled away
        controller.onScrollStateChanged(isScrollInProgress = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)

        // User scrolls back down to bottom (canScrollForward = false)
        controller.onScrollStateChanged(isScrollInProgress = false, canScrollForward = false)
        assertFalse("Reaching bottom should clear userScrolledAway", controller.userScrolledAway)

        // New tokens stream
        controller.onTokenStreamChunkArrived()
        assertEquals(1, controller.autoScrollTriggerCount)
    }
}
