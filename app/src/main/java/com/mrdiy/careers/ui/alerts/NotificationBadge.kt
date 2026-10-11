package com.mrdiy.careers.ui.alerts

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mrdiy.careers.data.repository.InboxRepository
import kotlinx.coroutines.launch

/** Shows the red dot on the bell while the signed-in user has unread notifications. */
fun Fragment.refreshNotificationBadge(badge: View) {
    viewLifecycleOwner.lifecycleScope.launch {
        val context = context ?: return@launch
        val unread = InboxRepository(context).unreadAlertCount()
        badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
    }
}
