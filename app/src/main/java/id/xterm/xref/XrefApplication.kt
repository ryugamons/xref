package id.xterm.xref

import android.app.Application
import android.content.Context

class XrefApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: XrefApplication
        fun getContext(): Context = instance.applicationContext
    }
}
