package com.anis.glive

import android.app.Application
import com.anis.glive.data.Prefs
import com.anis.glive.data.Repository

class GliveApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        repo = Repository(this, prefs)
        repo.start()
    }

    companion object {
        lateinit var instance: GliveApp
            private set
    }
}
