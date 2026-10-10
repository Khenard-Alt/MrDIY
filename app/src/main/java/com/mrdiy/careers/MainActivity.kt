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
                R.id.emailVerificationFragment -> false
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
}
