package com.example.mux.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.mux.data.collector.DeviceStateEvents
import com.example.mux.data.model.DeviceStateTrigger

/**
 * Translates system broadcasts into device-state trigger strings.
 *
 * This receiver performs no hardware reads and no networking. It only maps an
 * incoming broadcast to a trigger on [DeviceStateEvents]; the collector
 * debounces the burst and takes a single snapshot. Publishing an event is
 * intentionally cheap to keep the event-driven path battery-friendly.
 */
class DeviceStateBroadcastReceiver(
    private val events: DeviceStateEvents,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> DeviceStateTrigger.AIRPLANE_MODE
            Intent.ACTION_BATTERY_CHANGED -> DeviceStateTrigger.BATTERY
            Intent.ACTION_HEADSET_PLUG -> DeviceStateTrigger.HEADSET
            "android.media.VOLUME_CHANGED_ACTION",
            "android.media.RINGER_MODE_CHANGED" -> DeviceStateTrigger.VOLUME
            "android.bluetooth.adapter.action.STATE_CHANGED" -> DeviceStateTrigger.BLUETOOTH
            "android.net.conn.CONNECTIVITY_CHANGE" -> DeviceStateTrigger.CONNECTIVITY
            else -> null
        }
        trigger?.let { events.publish(it) }
    }

    companion object {
        /**
         * Broadcast actions that should wake the device-state collector. Kept
         * explicit rather than a package-wide filter to minimize registration
         * cost and avoid reacting to unrelated system chatter.
         */
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.RINGER_MODE_CHANGED")
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
        }
    }
}
