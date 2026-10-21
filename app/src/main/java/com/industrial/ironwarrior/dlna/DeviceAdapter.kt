package com.industrial.ironwarrior.dlna

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adaptador para la lista de dispositivos DLNA encontrados.
 * NOTA: esta clase no venía en el export de la IA (MainActivity la
 * usaba pero nunca fue generada) — se agregó para que el proyecto compile.
 */
class DeviceAdapter(
    private val devices: List<DlnaDevice>,
    private val onDeviceClick: (DlnaDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtDeviceName)
        val txtUrl: TextView = view.findViewById(R.id.txtDeviceUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.txtName.text = device.name
        val tag = when (device.type) {
            DlnaDeviceType.SERVER -> "[SERVIDOR]"
            DlnaDeviceType.RENDERER -> "[RECEPTOR / CAST]"
            DlnaDeviceType.UNKNOWN -> "[DESCONOCIDO]"
        }
        holder.txtUrl.text = "$tag ${device.locationUrl}"
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount(): Int = devices.size
}
