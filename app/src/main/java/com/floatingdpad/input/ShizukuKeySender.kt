package com.floatingdpad.input

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.floatingdpad.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Delivers key events through a helper process that Shizuku forks for us as the shell
 * UID -- the same UID `adb shell input keyevent` runs as, and the reason this works.
 *
 * The user service is bound once and kept bound. Shelling out to `input keyevent` per
 * press would cost 100-200 ms of process spawn each time, which makes scrolling an EPG
 * guide unusable.
 */
object ShizukuKeySender : KeySender {

    enum class State {
        /** Shizuku itself is not installed. */
        NOT_INSTALLED,

        /** Installed, but not started -- the post-reboot state. */
        NOT_RUNNING,

        /** Running, but has not granted this app access yet. */
        PERMISSION_REQUIRED,

        /** Forking the helper process. */
        CONNECTING,

        /** Keys are going through. */
        READY,

        /** Something unexpected; see logcat. */
        FAILED,
    }

    const val PERMISSION_REQUEST_CODE = 4242

    private const val TAG = "ShizukuKeySender"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()

    private var appContext: Context? = null

    @Volatile
    private var injector: IKeyInjector? = null

    @Volatile
    var state: State = State.NOT_RUNNING
        private set

    override val isReady: Boolean
        get() = injector != null

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { connect() }

    private val onBinderDead = Shizuku.OnBinderDeadListener {
        injector = null
        setState(State.NOT_RUNNING)
    }

    private val onPermissionResult =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                connect()
            } else {
                setState(State.PERMISSION_REQUIRED)
            }
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                injector = IKeyInjector.Stub.asInterface(binder)
                setState(State.READY)
            } else {
                injector = null
                setState(State.FAILED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            injector = null
            setState(State.NOT_RUNNING)
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, KeyInjectorService::class.java.name),
        )
            // Not a daemon: the helper dies with us rather than lingering as a stray
            // shell-UID process after the overlay is stopped.
            .daemon(false)
            .processNameSuffix("keyinjector")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    /** Idempotent; safe to call from every entry point (activity, service, tile). */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }
        connect()
    }

    /**
     * Works out where we stand and binds the helper if it can. Cheap and safe to call
     * repeatedly -- this is also the "tap to reconnect" path after a reboot.
     */
    fun connect() {
        if (injector != null) {
            setState(State.READY)
            return
        }
        if (state == State.CONNECTING) return

        if (!Shizuku.pingBinder()) {
            setState(if (isShizukuInstalled()) State.NOT_RUNNING else State.NOT_INSTALLED)
            return
        }
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku pre-v11 is not supported")
            setState(State.FAILED)
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            setState(State.PERMISSION_REQUIRED)
            return
        }

        setState(State.CONNECTING)
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            setState(State.FAILED)
        }
    }

    /** Asks Shizuku for access. Only meaningful in state PERMISSION_REQUIRED. */
    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            connect()
            return
        }
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission failed", t)
            setState(State.FAILED)
        }
    }

    override fun send(keyCode: Int, action: Int, repeatCount: Int, downTime: Long): Boolean {
        val target = injector
        if (target == null) {
            connect()
            return false
        }
        return try {
            target.injectKey(keyCode, action, repeatCount, downTime)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "inject failed, dropping binder", t)
            injector = null
            setState(State.NOT_RUNNING)
            connect()
            false
        }
    }

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        main.post { listener(state) }
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    private fun isShizukuInstalled(): Boolean {
        val pm = appContext?.packageManager ?: return false
        return try {
            pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setState(next: State) {
        if (state == next) return
        state = next
        main.post { listeners.forEach { it(next) } }
    }
}
