package com.example.on_device_qwen

import com.example.on_device_qwen.viewmodel.ScrollFollowController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies production [ScrollFollowController] behavior used by MessageList:
 * - auto-follow during streaming when pinned at bottom
 * - pausing auto-follow when user drags upward while reading earlier messages
 * - preserving position during incoming token chunks while paused
 * - re-engaging follow when reaching bottom or tapping "Jump to latest"
 * - conversation switch resets pause state
 * - exact LazyColumn index mapping when memory/header banners precede messages
 */
class ScrollAndFollowModeTests {

    @Test
    fun initialFollowModeTriggersScrollOnStreamingChunks() {
        val controller = ScrollFollowController()
        assertFalse(controller.userScrolledAway)
        assertTrue(controller.onNewMessageAdded())
        assertTrue(controller.onTokenStreamChunkArrived())
        assertTrue(controller.onTokenStreamChunkArrived())
        assertEquals(3, controller.autoScrollTriggerCount)
    }

    @Test
    fun draggingUpwardPausesAutoScrollAndPreservesReadingPosition() {
        val controller = ScrollFollowController()
        controller.onNewMessageAdded()
        val countBeforeDrag = controller.autoScrollTriggerCount

        // User drags away from bottom (canScrollForward = true)
        controller.onScrollStateChanged(isUserDragging = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)

        // User stops dragging while still scrolled upward
        controller.onScrollStateChanged(isUserDragging = false, canScrollForward = true)
        assertTrue("Still scrolled upward -> follow must remain paused", controller.userScrolledAway)

        // Incoming streaming tokens should NOT yank the viewport down
        assertFalse(controller.onTokenStreamChunkArrived())
        assertFalse(controller.onTokenStreamChunkArrived())
        assertEquals(countBeforeDrag, controller.autoScrollTriggerCount)
    }

    @Test
    fun reachingBottomManuallyReEngagesFollowMode() {
        val controller = ScrollFollowController()
        controller.onScrollStateChanged(isUserDragging = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)

        // Scroll reaches bottom (canScrollForward = false)
        controller.onScrollStateChanged(isUserDragging = false, canScrollForward = false)
        assertFalse(controller.userScrolledAway)
        assertTrue(controller.onTokenStreamChunkArrived())
    }

    @Test
    fun jumpToBottomButtonClearsPauseAndIncrementsTrigger() {
        val controller = ScrollFollowController()
        controller.onScrollStateChanged(isUserDragging = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)
        val beforeJump = controller.autoScrollTriggerCount

        controller.onJumpToBottomClicked()
        assertFalse(controller.userScrolledAway)
        assertEquals(beforeJump + 1, controller.autoScrollTriggerCount)
        assertTrue(controller.onTokenStreamChunkArrived())
    }

    @Test
    fun newUserMessageOverridesPauseAndFollowsNewTurn() {
        val controller = ScrollFollowController()
        controller.onScrollStateChanged(isUserDragging = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)

        assertTrue(controller.onNewMessageAdded())
        assertFalse(controller.userScrolledAway)
    }

    @Test
    fun conversationSwitchResetsUserScrolledAway() {
        val controller = ScrollFollowController()
        controller.onScrollStateChanged(isUserDragging = true, canScrollForward = true)
        assertTrue(controller.userScrolledAway)

        controller.resetForConversationSwitch()
        assertFalse(controller.userScrolledAway)
        assertTrue(controller.onTokenStreamChunkArrived())
    }

    @Test
    fun bottomAnchorAndMessageIndexAccountForLeadingMemoryHeader() {
        // Without header (0 leading items)
        assertEquals(0, ScrollFollowController.messageListIndex(messageIndex = 0, leadingHeaderCount = 0))
        assertEquals(4, ScrollFollowController.bottomAnchorIndex(messageCount = 4, leadingHeaderCount = 0))

        // With 1 leading memory indicator banner item
        assertEquals(1, ScrollFollowController.messageListIndex(messageIndex = 0, leadingHeaderCount = 1))
        assertEquals(5, ScrollFollowController.bottomAnchorIndex(messageCount = 4, leadingHeaderCount = 1))

        // Negative safety bounds clamp to 0
        assertEquals(0, ScrollFollowController.messageListIndex(messageIndex = -2, leadingHeaderCount = -1))
        assertEquals(0, ScrollFollowController.bottomAnchorIndex(messageCount = -1, leadingHeaderCount = -1))
    }
}
