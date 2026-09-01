package com.linkdeck.android.core.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor(private val parent: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) parent.map.clear()
                temp.forEach { (k, v) ->
                    if (v == null) parent.map.remove(k) else parent.map[k] = v
                }
            }
        }
    }

    @Test
    fun defaultSettings_allEnabledByDefault() {
        val fakePrefs = FakeSharedPreferences()
        val store = AppSettingsStore(fakePrefs)

        assertTrue(store.isAutomaticRoutingEnabled)
        assertTrue(store.isRememberChoicesEnabled)
        assertTrue(store.isTrackingCleanerEnabled)
        assertTrue(store.isRedirectCheckingEnabled)
        assertTrue(store.isThreatWarningsEnabled)
        assertTrue(store.isTlsInspectionEnabled)
        assertFalse(store.isOnboardingCompleted)
    }

    @Test
    fun updateSettings_valuesPersisted() {
        val fakePrefs = FakeSharedPreferences()
        val store = AppSettingsStore(fakePrefs)

        store.isAutomaticRoutingEnabled = false
        store.isRememberChoicesEnabled = false
        store.isTrackingCleanerEnabled = false
        store.isRedirectCheckingEnabled = false
        store.isThreatWarningsEnabled = false
        store.isTlsInspectionEnabled = false
        store.isOnboardingCompleted = true

        assertFalse(store.isAutomaticRoutingEnabled)
        assertFalse(store.isRememberChoicesEnabled)
        assertFalse(store.isTrackingCleanerEnabled)
        assertFalse(store.isRedirectCheckingEnabled)
        assertFalse(store.isThreatWarningsEnabled)
        assertFalse(store.isTlsInspectionEnabled)
        assertTrue(store.isOnboardingCompleted)
    }

    @Test
    fun resetSettings_restoresDefaults() {
        val fakePrefs = FakeSharedPreferences()
        val store = AppSettingsStore(fakePrefs)

        store.isAutomaticRoutingEnabled = false
        store.isRememberChoicesEnabled = false
        store.isTrackingCleanerEnabled = false
        store.isRedirectCheckingEnabled = false
        store.isThreatWarningsEnabled = false
        store.isTlsInspectionEnabled = false
        store.isOnboardingCompleted = true

        assertTrue(store.resetSettings())

        assertTrue(store.isAutomaticRoutingEnabled)
        assertTrue(store.isRememberChoicesEnabled)
        assertTrue(store.isTrackingCleanerEnabled)
        assertTrue(store.isRedirectCheckingEnabled)
        assertTrue(store.isThreatWarningsEnabled)
        assertTrue(store.isTlsInspectionEnabled)
        assertFalse(store.isOnboardingCompleted)
    }
}
