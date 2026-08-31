package com.linkdeck.android.core.rule

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRuleStoreTest {

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
    fun saveAndGetRule_standardRule_savesSuccessfully() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val rule = RoutingRule(
            id = "rule_1",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )
        val result = store.saveRule(rule)

        assertTrue(result is SaveRuleResult.Success)
        val rules = store.getRules()
        assertEquals(1, rules.size)
        assertEquals("youtube.com", rules[0].host)
        assertEquals("/shorts/*", rules[0].pathPattern)
    }

    @Test
    fun saveRule_conflictingCondition_returnsConflict() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val rule1 = RoutingRule(
            id = "rule_1",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )
        store.saveRule(rule1)

        val rule2 = RoutingRule(
            id = "rule_2",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.brave.browser",
            appLabel = "Brave"
        )
        val result2 = store.saveRule(rule2)

        assertTrue("Expected conflict when adding rule with same condition", result2 is SaveRuleResult.Conflict)
        assertEquals(1, store.getRules().size)
    }

    @Test
    fun saveRule_editingRuleToConflictWithAnotherRule_returnsConflict() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val rule1 = RoutingRule(
            id = "rule_1",
            host = "youtube.com",
            pathPattern = "/shorts/*",
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )
        store.saveRule(rule1)

        val rule2 = RoutingRule(
            id = "rule_2",
            host = "github.com",
            pathPattern = null,
            packageName = "com.android.chrome",
            appLabel = "Chrome"
        )
        store.saveRule(rule2)

        // Edit rule2 to have the same condition as rule1
        val editResult = store.saveRule(
            RoutingRule(
                id = "rule_2",
                host = "youtube.com",
                pathPattern = "/shorts/*",
                packageName = "com.android.chrome",
                appLabel = "Chrome"
            )
        )

        assertTrue("Expected conflict when editing rule to match another rule condition", editResult is SaveRuleResult.Conflict)
        assertEquals("github.com", store.getRules().first { it.id == "rule_2" }.host)
    }

    @Test
    fun toggleRuleEnabled_updatesState() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val rule = RoutingRule(
            id = "rule_1",
            host = "youtube.com",
            pathPattern = null,
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            isEnabled = true
        )
        store.saveRule(rule)

        assertTrue(store.toggleRuleEnabled("rule_1", false))
        val rules = store.getRules()
        assertEquals(false, rules[0].isEnabled)
    }

    @Test
    fun deleteRule_removesRule() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val rule = RoutingRule(
            id = "rule_1",
            host = "youtube.com",
            pathPattern = null,
            packageName = "com.google.android.youtube",
            appLabel = "YouTube"
        )
        store.saveRule(rule)
        assertTrue(store.deleteRule("rule_1"))
        assertTrue(store.getRules().isEmpty())
    }

    @Test
    fun saveRule_maxLimit50_enforcesLimitAndAllowsUpdate() {
        val fakePrefs = FakeSharedPreferences()
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        for (i in 1..50) {
            val res = store.saveRule(
                RoutingRule(
                    id = "rule_$i",
                    host = "domain$i.com",
                    pathPattern = null,
                    packageName = "com.app.$i",
                    appLabel = "App $i"
                )
            )
            assertTrue(res is SaveRuleResult.Success)
        }

        // Updating an existing rule at 50 capacity must succeed
        val updateRes = store.saveRule(
            RoutingRule(
                id = "rule_1",
                host = "domain1.com",
                pathPattern = "/path",
                packageName = "com.app.updated",
                appLabel = "Updated App"
            )
        )
        assertTrue(updateRes is SaveRuleResult.Success)
        assertEquals(50, store.getRules().size)

        // 51st new rule rejected
        val overflowResult = store.saveRule(
            RoutingRule(
                id = "rule_51",
                host = "domain51.com",
                pathPattern = null,
                packageName = "com.app.51",
                appLabel = "App 51"
            )
        )
        assertTrue(overflowResult is SaveRuleResult.LimitReached)
        assertEquals(50, store.getRules().size)
    }

    @Test
    fun loadRules_corruptJson_failsSafeWithoutCrashing() {
        val fakePrefs = FakeSharedPreferences().apply {
            map["rules_json"] = "[[[ corrupt invalid json }}}"
        }
        val store = SharedPreferencesRoutingRuleStore(fakePrefs)

        val list = store.getRules()
        assertTrue(list.isEmpty())
    }
}
