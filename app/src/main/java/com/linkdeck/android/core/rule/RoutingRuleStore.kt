package com.linkdeck.android.core.rule

import android.content.Context
import android.content.SharedPreferences
import com.linkdeck.android.core.preference.RoutingPreferenceMatcher
import org.json.JSONArray
import org.json.JSONObject

/**
 * Outcome of attempting to persist or update a [RoutingRule].
 */
sealed class SaveRuleResult {
    data class Success(val rule: RoutingRule) : SaveRuleResult()
    data class LimitReached(val maxLimit: Int) : SaveRuleResult()
    data class Conflict(val message: String) : SaveRuleResult()
    data class Invalid(val reason: String) : SaveRuleResult()
    data class Error(val message: String) : SaveRuleResult()
}

/**
 * Abstraction for local persistent storage of [RoutingRule]s.
 */
interface RuleStore {
    fun getRules(): List<RoutingRule>
    fun saveRule(rule: RoutingRule): SaveRuleResult
    fun updateRule(rule: RoutingRule): SaveRuleResult
    fun toggleRuleEnabled(id: String, enabled: Boolean): Boolean
    fun deleteRule(id: String): Boolean
    fun clearAll(): Boolean
}

/**
 * Thread-safe local storage for routing rules using private [SharedPreferences]
 * with structured JSON serialization, conflict checking, and safe fallback.
 */
class SharedPreferencesRoutingRuleStore(
    private val prefs: SharedPreferences
) : RuleStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Synchronized
    override fun getRules(): List<RoutingRule> {
        return loadRulesFromStorage()
    }

    @Synchronized
    override fun saveRule(rule: RoutingRule): SaveRuleResult {
        val validation = RoutingRule.validate(
            rawHost = rule.host,
            rawPathPattern = rule.pathPattern,
            packageName = rule.packageName,
            appLabel = rule.appLabel,
            isEnabled = rule.isEnabled,
            id = rule.id,
            createdAt = rule.createdAt
        )

        val cleanRule = when (validation) {
            is RuleValidationResult.Valid -> validation.rule
            is RuleValidationResult.Invalid -> return SaveRuleResult.Invalid(validation.reason)
        }

        val currentList = loadRulesFromStorage().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == cleanRule.id }

        // Check for identical condition conflict (same host & path pattern on any other rule)
        val conflict = currentList.firstOrNull {
            it.id != cleanRule.id && it.host == cleanRule.host && it.pathPattern == cleanRule.pathPattern
        }
        if (conflict != null) {
            return SaveRuleResult.Conflict(
                "A rule for '${cleanRule.displayCondition}' already exists (pointing to ${conflict.appLabel})"
            )
        }

        if (existingIndex != -1) {
            // Update existing rule in place
            currentList[existingIndex] = cleanRule
        } else {
            if (currentList.size >= MAX_RULES) {
                return SaveRuleResult.LimitReached(MAX_RULES)
            }
            currentList.add(cleanRule)
        }

        return if (persistRules(currentList)) {
            SaveRuleResult.Success(cleanRule)
        } else {
            SaveRuleResult.Error("Failed to write rule to storage")
        }
    }

    @Synchronized
    override fun updateRule(rule: RoutingRule): SaveRuleResult {
        return saveRule(rule)
    }

    @Synchronized
    override fun toggleRuleEnabled(id: String, enabled: Boolean): Boolean {
        val currentList = loadRulesFromStorage().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return false

        currentList[index] = currentList[index].copy(isEnabled = enabled)
        return persistRules(currentList)
    }

    @Synchronized
    override fun deleteRule(id: String): Boolean {
        val currentList = loadRulesFromStorage().toMutableList()
        val removed = currentList.removeAll { it.id == id }
        if (removed) {
            return persistRules(currentList)
        }
        return false
    }

    @Synchronized
    override fun clearAll(): Boolean {
        return prefs.edit().remove(KEY_RULES_JSON).commit()
    }

    private fun loadRulesFromStorage(): List<RoutingRule> {
        val jsonString = prefs.getString(KEY_RULES_JSON, null) ?: return emptyList()
        val result = mutableListOf<RoutingRule>()

        try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version > CURRENT_SCHEMA_VERSION) return emptyList()

            val array = root.optJSONArray("rules") ?: return emptyList()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val host = item.optString("host")
                val path = if (item.has("path") && !item.isNull("path")) item.optString("path") else null
                val pkg = item.optString("package")
                val label = item.optString("label", pkg)
                val enabled = item.optBoolean("enabled", true)
                val createdAt = item.optLong("created_at", System.currentTimeMillis())

                val canonicalHost = RoutingPreferenceMatcher.canonicalizeHost(host)
                if (id.isNotBlank() && canonicalHost != null && pkg.isNotBlank()) {
                    result.add(
                        RoutingRule(
                            id = id,
                            host = canonicalHost,
                            pathPattern = path,
                            packageName = pkg,
                            appLabel = label,
                            isEnabled = enabled,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safe fallback on corrupted data
            return emptyList()
        }

        return result
    }

    private fun persistRules(list: List<RoutingRule>): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", CURRENT_SCHEMA_VERSION)

            val array = JSONArray()
            for (rule in list) {
                val item = JSONObject()
                item.put("id", rule.id)
                item.put("host", rule.host)
                if (rule.pathPattern != null) {
                    item.put("path", rule.pathPattern)
                } else {
                    item.put("path", JSONObject.NULL)
                }
                item.put("package", rule.packageName)
                item.put("label", rule.appLabel)
                item.put("enabled", rule.isEnabled)
                item.put("created_at", rule.createdAt)
                array.put(item)
            }
            root.put("rules", array)

            prefs.edit().putString(KEY_RULES_JSON, root.toString()).commit()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val PREFS_NAME = "linkdeck_routing_rules"
        private const val KEY_RULES_JSON = "rules_json"
        private const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_RULES = 50
    }
}
