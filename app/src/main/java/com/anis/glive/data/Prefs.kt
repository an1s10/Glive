package com.anis.glive.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("glive", Context.MODE_PRIVATE)
    private val resumeSp = context.getSharedPreferences("glive_resume", Context.MODE_PRIVATE)

    var account: Account?
        get() {
            val s = sp.getString("server", null) ?: return null
            return Account(s, sp.getString("user", "") ?: "", sp.getString("pass", "") ?: "")
        }
        set(v) {
            sp.edit().apply {
                if (v == null) remove("server").remove("user").remove("pass")
                else putString("server", v.server).putString("user", v.user).putString("pass", v.pass)
            }.apply()
        }

    var userAgent: String
        get() = sp.getString("ua2", DEFAULT_UA) ?: DEFAULT_UA
        set(v) = sp.edit().putString("ua2", v).apply()

    var theme: String
        get() = sp.getString("theme", "bleu") ?: "bleu"
        set(v) = sp.edit().putString("theme", v).apply()

    var favoriteTeam: String
        get() = sp.getString("team", "Real Madrid") ?: "Real Madrid"
        set(v) = sp.edit().putString("team", v).apply()

    var lastChannel: Int
        get() = sp.getInt("lastChannel", -1)
        set(v) = sp.edit().putInt("lastChannel", v).apply()

    private val _favorites = MutableStateFlow(readSet("favorites").mapNotNull { it.toIntOrNull() }.toSet())
    val favorites: StateFlow<Set<Int>> = _favorites

    fun toggleFavorite(id: Int) {
        val s = _favorites.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _favorites.value = s
        sp.edit().putStringSet("favorites", s.map { it.toString() }.toSet()).apply()
    }

    private val _recents = MutableStateFlow(
        (sp.getString("recents", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }
    )
    val recents: StateFlow<List<Int>> = _recents

    fun addRecent(id: Int) {
        val l = (listOf(id) + _recents.value.filter { it != id }).take(20)
        _recents.value = l
        sp.edit().putString("recents", l.joinToString(",")).apply()
    }

    /** Langues affichées. Vide = toutes. */
    private val _langs = MutableStateFlow(readSet("langs").mapNotNull { runCatching { Lang.valueOf(it) }.getOrNull() }.toSet())
    val langs: StateFlow<Set<Lang>> = _langs

    fun toggleLang(l: Lang) {
        val s = _langs.value.toMutableSet()
        if (!s.add(l)) s.remove(l)
        _langs.value = s
        sp.edit().putStringSet("langs", s.map { it.name }.toSet()).apply()
    }

    private val _hidden = MutableStateFlow(readSet("hidden"))
    val hidden: StateFlow<Set<String>> = _hidden

    /** clé = "live:<id>", "vod:<id>" ou "series:<id>" */
    fun toggleHidden(key: String) {
        val s = _hidden.value.toMutableSet()
        if (!s.add(key)) s.remove(key)
        _hidden.value = s
        sp.edit().putStringSet("hidden", s).apply()
    }

    private fun readSet(key: String): Set<String> = sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    // ---- Reprise de lecture ----
    data class Resume(val key: String, val title: String, val image: String, val position: Long, val duration: Long, val time: Long)

    private val _resumes = MutableStateFlow(readResumes())
    val resumes: StateFlow<List<Resume>> = _resumes

    private fun readResumes(): List<Resume> = resumeSp.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split('\u0001') ?: return@mapNotNull null
        if (parts.size < 5) return@mapNotNull null
        Resume(k, parts[0], parts[1], parts[2].toLongOrNull() ?: 0, parts[3].toLongOrNull() ?: 0, parts[4].toLongOrNull() ?: 0)
    }.sortedByDescending { it.time }

    fun resumeFor(key: String): Resume? = _resumes.value.firstOrNull { it.key == key }

    fun saveResume(key: String, title: String, image: String, position: Long, duration: Long) {
        if (duration <= 0) return
        val done = position > duration * 0.93 || duration - position < 120_000
        if (done || position < 30_000) {
            resumeSp.edit().remove(key).apply()
        } else {
            resumeSp.edit().putString(key, listOf(title, image, position, duration, System.currentTimeMillis()).joinToString("\u0001")).apply()
        }
        _resumes.value = readResumes()
    }

    companion object {
        /** Beaucoup de fournisseurs filtrent les lecteurs inconnus : on essaie ces signatures dans l'ordre. */
        val USER_AGENTS = listOf(
            "VLC/3.0.20 LibVLC/3.0.20",
            "okhttp/4.12.0",
            "Mozilla/5.0 (Linux; Android 11; AFTKA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
            "Lavf/58.76.100",
        )
        val DEFAULT_UA = USER_AGENTS.first()
    }
}
