package id.xterm.core.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class IntegrityMonitor(
    private val securityManager: SecurityManager
) {
    private var monitorJob: Job? = null

    companion object {
        private var instance: IntegrityMonitor? = null
        
        fun getInstance(securityManager: SecurityManager): IntegrityMonitor {
            if (instance == null) {
                instance = IntegrityMonitor(securityManager)
            }
            return instance!!
        }
    }

    fun start(scope: CoroutineScope) {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch {
            while (isActive) {
                val intervalMs = Random.nextLong(20_000, 50_000)
                delay(intervalMs)

                val suspicious = securityManager.checkIntegrity()
                if (suspicious) {
                    securityManager.triggerIntegrityPunishment()
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
