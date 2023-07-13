package co.smartreceipts.android.utils

import android.os.Build
import android.os.StrictMode
import co.smartreceipts.android.BuildConfig
import co.smartreceipts.analytics.log.Logger
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * A simple wrapper around our [StrictMode] configuration to allow us to enable this for testing
 * purposes
 */
object StrictModeConfiguration {

    private const val MAX_STACK_DEPTH_TO_CHECK = 15

    /**
     * Enables strict mode
     */
    @JvmStatic
    fun enable() {
        Logger.debug(this, "Enabling strict mode")
        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder()
        threadPolicyBuilder.detectNetwork()
        // threadPolicyBuilder.detectCustomSlowCalls() Note: Excluding as AdMob can fail this
        threadPolicyBuilder.detectDiskReads()
        threadPolicyBuilder.detectDiskWrites()
        // threadPolicyBuilder.detectUnbufferedIo() Note: Excluding as our 3p libraries can fail this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            threadPolicyBuilder.detectResourceMismatches()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // We use a custom policy to white-list specific failures
            threadPolicyBuilder.penaltyListener(Executors.newSingleThreadExecutor(), StrictMode.OnThreadViolationListener {
                // We use this to check for a strict mode violation that occurs due to instant run
                // https://stackoverflow.com/questions/51021362/strictmode-disk-read-violation-on-empty-activitys-setcontentview
                val stackTrace = it.stackTrace.asList()
                stackTrace.reversed()
                stackTrace.subList(0, min(stackTrace.size, MAX_STACK_DEPTH_TO_CHECK))
                var hasInflationTraceElement = false
                var hasDexTraceElement = false
                var hasPreferenceManagerInflation = false
                var hasPreferenceReadWrite = false
                var hasBridgingQuery = false
                var hasEmailAttachments = false
                var hasLeakCanary = false
                var isTestRunning = false
                stackTrace.forEach { stackTraceElement ->
                    if (stackTraceElement.toString().contains("androidx.test.runner")) {
                        isTestRunning = true
                    }
                    if (stackTraceElement.toString().contains("LayoutInflater.createView")) {
                        hasInflationTraceElement = true
                    }
                    if (stackTraceElement.toString().contains("BaseDexClassLoader.findClass")) {
                        hasDexTraceElement = true
                    }
                    if (stackTraceElement.toString().contains("PreferenceManager.inflateFromResource")) {
                        hasPreferenceManagerInflation = true
                    }
                    if (stackTraceElement.toString().contains("CrashReporter.initialize") ||
                            stackTraceElement.toString().contains("SharedPreferencesImpl.awaitLoadedLocked")) {
                        hasPreferenceReadWrite = true
                    }
                    if (stackTraceElement.toString().contains("TripForeignKeyAbstractSqlTable.getBlocking")) {
                        hasBridgingQuery = true
                    }
                    if (stackTraceElement.toString().contains("EmailAssistant.onAttachmentsCreated")) {
                        hasEmailAttachments = true
                    }
                    if (stackTraceElement.toString().contains("leakcanary.internal.HeapDumpControl")) {
                        hasLeakCanary = true
                    }
                }
                val isWhiteListed = (hasInflationTraceElement and hasDexTraceElement) or
                        hasDexTraceElement or hasPreferenceManagerInflation or
                        hasPreferenceReadWrite or hasBridgingQuery or hasEmailAttachments or
                        hasLeakCanary or isTestRunning
                if (!isWhiteListed) {
                    throw it
                } else {
                    Logger.warn(this, "Ignoring StrictMode Failure for WhiteListed violation", it.message)
                }
            })
        } else {
            threadPolicyBuilder.penaltyLog()
        }

        threadPolicyBuilder.build()
        StrictMode.setThreadPolicy(threadPolicyBuilder.build())


        val vmPolicyBuilder = StrictMode.VmPolicy.Builder()
        vmPolicyBuilder.detectActivityLeaks()
        vmPolicyBuilder.detectFileUriExposure()
        vmPolicyBuilder.detectLeakedClosableObjects()
        vmPolicyBuilder.detectLeakedRegistrationObjects()
        vmPolicyBuilder.detectLeakedSqlLiteObjects()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vmPolicyBuilder.detectCleartextNetwork()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vmPolicyBuilder.detectContentUriWithoutPermission()
        }
        // vmPolicyBuilder.detectUntaggedSockets() Note: We exclude this one as many of our 3p libraries fail it
        vmPolicyBuilder.penaltyLog()
        StrictMode.setVmPolicy(vmPolicyBuilder.build())
    }

    @JvmStatic
    fun <T> permitDiskReads(func: () -> T?): T? {
        val newPolicy = StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy()).permitDiskReads().build()

        return temporaryPolicyChange(func, newPolicy)
    }

    @JvmStatic
    fun <T> permitDiskWrites(func: () -> T?): T? {
        val newPolicy = StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy()).permitDiskWrites().build()

        return temporaryPolicyChange(func, newPolicy)
    }

    private fun <T> temporaryPolicyChange(func: () -> T?, newThreadPolicy: StrictMode.ThreadPolicy): T? {
        return if (BuildConfig.DEBUG) {
            val oldThreadPolicy = StrictMode.getThreadPolicy()

            StrictMode.setThreadPolicy(newThreadPolicy)
            val resultValue = func()

            StrictMode.setThreadPolicy(oldThreadPolicy)

            resultValue
        } else {
            func()
        }
    }
}