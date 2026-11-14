package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import java.util.Locale

/** Headless project command layer; the WebGL workspace only renders its persisted state. */
object WorkspaceCommandProcessor {
    data class ProjectState(val model: String, val edits: List<String>, val mapMode: Boolean, val lastCommand: String)
    data class Result(val state: ProjectState, val spokenResponse: String)

    private val workspaceWords = listOf("projekt", "pracovný priestor", "pracovny priestor", "3d", "trojrozmer", "model", "navrhni", "navrhn", "vizualiz", "nakresli", "vymodeluj", "vytvor", "valec", "kužeľ", "kuzel", "guľu", "gulu", "torus", "kváder", "kvader", "ukáž mi v 3d", "ukaz mi v 3d")
    private val editWords = listOf(
        "spoiler", "krídlo", "kridlo", "svetl", "reflektor", "led", "disk", "ráfik", "rafik",
        "červen", "cerv", "modr", "zmeň farbu", "zmen farbu",
        "pridaj", "vytvor", "odstráň", "odstran", "vymaž", "vymaz", "zväčši", "zvacsi",
        "zmenši", "zmensi", "otoč", "otoc", "posuň", "posun", "presuň", "presun",
        "valec", "guľu", "gulu", "kužeľ", "kuzel", "kocku", "kváder", "kvader",
        "torus", "prstenec", "rovinu", "plochu", "model", "objekt", "geometriu"
    )
    private val mapWords = listOf("mapu", "mapa", "zemeguľ", "zemegul", "ulic", "trasu", "trasa", "polohu")

    fun load(context: Context): ProjectState {
        val prefs = context.getSharedPreferences("jarvis_workspace", Context.MODE_PRIVATE)
        val edits = runCatching {
            val array = JSONArray(prefs.getString("project_edits", "[]").orEmpty())
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        return ProjectState(prefs.getString("project_model", "AUTO") ?: "AUTO", edits, prefs.getBoolean("workspace_map_mode", false), prefs.getString("workspace_last_command", "").orEmpty())
    }

    /** Null means this is ordinary conversation, not a project/workspace command. */
    fun process(context: Context, raw: String): Result? {
        val command = raw.trim()
        if (command.isBlank()) return null
        val prefs = context.getSharedPreferences("jarvis_workspace", Context.MODE_PRIVATE)
        val q = command.lowercase(Locale.ROOT)
        val previous = load(context)
        val explicitWorkspace = workspaceWords.any(q::contains)
        val projectEdit = editWords.any(q::contains) && (prefs.getBoolean("project_exists", false) || explicitWorkspace)
        val mapRequest = mapWords.any(q::contains) && (explicitWorkspace || q.contains("mapu") || q.contains("mapa") || q.contains("zemeguľ") || q.contains("zemegul") || q.contains("trasu"))
        if (!explicitWorkspace && !projectEdit && !mapRequest) return null

        val selectedModel = when {
            listOf("auto", "vozid", "športiak", "sportiak", "automobil", "car").any(q::contains) -> "AUTO"
            listOf("izb", "posteľ", "postel", "nábytok", "nabytok").any(q::contains) -> "IZBA"
            listOf("ulic", "cest", "chodník", "chodnik", "križovat", "krizovat", "budov").any(q::contains) -> "ULICA"
            listOf("dom", "strech", "komín", "komin").any(q::contains) -> "DOM"
            listOf("guľ", "gul", "lopta", "planéta", "planeta").any(q::contains) -> "GUĽA"
            (q.contains("vymodeluj") || q.contains("nový model") || q.contains("novy model") ||
                (!prefs.getBoolean("project_exists", false) && listOf("valec", "cylinder", "guľu", "gulu", "kužeľ", "kuzel", "torus", "kváder", "kvader", "kocku", "box").any(q::contains))) -> "CUSTOM"
            q.contains("pyram") -> "PYRAMÍDA"
            q.contains("kock") -> "KOCKA"
            else -> previous.model
        }
        val modelChanged = selectedModel != previous.model
        val edits = if (modelChanged) mutableListOf<String>() else previous.edits.toMutableList()
        val looksLikeEdit = editWords.any(q::contains) && !q.startsWith("otvor")
        if (looksLikeEdit && edits.lastOrNull() != command) {
            edits.add(command)
            while (edits.size > 30) edits.removeAt(0)
        }
        val isMap = mapRequest && !projectEdit
        val state = ProjectState(selectedModel, edits, if (isMap) true else if (explicitWorkspace && !mapRequest) false else previous.mapMode, command)
        val editJson = JSONArray().apply { state.edits.forEach { put(it) } }
        prefs.edit()
             .putBoolean("project_exists", true)
            .putBoolean("workspace_pending_open", true)
            .putString("project_model", state.model)
            .putString("project_edits", editJson.toString())
            .putBoolean("workspace_map_mode", state.mapMode)
            .putString("workspace_last_command", command)
            .apply()

        val response = when {
            isMap -> "Mapový príkaz som uložil, pane. Po otvorení pracovného priestoru sa obnoví mapa a posledná požiadavka."
            state.edits.size > previous.edits.size -> "Úpravu projektu som spracoval a uložil: $command. 3D scéna ju zobrazí pri otvorení pracovného priestoru."
            modelChanged -> "Model projektu som zmenil na ${state.model}. Stav je uložený a pripravený na vykreslenie."
            else -> "Pracovný projekt je pripravený. Aktuálny model: ${state.model}. Uložené úpravy zostávajú zachované."
        }
        return Result(state, response)
    }
}
