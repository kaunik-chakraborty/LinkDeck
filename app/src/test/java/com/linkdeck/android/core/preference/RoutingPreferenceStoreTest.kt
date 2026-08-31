package com.linkdeck.android.core.preference

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPreferenceStoreTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String?, defValue: String?): String? {
            return (map[key] as? String) ?: defValue
        }

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
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
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
    fun saveAndGetPreference_exactDomain_returnsPreference() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        val pref = RoutingPreference("youtube.com", "com.google.android.youtube", "YouTube")
        val result = store.savePreference(pref)

        assertTrue(result is SavePreferenceResult.Success)
        val loaded = store.getPreference("youtube.com")
        assertNotNull(loaded)
        assertEquals("youtube.com", loaded?.domain)
        assertEquals("com.google.android.youtube", loaded?.packageName)
    }

    @Test
    fun getPreference_subdomain_matchesRootDomain() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        store.savePreference(RoutingPreference("youtube.com", "com.google.android.youtube", "YouTube"))

        val loaded = store.getPreference("m.youtube.com")
        assertNotNull(loaded)
        assertEquals("youtube.com", loaded?.domain)
    }

    @Test
    fun savePreference_duplicateDomain_updatesInPlace() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        store.savePreference(RoutingPreference("youtube.com", "com.google.android.youtube", "YouTube"))
        store.savePreference(RoutingPreference("youtube.com", "com.brave.browser", "Brave"))

        val all = store.getAllPreferences()
        assertEquals(1, all.size)
        assertEquals("com.brave.browser", all[0].packageName)
    }

    @Test
    fun removePreference_existingDomain_removesSuccessfully() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        store.savePreference(RoutingPreference("youtube.com", "com.google.android.youtube", "YouTube"))
        assertTrue(store.removePreference("youtube.com"))

        assertNull(store.getPreference("youtube.com"))
        assertEquals(0, store.getAllPreferences().size)
    }

    @Test
    fun savePreference_maxLimit50_enforcesLimitAndAllowsUpdate() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        for (i in 1..50) {
            val res = store.savePreference(RoutingPreference("domain$i.com", "com.app.$i", "App $i"))
            assertTrue(res is SavePreferenceResult.Success)
        }

        // Updating an existing domain when at 50 capacity must succeed
        val updateRes = store.savePreference(RoutingPreference("domain1.com", "com.updated.app", "Updated App"))
        assertTrue(updateRes is SavePreferenceResult.Success)
        assertEquals(50, store.getAllPreferences().size)
        assertEquals("com.updated.app", store.getPreference("domain1.com")?.packageName)

        // 51st new domain rejected
        val overflowResult = store.savePreference(RoutingPreference("domain51.com", "com.app.51", "App 51"))
        assertTrue(overflowResult is SavePreferenceResult.LimitReached)
        assertEquals(50, store.getAllPreferences().size)
    }

    @Test
    fun loadPreferences_corruptJson_failsSafeWithoutCrashing() {
        val fakePrefs = FakeSharedPreferences().apply {
            map["preferences_json"] = "{ malformed json ::: "
        }
        val store = SharedPreferencesRoutingPreferenceStore(fakePrefs)

        val list = store.getAllPreferences()
        assertTrue(list.isEmpty())
        assertNull(store.getPreference("youtube.com"))
    }
}
