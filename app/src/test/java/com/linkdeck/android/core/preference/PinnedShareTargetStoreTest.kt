package com.linkdeck.android.core.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedShareTargetStoreTest {

    private class InMemoryPinnedShareTargetStore : PinnedShareTargetStore {
        private val pinned = mutableSetOf<String>()

        override fun getPinnedTargets(): Set<String> = pinned.toSet()

        override fun isPinned(componentSignature: String): Boolean = pinned.contains(componentSignature)

        override fun togglePin(componentSignature: String): Boolean {
            return if (pinned.contains(componentSignature)) {
                pinned.remove(componentSignature)
                false
            } else {
                pinned.add(componentSignature)
                true
            }
        }

        override fun setPinned(componentSignature: String, isPinned: Boolean) {
            if (isPinned) {
                pinned.add(componentSignature)
            } else {
                pinned.remove(componentSignature)
            }
        }
    }

    @Test
    fun togglePin_correctlyPinsAndUnpinsTargets() {
        val store = InMemoryPinnedShareTargetStore()
        val signature = "com.whatsapp/com.whatsapp.ContactPicker"

        assertFalse(store.isPinned(signature))

        val pinnedState = store.togglePin(signature)
        assertTrue(pinnedState)
        assertTrue(store.isPinned(signature))
        assertEquals(setOf(signature), store.getPinnedTargets())

        val unpinnedState = store.togglePin(signature)
        assertFalse(unpinnedState)
        assertFalse(store.isPinned(signature))
        assertTrue(store.getPinnedTargets().isEmpty())
    }

    @Test
    fun multiplePins_trackedAccurately() {
        val store = InMemoryPinnedShareTargetStore()
        val whatsapp = "com.whatsapp/com.whatsapp.ContactPicker"
        val telegram = "org.telegram.messenger/org.telegram.ui.LaunchActivity"

        store.setPinned(whatsapp, true)
        store.setPinned(telegram, true)

        assertEquals(setOf(whatsapp, telegram), store.getPinnedTargets())
        assertTrue(store.isPinned(whatsapp))
        assertTrue(store.isPinned(telegram))

        store.setPinned(whatsapp, false)
        assertEquals(setOf(telegram), store.getPinnedTargets())
        assertFalse(store.isPinned(whatsapp))
        assertTrue(store.isPinned(telegram))
    }
}
