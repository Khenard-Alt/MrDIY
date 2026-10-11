package com.mrdiy.careers

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import com.mrdiy.careers.notifications.AppNotificationManager
import com.mrdiy.careers.notifications.AlertCheckWorker
import com.mrdiy.careers.notifications.AlertNotifier
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.mrdiy.careers.notifications.PushTokenRegistrar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppNotificationManager.createChannel(this)
        requestNotificationPermissionIfNeeded()
        scheduleAlertChecks()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            binding.bottomNavigation.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val publicScreens = setOf(R.id.loginFragment, R.id.registerFragment,
                R.id.phoneLoginFragment, R.id.emailVerificationFragment)
            if (destination.id !in publicScreens && !AuthManager(this).isLoggedIn) {
                lifecycleScope.launch {
                    if (!AuthManager(this@MainActivity).restoreSession() &&
                        navController.currentDestination?.id !in publicScreens) {
                        navController.navigate(R.id.loginFragment, null, navOptions {
                            popUpTo(R.id.nav_graph) { inclusive = true }
                        })
                    }
                }
            }
            binding.bottomNavigation.isVisible = when (destination.id) {
                R.id.loginFragment,
                R.id.registerFragment,
                R.id.phoneLoginFragment,
                R.id.welcomeFragment,
                R.id.emailVerificationFragment,
                R.id.chatFragment -> false
                else                           -> true
            }
        }

        handleDeepLink(intent)
        handleNotificationIntent(intent)
        lifecycleScope.launch {
            SupabaseProvider.client.auth.awaitInitialization()
            PushTokenRegistrar.registerCurrentUser(this@MainActivity)
            SupabaseProvider.client.auth.sessionStatus.collect { status ->
                PushTokenRegistrar.registerCurrentUser(this@MainActivity)
                if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated) {
                    startAlertRealtime()
                    runCatching { AlertNotifier.checkAndNotify(this@MainActivity) }
                } else {
                    alertRealtimeJob?.cancel()
                    alertRealtimeJob = null
                }
                val publicScreens = setOf(R.id.loginFragment, R.id.registerFragment,
                    R.id.phoneLoginFragment, R.id.emailVerificationFragment)
                if (status is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated &&
                    navController.currentDestination?.id !in publicScreens) {
                    navController.navigate(R.id.loginFragment, null, navOptions {
                        popUpTo(R.id.nav_graph) { inclusive = true }
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleNotificationIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppNotificationManager.EXTRA_OPEN_ALERTS, false) == true &&
            AuthManager(this).isLoggedIn &&
            navController.currentDestination?.id != R.id.alertsFragment
        ) {
            navController.navigate(R.id.alertsFragment)
            intent.removeExtra(AppNotificationManager.EXTRA_OPEN_ALERTS)
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        intent ?: return
        if (intent.data?.scheme == "mrdiy" && intent.data?.host == "login-callback") {
            lifecycleScope.launch {
                try {
                    val auth = SupabaseProvider.client.auth
                    auth.awaitInitialization()
                    // Import inside this coroutine: handleDeeplinks launches a separate scope,
                    // which previously raced refresh/navigation and escaped our error handler.
                    val uri = requireNotNull(intent.data)
                    val code = uri.getQueryParameter("code")
                    if (code != null) auth.exchangeCodeForSession(code)
                    else {
                        val session = auth.parseSessionFromFragment(requireNotNull(uri.fragment))
                        val user = auth.retrieveUser(session.accessToken)
                        auth.importSession(session.copy(user = user))
                    }
                    if (AuthManager(this@MainActivity).restoreSession()) {
                        // Login routes verified sessions through profile loading/onboarding.
                        navController.navigate(R.id.loginFragment, null, navOptions {
                            popUpTo(R.id.nav_graph) { inclusive = true }
                        })
                    }
                } catch (e: Exception) {
                    com.mrdiy.careers.data.SafeDiagnostics.record("auth_callback", e)
                    android.widget.Toast.makeText(this@MainActivity, "Could not verify this link. Please sign in or request a new link.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun markAlertsRead() { }

    private var alertRealtimeJob: kotlinx.coroutines.Job? = null

    /** Checks for new notifications about every 15 minutes, even when the app is closed. */
    private fun scheduleAlertChecks() {
        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("alert_checks", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** While the app is running, a new row in `alerts` shows up in the phone's tray immediately. */
    private fun startAlertRealtime() {
        if (alertRealtimeJob?.isActive == true) return
        alertRealtimeJob = lifecycleScope.launch {
            try {
                val realtime = SupabaseProvider.client.pluginManager.getPlugin(Realtime)
                val channel = realtime.channel("alerts_${UUID.randomUUID()}")
                val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "alerts"
                }
                channel.subscribe()
                try {
                    inserts.collect { AlertNotifier.checkAndNotify(this@MainActivity) }
                } finally {
                    withContext(NonCancellable) { runCatching { channel.unsubscribe() } }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                com.mrdiy.careers.data.SafeDiagnostics.record("alerts_realtime", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AuthManager(this).isLoggedIn) {
            lifecycleScope.launch { runCatching { AlertNotifier.checkAndNotify(this@MainActivity) } }
        }
    }
}
