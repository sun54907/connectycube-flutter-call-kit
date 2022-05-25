package com.connectycube.flutter.connectycube_flutter_call_kit

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.connectycube.flutter.connectycube_flutter_call_kit.utils.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

import android.content.ContentValues.TAG
import android.util.Log


/** ConnectycubeFlutterCallKitPlugin */
@Keep
class ConnectycubeFlutterCallKitPlugin : FlutterPlugin, MethodCallHandler,
    PluginRegistry.NewIntentListener, ActivityAware, BroadcastReceiver() {
    private var applicationContext: Context? = null
    private var mainActivity: Activity? = null
    private lateinit var channel: MethodChannel
    private lateinit var localBroadcastManager: LocalBroadcastManager

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(
            flutterPluginBinding.applicationContext,
            flutterPluginBinding.binaryMessenger
        )
        registerCallStateReceiver()
    }

    private fun onAttachedToEngine(context: Context, binaryMessenger: BinaryMessenger) {
        this.applicationContext = context
        this.channel = MethodChannel(binaryMessenger, "connectycube_flutter_call_kit")
        this.channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "showCallNotification" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    if (CALL_STATE_UNKNOWN != getCallState(callId)) {
                        result.success(null)
                        return
                    }

                    val callType = arguments["call_type"] as Int
                    val callInitiatorId = arguments["caller_id"] as Int
                    val callInitiatorName = arguments["caller_name"] as String
                    val callOpponents = ArrayList((arguments["call_opponents"] as String)
                        .split(',')
                        .map { it.toInt() })
                    val userInfo = arguments["user_info"] as String

                    showCallNotification(
                        applicationContext!!,
                        callId,
                        callType,
                        callInitiatorId,
                        callInitiatorName,
                        callOpponents,
                        userInfo
                    )

                    saveCallState(callId, CALL_STATE_PENDING)
                    saveCallData(callId, arguments)
                    saveCallId(callId)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallAccepted" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    cancelCallNotification(applicationContext!!, callId)

                    saveCallState(callId, CALL_STATE_ACCEPTED)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "reportCallEnded" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    processCallEnded(callId)


                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getCallState" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    result.success(getCallState(callId))
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "setCallState" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String
                    val callState = arguments["call_state"] as String

                    saveCallState(callId, callState)

                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getCallData" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    result.success(getCallData(callId))
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "setOnLockScreenVisibility" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val isVisible = arguments["is_visible"] as Boolean

                    setOnLockScreenVisibility(isVisible)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "clearCallData" -> {
                try {
                    @Suppress("UNCHECKED_CAST") val arguments: Map<String, Any> =
                        call.arguments as Map<String, Any>
                    val callId = arguments["session_id"] as String

                    clearCallData(callId)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            "getLastCallId" -> {
                try {
                    result.success(getLastCallId())
                } catch (e: Exception) {
                    result.error("ERROR", e.message, "")
                }
            }

            else ->
                result.notImplemented()

        }
    }

    private fun registerCallStateReceiver() {
        localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext!!)
        val intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_CALL_REJECT)
        intentFilter.addAction(ACTION_CALL_ACCEPT)
        localBroadcastManager.registerReceiver(this, intentFilter)
    }

    private fun unRegisterCallStateReceiver() {
        localBroadcastManager.unregisterReceiver(this)
    }

    private fun setOnLockScreenVisibility(isVisible: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            mainActivity?.setShowWhenLocked(isVisible)
        } else {
            if (isVisible) {
                mainActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                mainActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = null
        channel.setMethodCallHandler(null)
        unRegisterCallStateReceiver()
    }

    override fun onNewIntent(intent: Intent?): Boolean {
        if (intent != null && intent.action != null && intent.action == ACTION_CALL_ACCEPT) {
            setOnLockScreenVisibility(true)
        }

        return false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || TextUtils.isEmpty(intent.action)) return

        val action: String? = intent.action
        if (ACTION_CALL_REJECT != action && ACTION_CALL_ACCEPT != action) {
            return
        }
        val callIdToProcess: String? = intent.getStringExtra(EXTRA_CALL_ID)
        if (TextUtils.isEmpty(callIdToProcess)) return

        val parameters = HashMap<String, Any?>()
        parameters["session_id"] = callIdToProcess
        parameters["call_type"] = intent.getIntExtra(EXTRA_CALL_TYPE, -1)
        parameters["caller_id"] = intent.getIntExtra(EXTRA_CALL_INITIATOR_ID, -1)
        parameters["caller_name"] = intent.getStringExtra(EXTRA_CALL_INITIATOR_NAME)
        parameters["call_opponents"] =
            intent.getIntegerArrayListExtra(EXTRA_CALL_OPPONENTS)?.joinToString(separator = ",")
        parameters["user_info"] = intent.getStringExtra(EXTRA_CALL_USER_INFO)

        when (action) {
            ACTION_CALL_REJECT -> {
                saveCallState(callIdToProcess!!, CALL_STATE_REJECTED)

                channel.invokeMethod("onCallRejected", parameters)
            }
            ACTION_CALL_ACCEPT -> {
                saveCallState(callIdToProcess!!, CALL_STATE_ACCEPTED)

                channel.invokeMethod("onCallAccepted", parameters)

                backToForeground(context)
            }
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        mainActivity = binding.activity
        val launchIntent = mainActivity?.intent

        if (launchIntent != null && launchIntent.action != null && launchIntent.action == ACTION_CALL_ACCEPT) {
            setOnLockScreenVisibility(true)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        mainActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        binding.addOnNewIntentListener(this)
        mainActivity = binding.activity
    }

    override fun onDetachedFromActivity() {
        mainActivity = null
    }

    private fun saveCallState(callId: String, callState: String) {
        if (applicationContext == null) return

        putString(applicationContext!!, callId + "_state", callState)
    }

    private fun getCallState(callId: String): String {
        if (applicationContext == null) return CALL_STATE_UNKNOWN

        val callState: String? = getString(applicationContext!!, callId + "_state")

        if (TextUtils.isEmpty(callState)) return CALL_STATE_UNKNOWN

        return callState!!
    }

    private fun getCallData(callId: String): Map<String, *>? {
        if (applicationContext == null) return null

        val callDataString: String? = getString(applicationContext!!, callId + "_data")

        if (TextUtils.isEmpty(callDataString)) return null

        return getMapFromJsonString(callDataString!!)
    }

    private fun saveCallData(callId: String, callData: Map<String, *>) {
        if (applicationContext == null) return

        try {
            putString(applicationContext!!, callId + "_data", mapToJsonString(callData))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun clearCallData(callId: String) {
        if (applicationContext == null) return

        try {
            remove(applicationContext!!, callId + "_state")
            remove(applicationContext!!, callId + "_data")
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveCallId(callId: String) {
        if (applicationContext == null) return

        try {
            putString(applicationContext!!, "last_call_id", callId)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getLastCallId(): String? {
        if (applicationContext == null) return null

        return getString(applicationContext!!, "last_call_id")
    }

    private fun processCallEnded(sessionId: String) {
        if (applicationContext == null) return

        saveCallState(sessionId, CALL_STATE_REJECTED)
        cancelCallNotification(applicationContext!!, sessionId)

        val broadcastIntent = Intent(ACTION_CALL_ENDED)
        val bundle = Bundle()
        bundle.putString(EXTRA_CALL_ID, sessionId)
        broadcastIntent.putExtras(bundle)
        localBroadcastManager.sendBroadcast(broadcastIntent)
    }

    private fun backToForeground(context: Context?) {
        if (context == null) return

        val packageName = context.packageName
        val focusIntent = context.packageManager.getLaunchIntentForPackage(packageName)!!
            .cloneFilter()
        val activity = mainActivity
        val isOpened = activity != null
        Log.d(TAG, "backToForeground, app isOpened ?" + if (isOpened) "true" else "false")
        if (isOpened) {
            focusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            focusIntent.action = ACTION_CALL_ACCEPT

            activity?.startActivity(focusIntent)
        } else {
            focusIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            focusIntent.action = ACTION_CALL_ACCEPT
            if (activity != null) {
                activity.startActivity(focusIntent)
            } else {
                context.startActivity(focusIntent)
            }
        }
    }
}
