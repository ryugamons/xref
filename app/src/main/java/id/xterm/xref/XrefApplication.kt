package id.xterm.xref

import android.app.Application
import android.content.Context
import id.xterm.core.security.IntegrityMonitor
import id.xterm.core.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class XrefApplication : Application() {
    
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Start Security Integrity Monitor
        val securityManager = SecurityManager.getInstance()
        val integrityMonitor = IntegrityMonitor.getInstance(securityManager)
        integrityMonitor.start(appScope)
    }

    companion object {
        private lateinit var instance: XrefApplication
        fun getContext(): Context = instance.applicationContext
    }
}
