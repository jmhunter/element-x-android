/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumble.appyx.core.integrationpoint.NodeActivity
import io.element.android.appnav.bubble.BubbleFlowNode
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.lockscreen.api.LockScreenLockState
import io.element.android.features.lockscreen.api.handleSecureFlag
import io.element.android.libraries.architecture.NodeFactoriesBindings
import io.element.android.libraries.architecture.appyx.DebugNavStateNodeHost
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.deeplink.api.DeeplinkData
import io.element.android.libraries.designsystem.theme.ElementThemeApp
import io.element.android.libraries.designsystem.utils.snackbar.LocalSnackbarDispatcher
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.x.di.AppBindings
import io.element.android.x.intent.SafeUriHandler
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Activity displayed in a notification bubble, to chat in a room on top of other applications.
 * See https://developer.android.com/develop/ui/views/notifications/bubbles
 *
 * It is started with a room deep link, and only displays this room, see [BubbleFlowNode].
 * Unlike [MainActivity], it is not `singleTask`: the system creates one instance per bubble, embedded in the bubble's own task.
 */
class BubbleActivity : NodeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appBindings: AppBindings = bindings()
        val deeplinkData = appBindings.deeplinkParser().getFromIntent(intent) as? DeeplinkData.Room
        if (deeplinkData == null) {
            Timber.w("BubbleActivity started without a room deep link")
            finish()
            return
        }
        setupLockManagement(appBindings)
        enableEdgeToEdge()
        val callback = object : BubbleFlowNode.Callback {
            override fun onDone() {
                finish()
            }

            override fun openInApp(sessionId: SessionId, roomId: RoomId?, eventId: EventId?, threadId: ThreadId?) {
                openMainScreen(
                    appBindings.intentProvider().getViewRoomIntent(sessionId = sessionId, roomId = roomId, threadId = threadId, eventId = eventId)
                )
            }

            override fun openPermalinkInApp(permalink: String) {
                openMainScreen(
                    Intent(this@BubbleActivity, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(permalink.toUri())
                )
            }
        }
        setContent {
            val colors by remember {
                appBindings.enterpriseService().semanticColorsFlow(sessionId = deeplinkData.sessionId)
            }.collectAsState(SemanticColorsLightDark.default)
            ElementThemeApp(
                appPreferencesStore = appBindings.preferencesStore(),
                featureFlagService = appBindings.featureFlagService(),
                compoundLight = colors.light,
                compoundDark = colors.dark,
                buildMeta = appBindings.buildMeta(),
            ) {
                CompositionLocalProvider(
                    LocalSnackbarDispatcher provides appBindings.snackbarDispatcher(),
                    LocalUriHandler provides SafeUriHandler(this),
                    LocalAnalyticsService provides appBindings.analyticsService(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ElementTheme.colors.bgCanvasDefault),
                    ) {
                        DebugNavStateNodeHost(integrationPoint = appyxV1IntegrationPoint) { buildContext ->
                            bindings<NodeFactoriesBindings>().createNode<BubbleFlowNode>(
                                buildContext = buildContext,
                                plugins = listOf(BubbleFlowNode.Inputs(deeplinkData.sessionId, deeplinkData.roomId), callback),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openMainScreen(intent: Intent) {
        // The bubble has its own task, so the main screen must be started in the task of the application
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun setupLockManagement(appBindings: AppBindings) {
        val lockScreenService = appBindings.lockScreenService()
        lockScreenService.handleSecureFlag(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                lockScreenService.lockState.collect { state ->
                    if (state == LockScreenLockState.Locked) {
                        startActivity(appBindings.lockScreenEntryPoint().pinUnlockIntent(this@BubbleActivity))
                    }
                }
            }
        }
    }
}
