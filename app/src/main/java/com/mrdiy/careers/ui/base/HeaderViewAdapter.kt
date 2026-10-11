package com.mrdiy.careers.ui.base

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Shows an already-inflated layout section as the first row of a RecyclerView, so the section
 * scrolls away with the list. Use this instead of putting a RecyclerView inside a
 * NestedScrollView, which makes RecyclerView inflate every item at once and freezes the UI
 * on long lists. The header keeps its view-binding references because the same View is reused.
 */
class HeaderViewAdapter(private val header: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = 1

    override fun getItemViewType(position: Int) = VIEW_TYPE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        (header.parent as? ViewGroup)?.removeView(header)
        header.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return object : RecyclerView.ViewHolder(header) {}.apply { setIsRecyclable(false) }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

    companion object {
        private val VIEW_TYPE = View.generateViewId()
    }
}

/**
 * Puts [header] above [content] in this RecyclerView and insets only the content rows by
 * [contentPaddingDp] on each side (the header keeps its own padding).
 */
fun RecyclerView.setAdapterWithHeader(
    header: View,
    content: RecyclerView.Adapter<*>,
    contentPaddingDp: Int = 16
) {
    val inset = (contentPaddingDp * resources.displayMetrics.density).toInt()
    layoutManager = layoutManager ?: LinearLayoutManager(context)
    adapter = ConcatAdapter(HeaderViewAdapter(header), content)
    addItemDecoration(object : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (parent.getChildAdapterPosition(view) > 0) outRect.set(inset, 0, inset, 0)
        }
    })
}
