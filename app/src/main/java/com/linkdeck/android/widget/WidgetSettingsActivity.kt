package com.linkdeck.android.widget

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.linkdeck.android.R
import com.linkdeck.android.core.intent.IntentSanitizer
import com.linkdeck.android.core.model.SanitizationResult
import com.linkdeck.android.ui.base.BaseActivity
import com.linkdeck.android.ui.chooser.ChooserActivity

class WidgetSettingsActivity : BaseActivity() {

    private val quickLinksStore by lazy { WidgetQuickLinksStore(this) }
    private lateinit var adapter: QuickLinksAdapter
    private lateinit var emptyLayout: View
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_widget_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.widgetSettingsRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupAddForm()
        setupList()
        refreshList()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.widgetSettingsToolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupAddForm() {
        val editUrl: TextInputEditText = findViewById(R.id.editWidgetUrl)
        val editTitle: TextInputEditText = findViewById(R.id.editWidgetTitle)
        val btnPaste: MaterialButton = findViewById(R.id.btnPasteClipboard)
        val btnAdd: MaterialButton = findViewById(R.id.btnAddQuickLink)

        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).coerceToText(this)?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    editUrl.setText(text)
                    Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnAdd.setOnClickListener {
            val url = editUrl.text?.toString()?.trim()
            if (url.isNullOrBlank()) {
                Toast.makeText(this, "Please enter a valid website or URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sanitized = IntentSanitizer.sanitizeUrl(url)
            if (sanitized !is SanitizationResult.Success) {
                Toast.makeText(this, "Invalid URL format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val title = editTitle.text?.toString()?.trim()
            val added = quickLinksStore.addQuickLink(sanitized.link.rawUrl, title)
            if (added) {
                editUrl.text?.clear()
                editTitle.text?.clear()
                refreshList()
                WidgetUpdateHelper.updateAllWidgets(this)
                Toast.makeText(this, "Quick link added to widget", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Maximum limit of ${WidgetQuickLinksStore.MAX_LINKS} links reached", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupList() {
        emptyLayout = findViewById(R.id.emptyQuickLinksLayout)
        recyclerView = findViewById(R.id.recyclerQuickLinks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = QuickLinksAdapter(
            onOpen = { link ->
                val intent = Intent(this, ChooserActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(link.url)
                }
                startActivity(intent)
            },
            onDelete = { link ->
                quickLinksStore.removeQuickLink(link.id)
                refreshList()
                WidgetUpdateHelper.updateAllWidgets(this)
                Toast.makeText(this, "Removed from widget", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.adapter = adapter
    }

    private fun refreshList() {
        val links = quickLinksStore.getQuickLinks()
        if (links.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter.submitList(links)
        }
    }

    private class QuickLinksAdapter(
        private val onOpen: (WidgetQuickLink) -> Unit,
        private val onDelete: (WidgetQuickLink) -> Unit
    ) : RecyclerView.Adapter<QuickLinksAdapter.ViewHolder>() {

        private val items = mutableListOf<WidgetQuickLink>()

        fun submitList(newItems: List<WidgetQuickLink>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget_quick_link, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textTitle: TextView = itemView.findViewById(R.id.textQuickLinkTitle)
            private val textUrl: TextView = itemView.findViewById(R.id.textQuickLinkUrl)
            private val btnOpen: MaterialButton = itemView.findViewById(R.id.btnOpenQuickLink)
            private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteQuickLink)

            fun bind(link: WidgetQuickLink) {
                textTitle.text = link.title
                textUrl.text = link.url

                btnOpen.setOnClickListener { onOpen(link) }
                btnDelete.setOnClickListener { onDelete(link) }
            }
        }
    }
}
