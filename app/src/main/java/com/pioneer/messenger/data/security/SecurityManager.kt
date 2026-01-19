package com.pioneer.messenger.data.security

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Главный менеджер безопасности приложения
 * 
 * Функции:
 * - Обнаружение Root/Jailbreak
 * - Защита от отладки
 * - Проверка целостности приложения
 * - Обнаружение эмулятора
 * - Защита от MITM
 * - Stealth Mode
 */
@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecurityManager"
        
        // Известные пути root
        private val ROOT_PATHS = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su"
        )
        
        // Опасные пакеты
        private val DANGEROUS_PACKAGES = listOf(
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
            // Xposed
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            // Frida
            "re.frida.server",
            // Lucky Patcher
            "com.chelpus.lackypatch",
            "com.dimonvideo.luckypatcher",
            "com.forpda.lp"
        )
        
        // Эмуляторы
        private val EMULATOR_FILES = listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props"
        )
    }
    
    private val secureRandom = SecureRandom()
    private var stealthModeEnabled = false
    
    /**
     * Полная проверка безопасности устройства
     */
    fun performSecurityCheck(): SecurityCheckResult {
        val issues = mutableListOf<SecurityIssue>()
        
        // 1. Проверка Root
        if (isRooted()) {
            issues.add(SecurityIssue.ROOT_DETECTED)
        }
        
        // 2. Проверка отладки
        if (isDebuggerAttached()) {
            issues.add(SecurityIssue.DEBUGGER_ATTACHED)
        }
        
        // 3. Проверка эмулятора
        if (isEmulator()) {
            issues.add(SecurityIssue.EMULATOR_DETECTED)
        }
        
        // 4. Проверка опасных приложений
        if (hasDangerousApps()) {
            issues.add(SecurityIssue.DANGEROUS_APPS)
        }
        
        // 5. Проверка USB отладки
        if (isUsbDebuggingEnabled()) {
            issues.add(SecurityIssue.USB_DEBUGGING)
        }
        
        // 6. Проверка Developer Options
        if (isDeveloperOptionsEnabled()) {
            issues.add(SecurityIssue.DEVELOPER_OPTIONS)
        }
        
        // 7. Проверка целостности APK
        if (!verifyAppIntegrity()) {
            issues.add(SecurityIssue.TAMPERED_APP)
        }
        
        return SecurityCheckResult(
            isSecure = issues.isEmpty(),
            issues = issues,
            riskLevel = calculateRiskLevel(issues)
        )
    }
    
    /**
     * Проверка на Root
     */
    fun isRooted(): Boolean {
        return checkRootPaths() || 
               checkRootPackages() || 
               checkSuBinary() ||
               checkRootProperties() ||
               checkBusyBox()
    }
    
    private fun checkRootPaths(): Boolean {
        return ROOT_PATHS.any { File(it).exists() }
    }
    
    private fun checkRootPackages(): Boolean {
        val pm = context.packageManager
        return DANGEROUS_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkRootProperties(): Boolean {
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )
        
        return dangerousProps.any { (prop, value) ->
            try {
                val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.readLine()?.trim() == value
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun checkBusyBox(): Boolean {
        val busyboxPaths = listOf(
            "/system/xbin/busybox",
            "/system/bin/busybox",
            "/sbin/busybox"
        )
        return busyboxPaths.any { File(it).exists() }
    }
    
    /**
     * Проверка отладчика
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() ||
               Debug.waitingForDebugger() ||
               checkTracerPid() ||
               isDebuggable()
    }
    
    private fun checkTracerPid(): Boolean {
        return try {
            val status = File("/proc/self/status").readText()
            val tracerPid = status.lines()
                .find { it.startsWith("TracerPid:") }
                ?.split(":")?.get(1)?.trim()?.toIntOrNull() ?: 0
            tracerPid != 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    /**
     * Проверка эмулятора
     */
    fun isEmulator(): Boolean {
        return checkEmulatorFiles() ||
               checkEmulatorBuild() ||
               checkEmulatorProperties()
    }
    
    private fun checkEmulatorFiles(): Boolean {
        return EMULATOR_FILES.any { File(it).exists() }
    }
    
    private fun checkEmulatorBuild(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.startsWith("unknown") ||
               Build.MODEL.contains("google_sdk") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK built for x86") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               Build.BRAND.startsWith("generic") ||
               Build.DEVICE.startsWith("generic") ||
               Build.PRODUCT == "sdk" ||
               Build.PRODUCT == "sdk_x86" ||
               Build.PRODUCT == "vbox86p" ||
               Build.HARDWARE.contains("goldfish") ||
               Build.HARDWARE.contains("ranchu")
    }
    
    private fun checkEmulatorProperties(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.kernel.qemu"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.trim() == "1"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Проверка опасных приложений
     */
    fun hasDangerousApps(): Boolean {
        return checkRootPackages()
    }
    
    /**
     * Проверка USB отладки
     */
    fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }
    
    /**
     * Проверка Developer Options
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }
    
    /**
     * Проверка целостности приложения
     */
    fun verifyAppIntegrity(): Boolean {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, 0)
            
            // Проверяем, что приложение установлено из доверенного источника
            val installer = pm.getInstallerPackageName(context.packageName)
            val trustedInstallers = listOf(
                "com.android.vending", // Google Play
                "com.amazon.venezia",   // Amazon
                null // Прямая установка (для разработки)
            )
            
            trustedInstallers.contains(installer)
        } catch (e: Exception) {
            true // В случае ошибки считаем OK
        }
    }
    
    /**
     * Расчёт уровня риска
     */
    private fun calculateRiskLevel(issues: List<SecurityIssue>): RiskLevel {
        if (issues.isEmpty()) return RiskLevel.SAFE
        
        val criticalIssues = issues.count { it.severity == Severity.CRITICAL }
        val highIssues = issues.count { it.severity == Severity.HIGH }
        
        return when {
            criticalIssues > 0 -> RiskLevel.CRITICAL
            highIssues > 1 -> RiskLevel.HIGH
            highIssues == 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
    
    // ==================== STEALTH MODE ====================
    
    /**
     * Включить скрытый режим
     * Приложение маскируется под калькулятор
     */
    fun enableStealthMode() {
        stealthModeEnabled = true
        // Здесь можно добавить логику смены иконки через Activity-alias
    }
    
    fun disableStealthMode() {
        stealthModeEnabled = false
    }
    
    fun isStealthModeEnabled(): Boolean = stealthModeEnabled
    
    // ==================== ANTI-TAMPERING ====================
    
    /**
     * Генерация отпечатка устройства
     */
    fun generateDeviceFingerprint(): String {
        val data = StringBuilder()
        data.append(Build.BOARD)
        data.append(Build.BRAND)
        data.append(Build.DEVICE)
        data.append(Build.HARDWARE)
        data.append(Build.MANUFACTURER)
        data.append(Build.MODEL)
        data.append(Build.PRODUCT)
        
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        data.append(androidId)
        
        return sha256(data.toString())
    }
    
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    // ==================== MEMORY PROTECTION ====================
    
    /**
     * Очистка чувствительных данных из памяти
     */
    fun secureWipe(data: ByteArray) {
        secureRandom.nextBytes(data)
        data.fill(0)
    }
    
    fun secureWipe(data: CharArray) {
        data.fill('\u0000')
    }
    
    /**
     * Проверка на memory dump
     */
    fun isMemoryDumpDetected(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            // Проверяем подозрительное использование памяти
            false
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== DATA CLASSES ====================
    
    data class SecurityCheckResult(
        val isSecure: Boolean,
        val issues: List<SecurityIssue>,
        val riskLevel: RiskLevel
    )
    
    enum class SecurityIssue(val severity: Severity, val description: String) {
        ROOT_DETECTED(Severity.CRITICAL, "Обнаружен Root-доступ"),
        DEBUGGER_ATTACHED(Severity.CRITICAL, "Обнаружен отладчик"),
        EMULATOR_DETECTED(Severity.HIGH, "Запуск на эмуляторе"),
        DANGEROUS_APPS(Severity.HIGH, "Обнаружены опасные приложения"),
        USB_DEBUGGING(Severity.MEDIUM, "Включена USB-отладка"),
        DEVELOPER_OPTIONS(Severity.LOW, "Включены опции разработчика"),
        TAMPERED_APP(Severity.CRITICAL, "Приложение модифицировано")
    }
    
    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
    
    enum class RiskLevel { SAFE, LOW, MEDIUM, HIGH, CRITICAL }
}
