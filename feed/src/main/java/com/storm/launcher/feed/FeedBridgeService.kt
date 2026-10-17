package com.storm.launcher.feed

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Storm's way to the Google app's feed (the launcher overlay), made under this app's name: the Google app serves its feed
 * only to debuggable or system apps, and this one is debuggable where Storm itself is not.
 *
 * Storm binds this service as it would the Google app's and gets a feed back straight away: an [OverlayProxy], which
 * speaks the overlay's own protocol. This app connects to the Google app as itself, and the proxy passes each of
 * Storm's calls on as this app's own. It remembers the window Storm attached and the state of Storm's
 * activity, and hands them to the Google app again whenever its connection there is new, so Storm never sees one go;
 * and a call that finds no connection while Storm is in front reconnects. Storm's callback travels inside its calls,
 * and the Google app calls Storm back directly.
 */
class FeedBridgeService : Service() {

    /** One proxy per client binding, by the binding's data (Android hands each distinct intent its own onBind). */
    private val proxies = HashMap<Uri, OverlayProxy>()

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? {
        val client = intent.data ?: return null
        return proxies.getOrPut(client) { OverlayProxy(client).also { it.connect() } }
    }

    override fun onUnbind(intent: Intent): Boolean {
        intent.data?.let { proxies.remove(it)?.disconnect() }
        return false
    }

    override fun onDestroy() {
        proxies.values.forEach { it.disconnect() }
        proxies.clear()
        super.onDestroy()
    }

    private inner class OverlayProxy(private val client: Uri) : Binder() {

        init {
            attachInterface(null, OVERLAY_DESCRIPTOR)
        }

        /** The Google app's overlay, while connected. */
        @Volatile private var overlay: IBinder? = null

        private var connection: ServiceConnection? = null

        /** Held beside [connection], so the Google app keeps its priority while Storm is in front. */
        private var priorityConnection: ServiceConnection? = null

        /** The last window Storm attached (windowAttached2's bundle and callback), handed on again on a new connection. */
        @Volatile private var attachedWindow: Pair<Bundle, IBinder?>? = null

        /** The last activity state Storm reported (started 1, resumed 2), handed on with the window. */
        @Volatile private var activityState = 0

        /** Whether the Google app's feed is connected for this client (counted in [connectedClients]). */
        private var isConnected = false
            set(value) {
                if (field != value) connectedClients.addAndGet(if (value) 1 else -1)
                field = value
            }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
            enforceClient()
            data.enforceInterface(OVERLAY_DESCRIPTOR)
            val args = data.dataPosition()
            when (code) {
                WINDOW_ATTACHED_2 -> {
                    val bundle = data.readTypedObject(Bundle.CREATOR) ?: Bundle()
                    val callback = data.readStrongBinder()
                    attachedWindow = withOwnPackage(bundle) to callback
                    mainHandler.post { attachWindow() }
                    return true
                }
                WINDOW_DETACHED -> attachedWindow = null
                SET_ACTIVITY_STATE -> {
                    val state = data.readInt()
                    val resumed = state and ACTIVITY_RESUMED != 0 && activityState and ACTIVITY_RESUMED == 0
                    activityState = state
                    // Coming to the front, connect afresh: that takes the feed back if another launcher (the phone's
                    // own runs on for its recents screen) took it meanwhile. The new connection hands on this state.
                    if (resumed) {
                        mainHandler.post { connect() }
                        return true
                    }
                    data.setDataPosition(args)
                }
            }
            val target = overlay ?: run {
                // A call with nowhere to go while Storm is in front reconnects
                if (activityState and ACTIVITY_RESUMED != 0) mainHandler.post { if (overlay == null) connect() }
                return true
            }
            val call = Parcel.obtain()
            return try {
                // Sent again under a header of this app's: a call's header names the app it's made for, and the
                // Google app checks that against the package bound to it
                call.writeInterfaceToken(OVERLAY_DESCRIPTOR)
                call.appendFrom(data, args, data.dataSize() - args)
                target.transact(code, call, reply, flags)
            } catch (e: RemoteException) {
                Log.w(TAG, "Overlay call $code failed", e)
                true
            } finally {
                call.recycle()
            }
        }

        /** Storm's window's layout params, naming this app: the Google app moves the feed only for the package bound to it. */
        private fun withOwnPackage(bundle: Bundle): Bundle {
            bundle.classLoader = WindowManager.LayoutParams::class.java.classLoader
            val params = bundle.getParcelable(KEY_LAYOUT_PARAMS, WindowManager.LayoutParams::class.java) ?: return bundle
            bundle.putParcelable(KEY_LAYOUT_PARAMS, WindowManager.LayoutParams().apply {
                copyFrom(params)
                packageName = this@FeedBridgeService.packageName
            })
            return bundle
        }

        /** Hands the Google app Storm's window, then its activity state: what a new connection needs to show the feed. */
        private fun attachWindow() {
            val target = overlay ?: return
            val (bundle, callback) = attachedWindow ?: return
            send(target, WINDOW_ATTACHED_2) {
                writeTypedObject(bundle, 0)
                writeStrongBinder(callback)
            }
            send(target, SET_ACTIVITY_STATE) { writeInt(activityState) }
        }

        private inline fun send(target: IBinder, code: Int, args: Parcel.() -> Unit) {
            val call = Parcel.obtain()
            try {
                call.writeInterfaceToken(OVERLAY_DESCRIPTOR)
                call.args()
                target.transact(code, call, null, IBinder.FLAG_ONEWAY)
            } catch (e: RemoteException) {
                Log.w(TAG, "Overlay call $code failed", e)
            } finally {
                call.recycle()
            }
        }

        /**
         * Connects to the Google app afresh (on the main thread), keeping the old connection until the new one is made.
         *
         * The Google app keeps one feed for all its clients, tied to the uid of the first to connect, and starts afresh
         * only for a client of another version: other launchers ask as 7 or 10, so this asks as 8, which the
         * Google app treats as 7. Each connection's intent is new ([CONNECTION_KEY]): Android hands back the binder of
         * an intent a service has seen before without asking it, and after another launcher connected that's a closed
         * feed, which ignores every call.
         */
        fun connect() {
            val intent = Intent(OVERLAY_ACTION).setPackage(GOOGLE_APP_PACKAGE).setData(
                Uri.Builder().scheme("app").encodedAuthority("$packageName:${Process.myUid()}")
                    .appendQueryParameter("v", CLIENT_VERSION.toString())
                    .apply { client.getQueryParameter("cv")?.let { appendQueryParameter("cv", it) } }
                    .appendQueryParameter(CONNECTION_KEY, SystemClock.elapsedRealtimeNanos().toString())
                    .build()
            )
            val previous = connection
            val previousPriority = priorityConnection
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (connection !== this) return
                    overlay = service
                    isConnected = true
                    // Also when the Google app handed back the feed it had: the window attached to it may have been
                    // dismissed for another launcher's meanwhile
                    attachWindow()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (connection !== this) return
                    overlay = null
                    isConnected = false
                }
            }
            val bound = try {
                bindService(intent, conn, BIND_AUTO_CREATE or BIND_WAIVE_PRIORITY)
            } catch (e: SecurityException) {
                Log.w(TAG, "Google app refused the overlay connection", e)
                false
            }
            if (!bound) {
                runCatching { unbindService(conn) }
                return
            }
            connection = conn
            val priority = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
                override fun onServiceDisconnected(name: ComponentName) = Unit
            }
            priorityConnection = priority.takeIf {
                runCatching { bindService(intent, it, BIND_AUTO_CREATE or BIND_IMPORTANT) }.getOrDefault(false)
            }
            previous?.let { runCatching { unbindService(it) } }
            previousPriority?.let { runCatching { unbindService(it) } }
        }

        fun disconnect() {
            connection?.let { runCatching { unbindService(it) } }
            priorityConnection?.let { runCatching { unbindService(it) } }
            connection = null
            priorityConnection = null
            overlay = null
            isConnected = false
        }
    }

    /** Only Storm may use the feed through this app: anyone else would get the Google app's trust through it. */
    private fun enforceClient() {
        val uid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (packages.none { it in CLIENT_PACKAGES }) throw SecurityException("uid $uid may not use Storm Feed")
    }

    companion object {
        /** How many Storm clients the feed is connected for, for [StatusActivity]. */
        val connectedClients = AtomicInteger()

        private const val TAG = "StormFeed"
        private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        private const val OVERLAY_DESCRIPTOR = "com.google.android.libraries.launcherclient.ILauncherOverlay"
        private const val CLIENT_VERSION = 8
        private const val CONNECTION_KEY = "storm_connection"
        private const val KEY_LAYOUT_PARAMS = "layout_params"
        private val CLIENT_PACKAGES = setOf("com.storm.launcher", "com.storm.launcher.debug")

        // ILauncherOverlay transaction codes, as Storm's GoogleDiscoverOverlay sends them
        private const val WINDOW_DETACHED = IBinder.FIRST_CALL_TRANSACTION + 4
        private const val WINDOW_ATTACHED_2 = IBinder.FIRST_CALL_TRANSACTION + 13
        private const val SET_ACTIVITY_STATE = IBinder.FIRST_CALL_TRANSACTION + 15
        private const val ACTIVITY_RESUMED = 2
    }
}
