package com.example.jonim

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onClick: (DeviceModel) -> Unit
) : ListAdapter<DeviceModel, DeviceAdapter.DeviceViewHolder>(DiffCallback()) {

    class DeviceViewHolder(itemView: View, val onClick: (DeviceModel) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.deviceName)
        private val address: TextView = itemView.findViewById(R.id.deviceAddress)
        private var currentDevice: DeviceModel? = null

        init {
            itemView.setOnClickListener {
                currentDevice?.let { onClick(it) }
            }
        }

        fun bind(device: DeviceModel) {
            currentDevice = device
            name.text = device.name
            address.text = "${device.address} (RSSI: ${device.rssi})"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<DeviceModel>() {
        override fun areItemsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: DeviceModel, newItem: DeviceModel): Boolean {
            return oldItem == newItem
        }
    }
}
