package com.swaroopsingh.qrreward.ui.fragments

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.swaroopsingh.qrreward.R

data class ScanHistoryItem(val data: String, val time: String, val type: String)

class HistoryFragment : Fragment() {

    private val history = mutableListOf<ScanHistoryItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(R.id.list_history)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)

        // Load from SharedPreferences
        val prefs = requireContext().getSharedPreferences("scan_history", Context.MODE_PRIVATE)
        val historySet = prefs.getStringSet("history", emptySet()) ?: emptySet()
        history.clear()
        historySet.forEach { item ->
            val parts = item.split("|||")
            if (parts.size >= 3) history.add(ScanHistoryItem(parts[0], parts[1], parts[2]))
        }
        history.sortByDescending { it.time }

        if (history.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            listView.visibility = View.VISIBLE
            val adapter = object : ArrayAdapter<ScanHistoryItem>(requireContext(), R.layout.item_history, history) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = convertView ?: layoutInflater.inflate(R.layout.item_history, parent, false)
                    val item = getItem(position)!!
                    v.findViewById<TextView>(R.id.tv_history_data).text = item.data.take(50)
                    v.findViewById<TextView>(R.id.tv_history_time).text = item.time
                    v.findViewById<TextView>(R.id.tv_history_type).text = item.type
                    return v
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, position, _ ->
                val item = history[position]
                // Open URL if it's a link
                if (item.data.startsWith("http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.data)))
                } else {
                    // Copy to clipboard
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Data", item.data))
                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun saveToHistory(context: Context, data: String, type: String) {
            val prefs = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE)
            val existing = prefs.getStringSet("history", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            val time = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            existing.add("$data|||$time|||$type")
            if (existing.size > 100) existing.remove(existing.first()) // Keep last 100
            prefs.edit().putStringSet("history", existing).apply()
        }
    }
}
