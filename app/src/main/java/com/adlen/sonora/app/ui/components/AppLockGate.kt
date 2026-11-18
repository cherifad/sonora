// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adlen.sonora.app.data.preferences.LockPref
import com.adlen.sonora.app.data.preferences.LockRule
import com.adlen.sonora.app.data.preferences.LockSession
import com.adlen.sonora.engine.R

/**
 * The lock the user asked for: when the app comes back to the front after being left, a screen
 * of its own asks for the phone's lock before anything is shown.
 *
 * The app stays composed underneath, so the queue, the player and the current screen are exactly
 * where they were once the prompt succeeds. The prompt is the system's, with the phone's own PIN
 * or pattern as the fallback, so a phone with no fingerprint still works and no secret is stored
 * here.
 */
@Composable
fun AppLockGate(active: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val enabled = LockPref.enabled
    val secure = remember { LockPref.deviceLocked(context) }
    var leftApp by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // The prompt itself stops the activity, and so does a rotation: neither is the
                // user leaving the app.
                Lifecycle.Event.ON_STOP ->
                    if (!prompting && activity?.isChangingConfigurations != true) leftApp = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A cold start is locked too: LockSession starts locked, and only a successful prompt opens
    // this process. A recreation (rotation, theme) keeps the session it had.
    val locked = active && LockRule.wanted(enabled, secure, leftApp || !LockSession.unlocked)
    Box(Modifier.fillMaxSize()) {
        content()
        if (locked) {
            LockScreen(
                onUnlock = {
                    val activity = context as? FragmentActivity
                    if (activity == null) {
                        leftApp = false
                        return@LockScreen
                    }
                    prompting = true
                    promptForDeviceLock(activity,
                        onSuccess = {
                            prompting = false
                            leftApp = false
                            LockSession.unlocked = true
                        },
                        onCancel = { prompting = false },
                    )
                },
            )
        }
    }
}

/** The phone's own lock, with the PIN or pattern as the fallback. */
private fun promptForDeviceLock(activity: FragmentActivity, onSuccess: () -> Unit, onCancel: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancel()
            override fun onAuthenticationFailed() {
                // A finger that did not match: the prompt stays up, the user tries again.
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_prompt_title))
        .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.app_mark),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.lock_screen_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.lock_screen_detail),
            color = Color(0xFFB3B3B3),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.adlen.sonora.app.ui.theme.AppAccent,
                contentColor = Color.Black,
            ),
        ) {
            Text(stringResource(R.string.lock_unlock), fontWeight = FontWeight.Bold)
        }
    }
}
