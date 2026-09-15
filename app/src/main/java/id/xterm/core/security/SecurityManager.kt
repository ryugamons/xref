package id.xterm.core.security

import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.random.Random
import kotlin.system.exitProcess

class SecurityManager {

    companion object {
        init {
            System.loadLibrary("xtsec")
        }
        
        private var instance: SecurityManager? = null
        
        fun getInstance(): SecurityManager {
            if (instance == null) {
                instance = SecurityManager()
            }
            return instance!!
        }
    }

    private var initialized = false

    fun ensureInit() {
        if (!initialized) {
            nativeInit()
            initialized = true
        }
    }

    fun activateAndVerify(
        username: String,
        challenge: String,
        signatureBase64: String,
        canaryBlob: ByteArray
    ): Boolean {
        ensureInit()
        nativeVerifyAndDeriveKey(username, challenge, signatureBase64)
        return nativeTryDecryptCanary(canaryBlob)
    }

    fun decryptPayload(encrypted: ByteArray): ByteArray? = nativeDecryptPayload(encrypted)

    fun triggerIntegrityPunishment() {
        val delayMs = Random.nextLong(400, 3500)
        Handler(Looper.getMainLooper()).postDelayed({
            nativeKill()
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }, delayMs)
    }

    fun checkIntegrity(): Boolean = nativeCheckIntegrity()
    
    private external fun nativeCheckIntegrity(): Boolean
    private external fun nativeInit()
    private external fun nativeVerifyAndDeriveKey(username: String, challenge: String, signatureBase64: String)
    private external fun nativeTryDecryptCanary(canaryBlob: ByteArray): Boolean
    private external fun nativeDecryptPayload(encrypted: ByteArray): ByteArray?
    private external fun nativeKill()
}
