package com.jarvis.assistant

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, on-device notes about people the user knows.
 *
 * Facts keep their source so JARVIS can distinguish what the user said,
 * what the person said, and what was checked in a public source.
 * Photos are references explicitly selected by the user; this store does
 * not scrape social networks or download profile pictures.
 */
data class PersonFact(
    val text: String,
    val source: String,
    val sourceDetail: String = "",
    val recordedAt: Long = System.currentTimeMillis()
)

data class PersonProfile(
    val id: String,
    val displayName: String,
    val relationship: String = "",
    val photoUri: String? = null,
    val facts: List<PersonFact> = emptyList(),
    val sharedMemories: List<String> = emptyList(),
    val communicationPreferences: List<String> = emptyList(),
    val openTopics: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

object PersonProfileStore {
    private const val PREFS = "jarvis_people_profiles_v1"
    private const val KEY_PROFILES = "profiles"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<PersonProfile> {
        val raw = prefs(context).getString(KEY_PROFILES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                runCatching { decode(array.getJSONObject(index)) }.getOrNull()
            }.sortedBy { it.displayName.lowercase() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun find(context: Context, id: String): PersonProfile? =
        all(context).firstOrNull { it.id == id }

    @Synchronized
    fun save(context: Context, profile: PersonProfile) {
        require(profile.id.isNotBlank()) { "Profile id must not be blank." }
        require(profile.displayName.isNotBlank()) { "Display name must not be blank." }
        val current = all(context).filterNot { it.id == profile.id }
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        val array = JSONArray()
        (current + updated).forEach { array.put(encode(it)) }
        check(prefs(context).edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Could not save JARVIS person profile."
        }
    }

    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        val current = all(context)
        if (current.none { it.id == id }) return false
        val array = JSONArray()
        current.filterNot { it.id == id }.forEach { array.put(encode(it)) }
        return prefs(context).edit().putString(KEY_PROFILES, array.toString()).commit()
    }

    /** Add a fact with explicit provenance; unknown provenance is never silently treated as verified. */
    @Synchronized
    fun addFact(context: Context, id: String, fact: PersonFact): Boolean {
        if (fact.text.isBlank() || fact.source !in setOf("user", "person", "public_source", "unverified")) return false
        val profile = find(context, id) ?: return false
        save(context, profile.copy(facts = profile.facts + fact))
        return true
    }

    /** Store a photo URI selected by the user; caller must obtain URI permission when needed. */
    @Synchronized
    fun setPhoto(context: Context, id: String, uri: Uri?): Boolean {
        val profile = find(context, id) ?: return false
        save(context, profile.copy(photoUri = uri?.toString()))
        return true
    }

    private fun encode(profile: PersonProfile) = JSONObject().apply {
        put("id", profile.id)
        put("displayName", profile.displayName)
        put("relationship", profile.relationship)
        put("photoUri", profile.photoUri ?: JSONObject.NULL)
        put("facts", JSONArray().apply {
            profile.facts.forEach { fact ->
                put(JSONObject().apply {
                    put("text", fact.text)
                    put("source", fact.source)
                    put("sourceDetail", fact.sourceDetail)
                    put("recordedAt", fact.recordedAt)
                })
            }
        })
        put("sharedMemories", JSONArray(profile.sharedMemories))
        put("communicationPreferences", JSONArray(profile.communicationPreferences))
        put("openTopics", JSONArray(profile.openTopics))
        put("updatedAt", profile.updatedAt)
    }

    private fun decode(json: JSONObject): PersonProfile {
        val factsJson = json.optJSONArray("facts") ?: JSONArray()
        val facts = (0 until factsJson.length()).mapNotNull { index ->
            val item = factsJson.optJSONObject(index) ?: return@mapNotNull null
            PersonFact(
                text = item.optString("text"),
                source = item.optString("source", "unverified"),
                sourceDetail = item.optString("sourceDetail"),
                recordedAt = item.optLong("recordedAt", 0L)
            )
        }
        fun strings(key: String): List<String> {
            val array = json.optJSONArray(key) ?: JSONArray()
            return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }
        return PersonProfile(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            relationship = json.optString("relationship"),
            photoUri = json.optString("photoUri").takeUnless { it.isBlank() || it == "null" },
            facts = facts,
            sharedMemories = strings("sharedMemories"),
            communicationPreferences = strings("communicationPreferences"),
            openTopics = strings("openTopics"),
            updatedAt = json.optLong("updatedAt", 0L)
        )
    }
}
