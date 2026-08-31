package com.linkdeck.android.core.preference

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result outcome of attempting to persist a routing preference.
 */
sealed class SavePreferenceResult {
    data class Success(val preference: RoutingPreference) : SavePreferenceResult()
    data class LimitReached(val maxLimit: Int) : SavePreferenceResult()
    data class InvalidDomain(val reason: String) : SavePreferenceResult()
    data class Error(val message: String) : SavePreferenceResult()
}

/**
 * Abstraction for reading and modifying local routing preferences.
 */
interface PreferenceStore {
    fun getPreference(host: String): RoutingPreference?
    fun getAllPreferences(): List<RoutingPreference>
    fun savePreference(preference: RoutingPreference): SavePreferenceResult
    fun removePreference(domain: String): Boolean
    fun clearAll(): Boolean
}

/**
 * Thread-safe local storage for routing preferences using private [SharedPreferences]
 * with structured JSON serialization and safe error recovery.
 */
class SharedPreferencesRoutingPreferenceStore(
    private val prefs: SharedPreferences
) : PreferenceStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Synchronized
    override fun getPreference(host: String): RoutingPreference? {
        val canonicalHost = RoutingPreferenceMatcher.canonicalizeHost(host) ?: return null
        val all = loadPreferencesFromStorage()

        // Match against exact domain or parent domain
        return all.firstOrNull { pref ->
            RoutingPreferenceMatcher.matches(canonicalHost, pref.domain)
        }
    }

    @Synchronized
    override fun getAllPreferences(): List<RoutingPreference> {
        return loadPreferencesFromStorage()
    }

    @Synchronized
    override fun savePreference(preference: RoutingPreference): SavePreferenceResult {
        val canonicalDomain = RoutingPreferenceMatcher.canonicalizeHost(preference.domain)
            ?: return SavePreferenceResult.InvalidDomain("Invalid or unsupported domain: ${preference.domain}")

        if (preference.packageName.isBlank()) {
            return SavePreferenceResult.Error("Target package cannot be blank")
        }

        val cleanPref = preference.copy(domain = canonicalDomain)
        val currentList = loadPreferencesFromStorage().toMutableList()

        val existingIndex = currentList.indexOfFirst { it.domain == canonicalDomain }

        if (existingIndex != -1) {
            // Update existing preference in place
            currentList[existingIndex] = cleanPref
        } else {
            if (currentList.size >= MAX_PREFERENCES) {
                return SavePreferenceResult.LimitReached(MAX_PREFERENCES)
            }
            currentList.add(cleanPref)
        }

        return if (persistPreferences(currentList)) {
            SavePreferenceResult.Success(cleanPref)
        } else {
            SavePreferenceResult.Error("Failed to write preference to storage")
        }
    }

    @Synchronized
    override fun removePreference(domain: String): Boolean {
        val canonicalDomain = RoutingPreferenceMatcher.canonicalizeHost(domain) ?: domain.trim().lowercase()
        val currentList = loadPreferencesFromStorage().toMutableList()
        val removed = currentList.removeAll { it.domain == canonicalDomain }

        if (removed) {
            return persistPreferences(currentList)
        }
        return false
    }

    @Synchronized
    override fun clearAll(): Boolean {
        return prefs.edit().remove(KEY_PREFERENCES_JSON).commit()
    }

    private fun loadPreferencesFromStorage(): List<RoutingPreference> {
        val jsonString = prefs.getString(KEY_PREFERENCES_JSON, null) ?: return emptyList()
        val result = mutableListOf<RoutingPreference>()

        try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            if (version > CURRENT_SCHEMA_VERSION) {
                return emptyList()
            }

            val array = root.optJSONArray("preferences") ?: return emptyList()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val domain = item.optString("domain")
                val pkg = item.optString("package")
                val label = item.optString("label", pkg)
                val createdAt = item.optLong("created_at", System.currentTimeMillis())

                val canonicalDomain = RoutingPreferenceMatcher.canonicalizeHost(domain)
                if (canonicalDomain != null && pkg.isNotBlank()) {
                    result.add(
                        RoutingPreference(
                            domain = canonicalDomain,
                            packageName = pkg,
                            appLabel = label,
                            createdAt = createdAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safe fallback on corrupted JSON
            return emptyList()
        }

        return result
    }

    private fun persistPreferences(list: List<RoutingPreference>): Boolean {
        return try {
            val root = JSONObject()
            root.put("version", CURRENT_SCHEMA_VERSION)

            val array = JSONArray()
            for (pref in list) {
                val item = JSONObject()
                item.put("domain", pref.domain)
                item.put("package", pref.packageName)
                item.put("label", pref.appLabel)
                item.put("created_at", pref.createdAt)
                array.put(item)
            }
            root.put("preferences", array)

            prefs.edit().putString(KEY_PREFERENCES_JSON, root.toString()).commit()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val PREFS_NAME = "linkdeck_routing_preferences"
        private const val KEY_PREFERENCES_JSON = "preferences_json"
        private const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PREFERENCES = 50
    }
}
