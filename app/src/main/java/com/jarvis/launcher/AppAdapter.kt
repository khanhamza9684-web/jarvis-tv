package com.jarvis.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * tvOS-style row: tiles stay flat until focused, then lift, scale up, gain a soft
 * highlight and reveal their name underneath.
 */
class AppAdapter(
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit,
    private val onFocused: (View, AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    fun items(): List<AppEntry> = items

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: View = view.findViewById(R.id.tile)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val shine: View = view.findViewById(R.id.shine)
        private val label: TextView = view.findViewById(R.id.label)
        private val density = view.resources.displayMetrics.density
        private val interpolator = DecelerateInterpolator()
        private var current: AppEntry? = null

        init {
            view.setOnFocusChangeListener { v, focused ->
                applyFocus(focused, animate = true)
                if (focused) current?.let { onFocused(v, it) }
            }
        }

        fun bind(entry: AppEntry) {
            current = entry
            label.text = entry.label
            if (entry.banner != null) {
                banner.setImageDrawable(entry.banner)
                banner.visibility = View.VISIBLE
                icon.visibility = View.GONE
            } else {
                banner.setImageDrawable(null)
                banner.visibility = View.GONE
                icon.setImageDrawable(entry.icon)
                icon.visibility = View.VISIBLE
            }
            itemView.setOnClickListener { onClick(entry) }
            itemView.setOnLongClickListener {
                onLongClick(entry)
                true
            }
            applyFocus(itemView.hasFocus(), animate = false)
        }

        private fun applyFocus(focused: Boolean, animate: Boolean) {
            val scale = if (focused) 1.14f else 1f
            val lift = if (focused) 20f * density else 0f
            val alpha = if (focused) 1f else 0f
            val labelShift = if (focused) 0f else -6f * density

            if (animate) {
                tile.animate().scaleX(scale).scaleY(scale).translationZ(lift)
                    .setDuration(200).setInterpolator(interpolator).start()
                shine.animate().alpha(alpha).setDuration(200).start()
                label.animate().alpha(alpha).translationY(labelShift)
                    .setDuration(200).setInterpolator(interpolator).start()
            } else {
                tile.scaleX = scale
                tile.scaleY = scale
                tile.translationZ = lift
                shine.alpha = alpha
                label.alpha = alpha
                label.translationY = labelShift
            }
        }
    }
}
