package com.example.jonim

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter : ListAdapter<DeviceModel, DeviceAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.deviceName)
        val address: TextView = itemView.findViewById(R.id.deviceAddress)
        val rssi: TextView = itemView.findViewById(R.id.deviceRssi)
    }

    class DiffCallback : DiffUtil.ItemCallback<DeviceModel>() {
        override fun areItemsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean =
            oldItem.address == newItem.address

        override fun areContentsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = getItem(position)
        holder.name.text = device.name
        holder.address.text = device.address
        holder.rssi.text = "RSSI: ${device.rssi}"
    }
}
