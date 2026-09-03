package com.linkdeck.android.core.cleaner.rules

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * On-device persistence store for custom tracking parameter rules.
 * Manages atomic read/writes via SharedPreferences and maintains an in-memory cache.
 */
class CustomParameterRulesStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedRules: List<CustomParameterRule>? = null

    @Synchronized
    fun getRules(): List<CustomParameterRule> {
        cachedRules?.let { return it }

        val jsonStr = prefs.getString(KEY_RULES, null) ?: return emptyList()
        val list = mutableListOf<CustomParameterRule>()

        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomParameterRule(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        parameterPattern = obj.getString("parameterPattern"),
                        isPrefix = obj.optBoolean("isPrefix", false),
                        domainPattern = obj.optString("domainPattern").takeIf { it.isNotEmpty() },
                        action = try {
                            ParameterRuleAction.valueOf(obj.optString("action", ParameterRuleAction.BLOCK.name))
                        } catch (_: Exception) {
                            ParameterRuleAction.BLOCK
                        },
                        isEnabled = obj.optBoolean("isEnabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // Fallback gracefully on deserialization errors
        }

        cachedRules = list
        return list
    }

    @Synchronized
    fun getEnabledRules(): List<CustomParameterRule> {
        return getRules().filter { it.isEnabled }
    }

    @Synchronized
    fun saveRule(rule: CustomParameterRule) {
        val current = getRules().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == rule.id }
        if (existingIndex != -1) {
            current[existingIndex] = rule
        } else {
            current.add(0, rule)
        }
        persistRules(current)
    }

    @Synchronized
    fun deleteRule(id: String) {
        val current = getRules().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            persistRules(current)
        }
    }

    @Synchronized
    fun toggleRule(id: String, enabled: Boolean) {
        val current = getRules().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(isEnabled = enabled)
            persistRules(current)
        }
    }

    @Synchronized
    fun loadPresets(): Int {
        val current = getRules().toMutableList()
        val existingKeys = current.map { it.parameterPattern.lowercase() }.toSet()
        var addedCount = 0

        val presets = listOf(
            CustomParameterRule(
                parameterPattern = "igsi",
                isPrefix = false,
                domainPattern = "instagram.com",
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "igsh",
                isPrefix = false,
                domainPattern = "instagram.com",
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "ref_src",
                isPrefix = false,
                domainPattern = "twitter.com",
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "mibextid",
                isPrefix = false,
                domainPattern = "facebook.com",
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "feature",
                isPrefix = false,
                domainPattern = "youtube.com",
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "mkt_*",
                isPrefix = true,
                domainPattern = null,
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "cmpid",
                isPrefix = false,
                domainPattern = null,
                action = ParameterRuleAction.BLOCK
            ),
            CustomParameterRule(
                parameterPattern = "aff_id",
                isPrefix = false,
                domainPattern = null,
                action = ParameterRuleAction.BLOCK
            )
        )

        for (preset in presets) {
            if (!existingKeys.contains(preset.parameterPattern.lowercase())) {
                current.add(preset)
                addedCount++
            }
        }

        if (addedCount > 0) {
            persistRules(current)
        }
        return addedCount
    }

    private fun persistRules(rules: List<CustomParameterRule>) {
        cachedRules = rules
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("parameterPattern", rule.parameterPattern)
                put("isPrefix", rule.isPrefix)
                put("domainPattern", rule.domainPattern ?: "")
                put("action", rule.action.name)
                put("isEnabled", rule.isEnabled)
                put("createdAt", rule.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "linkdeck_custom_parameter_rules"
        private const val KEY_RULES = "custom_rules_json"
    }
}
