package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.core.content.ContextCompat
import me.magnum.melonds.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import me.magnum.melonds.ui.settings.SettingsActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView

abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (activity !is SettingsActivity) return
        setDivider(null)
        fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
        val density = resources.displayMetrics.density
        val horizontal = (24 * density).toInt()
        val gap = (12 * density).toInt()
        listView.setBackgroundColor(color(R.color.glass_background))
        listView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.set(horizontal, 0, horizontal, gap)
            }
        })
        fun styleRow(row: View) {
            row.findViewById<ImageView>(android.R.id.icon)?.imageTintList = ColorStateList.valueOf(color(R.color.glass_accent))
            val shape = GradientDrawable().apply {
                setColor(color(R.color.glass_surface))
                cornerRadius = 18 * density
            }
            row.background = RippleDrawable(ColorStateList.valueOf(color(R.color.glass_ripple)), shape, null)
            row.findViewById<TextView>(android.R.id.title)?.apply {
                setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(color(R.color.glass_text), 128), color(R.color.glass_text)),
                ))
                textSize = 16f
            }
            row.findViewById<TextView>(android.R.id.summary)?.apply {
                setTextColor(ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(color(R.color.glass_secondary), 128), color(R.color.glass_secondary)),
                ))
                textSize = 13f
            }
        }
        listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) { styleRow(view) }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        for (i in 0 until listView.childCount) styleRow(listView.getChildAt(i))
    }

    override fun onCreateRecyclerView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): RecyclerView {
        return super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
            clipToPadding = false

            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.updatePadding(bottom = insets.bottom)

                WindowInsetsCompat.CONSUMED
            }
        }
    }
}