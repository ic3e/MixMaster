package com.conwic.pourday.game

import android.Manifest
import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject

/**
 * Pour Day with co-workers, phone to phone, with nobody's server in between: Google's Nearby
 * Connections finds the other phones over Bluetooth and talks to them over Wi-Fi Direct or
 * Bluetooth, whichever is there, with or without a signal on site. One phone hosts the day and up
 * to three others join it.
 *
 * The page plays the game; this only carries its messages — short JSON strings — and says who
 * turned up, who connected and who left. Everything it hears goes to the page as an event.
 * Called and calling back on the main thread only.
 */
internal class GameNet(context: Context) {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val peers = LinkedHashMap<String, String>()
    private val pending = HashMap<String, String>()
    private var myName = "Worker"
    private var hosting = false

    /** Where events go: set once the page is there. */
    var emit: (String) -> Unit = {}

    fun host(name: String) {
        stop()
        myName = name
        hosting = true
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        client.startAdvertising(name, SERVICE_ID, lifecycle, options)
            .addOnSuccessListener { event("hosting") }
            .addOnFailureListener { failed("host", it) }
    }

    fun join(name: String) {
        stop()
        myName = name
        hosting = false
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        client.startDiscovery(SERVICE_ID, discovery, options)
            .addOnSuccessListener { event("searching") }
            .addOnFailureListener { failed("join", it) }
    }

    fun connect(endpointId: String) {
        client.requestConnection(myName, endpointId, lifecycle)
            .addOnFailureListener { failed("connect", it) }
    }

    /** To everyone connected: the host's co-workers, or a co-worker's host. */
    fun send(data: String) {
        if (peers.isEmpty()) return
        client.sendPayload(peers.keys.toList(), Payload.fromBytes(data.toByteArray(Charsets.UTF_8)))
    }

    fun sendTo(endpointId: String, data: String) {
        if (endpointId !in peers) return
        client.sendPayload(endpointId, Payload.fromBytes(data.toByteArray(Charsets.UTF_8)))
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peers.clear()
        pending.clear()
    }

    fun denied() = event("denied")

    private fun event(type: String, id: String? = null, name: String? = null, data: String? = null) {
        val o = JSONObject().put("t", type)
        if (id != null) o.put("id", id)
        if (name != null) o.put("name", name)
        if (data != null) o.put("data", data)
        emit(o.toString())
    }

    private fun failed(what: String, e: Exception) {
        emit(JSONObject().put("t", "error").put("what", what).put("text", e.message ?: e.javaClass.simpleName).toString())
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) event("found", endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) = event("lost", endpointId)
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // a day takes four: the host and three co-workers — counting those still being let in,
            // or four tapping Join at once would all get a place
            if (hosting && peers.size + pending.size >= 3) {
                client.rejectConnection(endpointId)
                return
            }
            pending[endpointId] = info.endpointName
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val name = pending.remove(endpointId) ?: "?"
            if (result.status.isSuccess) {
                peers[endpointId] = name
                if (!hosting) client.stopDiscovery()
                event("connected", endpointId, name)
            } else {
                event("failed", endpointId, name)
            }
        }

        override fun onDisconnected(endpointId: String) {
            peers.remove(endpointId)
            event("disconnected", endpointId)
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            event("msg", endpointId, data = String(bytes, Charsets.UTF_8))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    companion object {
        private const val SERVICE_ID = "com.conwic.mixmaster.pourday"

        /** What Nearby needs the person to allow on this version of Android: "Nearby devices", or location before 13. */
        fun permissions(): Array<String> {
            val list = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 31) {
                list += Manifest.permission.BLUETOOTH_SCAN
                list += Manifest.permission.BLUETOOTH_ADVERTISE
                list += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (Build.VERSION.SDK_INT >= 33) {
                list += Manifest.permission.NEARBY_WIFI_DEVICES
            } else {
                // Android 12 ignores a request for precise location that doesn't ask for approximate too
                list += Manifest.permission.ACCESS_COARSE_LOCATION
                list += Manifest.permission.ACCESS_FINE_LOCATION
            }
            return list.toTypedArray()
        }
    }
}
