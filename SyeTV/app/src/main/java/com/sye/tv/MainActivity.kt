package com.sye.tv

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sye.tv.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val items = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        items.addAll(LinkStorage.load(this))
        adapter = MediaAdapter(
            items,
            onPlay   = { openPlayer(it) },
            onDelete = { deleteItem(it) }
        )
        binding.rvMedia.layoutManager = GridLayoutManager(this, 4)
        binding.rvMedia.adapter = adapter
        refresh()

        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnAdd.setOnFocusChangeListener { v, focused ->
            (v as TextView).setTextColor(
                if (focused) getColor(R.color.black) else getColor(R.color.text_secondary)
            )
        }
    }

    private fun openPlayer(item: MediaItem) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL,   item.url)
            putExtra(PlayerActivity.EXTRA_TITLE, item.title)
            putExtra(PlayerActivity.EXTRA_DRIVE, DriveHelper.isDrive(item.url))
        })
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_link, null)
        val etUrl   = view.findViewById<EditText>(R.id.etUrl)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val btnAdd  = view.findViewById<View>(R.id.btnConfirmAdd)
        val btnCancel = view.findViewById<View>(R.id.btnCancel)

        val dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setView(view)
            .create()

        btnConfirm@ btnAdd.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) return@btnConfirm

            val rawTitle = etTitle.text.toString().trim()
            val title = rawTitle.ifEmpty {
                when {
                    DriveHelper.isDrive(url) -> "Drive · ${DriveHelper.fileId(url)?.take(8) ?: "video"}"
                    url.length > 50 -> url.take(48) + "…"
                    else -> url
                }
            }
            val item = MediaItem(
                id    = System.currentTimeMillis().toString(),
                title = title,
                url   = url
            )
            items.add(item)
            adapter.notifyItemInserted(items.size - 1)
            LinkStorage.save(this, items)
            refresh()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun deleteItem(item: MediaItem) {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setMessage("Remove \"${item.title}\"?")
            .setPositiveButton("Remove") { _, _ ->
                val idx = items.indexOf(item)
                if (idx >= 0) {
                    items.removeAt(idx)
                    adapter.notifyItemRemoved(idx)
                    LinkStorage.save(this, items)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        binding.tvEmpty.visibility  = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMedia.visibility  = if (items.isEmpty()) View.GONE   else View.VISIBLE
    }
}

/* ──────────────────────────── Adapter ──────────────────────────── */

class MediaAdapter(
    private val items: List<MediaItem>,
    private val onPlay: (MediaItem) -> Unit,
    private val onDelete: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle:  TextView = view.findViewById(R.id.tvMediaTitle)
        val tvSource: TextView = view.findViewById(R.id.tvMediaSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvTitle.text = item.title
        h.tvSource.text = when {
            DriveHelper.isDrive(item.url) -> "Google Drive"
            item.url.startsWith("rtsp")   -> "Stream · RTSP"
            item.url.endsWith(".m3u8")    -> "HLS Stream"
            item.url.endsWith(".mpd")     -> "DASH Stream"
            else -> item.url.take(32)
        }

        h.itemView.setOnClickListener { onPlay(item) }

        h.itemView.setOnLongClickListener {
            onDelete(item)
            true
        }

        h.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> { onPlay(item); true }
                    KeyEvent.KEYCODE_DEL   -> { onDelete(item); true }
                    else -> false
                }
            } else false
        }
    }
}
