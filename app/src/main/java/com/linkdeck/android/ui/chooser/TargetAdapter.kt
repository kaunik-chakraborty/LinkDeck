package com.linkdeck.android.ui.chooser

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.linkdeck.android.R
import com.linkdeck.android.core.model.AppTarget
import com.linkdeck.android.core.model.ShareTarget
import com.linkdeck.android.core.model.TargetCategory
import com.linkdeck.android.core.preference.PinnedShareTargetStore

/**
 * Displays categorized target destinations (Dedicated Apps, Browsers, and Share targets)
 * in the chooser bottom sheet.
 *
 * Supports pinning favorite share targets to the top of the "Share with" section.
 */
class TargetAdapter(
    private val context: Context,
    private val packageManager: PackageManager,
    private val pinnedStore: PinnedShareTargetStore? = null,
    var allowRememberChoices: Boolean = true,
    private val onOpenTargetLaunch: (AppTarget, Boolean) -> Unit,
    private val onShareTargetClicked: (ShareTarget) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Header(val title: String) : Item()
        data class Notice(val message: String) : Item()
        data class Open(val appTarget: AppTarget) : Item()
        data class Share(val shareTarget: ShareTarget, val isPinned: Boolean) : Item()
    }

    private val items = mutableListOf<Item>()
    private val iconCache = mutableMapOf<String, Drawable>()
    private var lastOpenTargets: List<AppTarget> = emptyList()
    private var lastShareTargets: List<ShareTarget> = emptyList()

    var selectedOpenTarget: AppTarget? = null
        private set

    fun submitData(openTargets: List<AppTarget>, shareTargets: List<ShareTarget>) {
        this.lastOpenTargets = openTargets
        this.lastShareTargets = shareTargets
        rebuildItems()
    }

    private fun rebuildItems() {
        items.clear()

        val dedicated = lastOpenTargets.filter { it.category == TargetCategory.RECOMMENDED }
        val browsers = lastOpenTargets.filter { it.category == TargetCategory.BROWSER }
        val others = lastOpenTargets.filter { it.category == TargetCategory.OTHER }

        if (dedicated.isNotEmpty()) {
            items.add(Item.Header(context.getString(R.string.category_recommended)))
            dedicated.forEach { items.add(Item.Open(it)) }
        }

        if (browsers.isNotEmpty()) {
            items.add(Item.Header(context.getString(R.string.category_browsers)))
            browsers.forEach { items.add(Item.Open(it)) }
        }

        if (others.isNotEmpty()) {
            items.add(Item.Header(context.getString(R.string.category_other)))
            others.forEach { items.add(Item.Open(it)) }
        }

        if (lastOpenTargets.isEmpty() && lastShareTargets.isNotEmpty()) {
            items.add(Item.Notice(context.getString(R.string.empty_open_targets_banner)))
        }

        if (lastShareTargets.isNotEmpty()) {
            items.add(Item.Header(context.getString(R.string.category_share_with)))

            val pinnedSignatures = pinnedStore?.getPinnedTargets() ?: emptySet()
            val sortedShareTargets = lastShareTargets.sortedWith { a, b ->
                val aPinned = pinnedSignatures.contains("${a.packageName}/${a.activityName}")
                val bPinned = pinnedSignatures.contains("${b.packageName}/${b.activityName}")
                when {
                    aPinned && !bPinned -> -1
                    !aPinned && bPinned -> 1
                    else -> a.appLabel.lowercase().compareTo(b.appLabel.lowercase())
                }
            }

            sortedShareTargets.forEach { shareTarget ->
                val isPinned = pinnedSignatures.contains("${shareTarget.packageName}/${shareTarget.activityName}")
                items.add(Item.Share(shareTarget, isPinned))
            }
        }

        notifyDataSetChanged()
    }

    fun setSelectedOpenTarget(target: AppTarget?) {
        selectedOpenTarget = target
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Item.Header -> VIEW_TYPE_HEADER
            is Item.Notice -> VIEW_TYPE_NOTICE
            is Item.Open -> VIEW_TYPE_OPEN
            is Item.Share -> VIEW_TYPE_SHARE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER, VIEW_TYPE_NOTICE -> {
                val view = inflater.inflate(R.layout.item_category_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_target, parent, false)
                TargetViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).bind(item.title)
            is Item.Notice -> (holder as HeaderViewHolder).bind(item.message)
            is Item.Open -> (holder as TargetViewHolder).bindOpen(item.appTarget)
            is Item.Share -> (holder as TargetViewHolder).bindShare(item.shareTarget, item.isPinned)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.categoryHeaderTitle)

        fun bind(title: String) {
            titleView.text = title
        }
    }

    inner class TargetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)
        private val subtitleView: TextView = itemView.findViewById(R.id.appSubtitle)
        private val btnPinView: ImageButton = itemView.findViewById(R.id.btnPinTarget)
        private val layoutInlineActions: View = itemView.findViewById(R.id.layoutInlineActions)
        private val btnInlineJustOnce: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnInlineJustOnce)
        private val btnInlineAlways: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnInlineAlways)

        fun bindOpen(target: AppTarget) {
            labelView.text = target.appLabel
            val subtitle = when {
                target.isNativeMatch -> "Matches this link"
                target.isBrowser -> "Web browser"
                else -> ""
            }
            subtitleView.text = subtitle
            subtitleView.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE

            loadIcon(target.packageName, target.activityName, iconView)

            val isSelected = (target == selectedOpenTarget)
            itemView.isActivated = isSelected
            itemView.isSelected = isSelected
            itemView.contentDescription = if (isSelected) {
                "${target.appLabel}, selected"
            } else {
                target.appLabel
            }
            btnPinView.visibility = View.GONE

            layoutInlineActions.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                btnInlineAlways.visibility = if (allowRememberChoices) View.VISIBLE else View.GONE
                btnInlineJustOnce.text = context.getString(
                    if (allowRememberChoices) R.string.action_just_once else R.string.action_open
                )
                btnInlineJustOnce.setOnClickListener { onOpenTargetLaunch(target, false) }
                btnInlineAlways.setOnClickListener { onOpenTargetLaunch(target, true) }
            }

            itemView.setOnClickListener {
                if (!allowRememberChoices || selectedOpenTarget == target) {
                    onOpenTargetLaunch(target, false)
                } else {
                    val prevTarget = selectedOpenTarget
                    selectedOpenTarget = target
                    val prevPos = items.indexOfFirst { it is Item.Open && it.appTarget == prevTarget }
                    val newPos = items.indexOfFirst { it is Item.Open && it.appTarget == target }
                    if (prevPos != -1) notifyItemChanged(prevPos)
                    if (newPos != -1) notifyItemChanged(newPos)
                }
            }
        }

        fun bindShare(target: ShareTarget, isPinned: Boolean) {
            labelView.text = target.appLabel
            itemView.contentDescription = "${target.appLabel}, share target"
            subtitleView.text = if (isPinned) "Pinned" else "Share link"
            subtitleView.visibility = View.VISIBLE
            layoutInlineActions.visibility = View.GONE

            loadIcon(target.packageName, target.activityName, iconView)

            itemView.isActivated = false
            itemView.isSelected = false
            btnPinView.visibility = View.VISIBLE

            val primaryColor = getThemeColor(context, com.google.android.material.R.attr.colorPrimary)
            val mutedColor = getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)

            if (isPinned) {
                btnPinView.setImageResource(R.drawable.pin_filled)
                btnPinView.imageTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                btnPinView.contentDescription = context.getString(R.string.action_unpin_app)
            } else {
                btnPinView.setImageResource(R.drawable.pin)
                btnPinView.imageTintList = android.content.res.ColorStateList.valueOf(mutedColor)
                btnPinView.contentDescription = context.getString(R.string.action_pin_app)
            }

            btnPinView.setOnClickListener {
                val signature = "${target.packageName}/${target.activityName}"
                pinnedStore?.togglePin(signature)
                rebuildItems()
            }

            itemView.setOnClickListener {
                onShareTargetClicked(target)
            }
        }
    }

    private fun getThemeColor(context: Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun loadIcon(packageName: String, activityName: String, imageView: ImageView) {
        val cacheKey = "$packageName/$activityName"
        val cached = iconCache[cacheKey]
        if (cached != null) {
            imageView.setImageDrawable(cached)
            return
        }

        try {
            val component = ComponentName(packageName, activityName)
            val icon = packageManager.getActivityIcon(component)
            iconCache[cacheKey] = icon
            imageView.setImageDrawable(icon)
        } catch (e: Exception) {
            try {
                val appIcon = packageManager.getApplicationIcon(packageName)
                iconCache[cacheKey] = appIcon
                imageView.setImageDrawable(appIcon)
            } catch (e2: Exception) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(imageView.context, android.R.drawable.sym_def_app_icon)
                )
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_NOTICE = 1
        private const val VIEW_TYPE_OPEN = 2
        private const val VIEW_TYPE_SHARE = 3
    }
}
