package org.feeluown.mobile

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Xiaomi / Redmi / POCO may require a separate Security Center "Show on lock screen"
 * permission (MIUI AppOp 10020) even for a correctly flagged Activity.
 *
 * Reflection can be blocked by newer Android hidden-API rules. In that case Unknown
 * is NOT a denial: keep the switch functional and offer manual settings navigation.
 */
internal object LockscreenStageVendor {
    private const val XIAOMI_SHOW_WHEN_LOCKED_OP = 10020

    enum class Permission { Granted, Denied, Unknown }

    fun isXiaomiFamily(): Boolean {
        val name = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return name.equals("xiaomi", true) ||
            brand.equals("xiaomi", true) || brand.equals("redmi", true) ||
            brand.equals("poco", true)
    }

    @Suppress("DiscouragedPrivateApi")
    fun checkShowOnLockScreen(context: Context): Permission {
        if (!isXiaomiFamily()) return Permission.Unknown
        return try {
            val manager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return Permission.Unknown
            val check = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            when (check.invoke(manager, XIAOMI_SHOW_WHEN_LOCKED_OP, Process.myUid(), context.packageName) as? Int) {
                AppOpsManager.MODE_ALLOWED -> Permission.Granted
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> Permission.Denied
                else -> Permission.Unknown
            }
        } catch (_: ReflectiveOperationException) {
            Permission.Unknown
        } catch (_: SecurityException) {
            Permission.Unknown
        }
    }

    fun openPermissionPage(context: Context) {
        if (isXiaomiFamily()) {
            val vendorIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setPackage("com.miui.securitycenter")
                putExtra("extra_package_uid", Process.myUid())
                putExtra("extra_pkgname", context.packageName)
                putExtra("extra_package_name", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(vendorIntent) }.isSuccess) return
        }
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun diagnostics(context: Context): String? {
        val prefs = context.getSharedPreferences("ming_immersive_lockscreen", Context.MODE_PRIVATE)
        val phase = prefs.getString("stage_last_phase", null) ?: return null
        val timestamp = prefs.getLong("stage_last_timestamp", 0L)
        val time = if (timestamp > 0L)
            android.text.format.DateFormat.format("MM-dd HH:mm:ss", timestamp).toString()
        else "未知时间"
        val locked = prefs.getBoolean("stage_last_keyguard", false)
        return "$time · $phase · 系统锁屏状态：${if (locked) "已锁定" else "未锁定"}"
    }
}
