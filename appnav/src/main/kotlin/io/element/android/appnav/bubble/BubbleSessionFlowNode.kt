/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(DelicateCoilApi::class)

package io.element.android.appnav.bubble

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.appnav.di.SessionGraphFactory
import io.element.android.appnav.room.RoomFlowNode
import io.element.android.appnav.room.RoomNavigationTarget
import io.element.android.appnav.room.joined.JoinedRoomLoadedFlowNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.architecture.waitForChildAttached
import io.element.android.libraries.di.DependencyInjectionGraphOwner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.asEventId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrixmedia.api.ImageLoaderHolder
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Optional

/**
 * Displays a single room of a session in a notification bubble. Navigating to anything else than this room opens the main screen
 * of the application.
 */
@ContributesNode(AppScope::class)
@AssistedInject
class BubbleSessionFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    sessionGraphFactory: SessionGraphFactory,
    private val imageLoaderHolder: ImageLoaderHolder,
    private val permalinkBuilder: PermalinkBuilder,
) : BaseFlowNode<BubbleSessionFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget(focusedEventId = null),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
), DependencyInjectionGraphOwner {
    /**
     * The room displayed in the bubble, optionally focused on an event.
     */
    @Parcelize
    data class NavTarget(val focusedEventId: EventId?) : Parcelable

    data class Inputs(
        val matrixClient: MatrixClient,
        val roomId: RoomId,
    ) : NodeInputs

    private val inputs: Inputs = inputs()
    private val callback: BubbleFlowNode.Callback = callback()
    private val sessionId = inputs.matrixClient.sessionId
    override val graph = sessionGraphFactory.create(inputs.matrixClient)

    override fun onBuilt() {
        super.onBuilt()
        lifecycle.subscribe(
            onResume = {
                SingletonImageLoader.setUnsafe(imageLoaderHolder.get(inputs.matrixClient))
            },
        )
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        val joinedRoomCallback = object : JoinedRoomLoadedFlowNode.Callback {
            override fun onDone() {
                callback.onDone()
            }

            override fun navigateToRoom(roomId: RoomId, serverNames: List<String>, clearBackStack: Boolean) {
                callback.openInApp(sessionId, roomId)
            }

            override fun handlePermalinkClick(data: PermalinkData, pushToBackstack: Boolean) {
                if (data is PermalinkData.RoomLink) {
                    onRoomLinkClick(data)
                } else {
                    // Other links are handled by the messages screen, so this is not expected
                    Timber.w("Permalink of type ${data::class.simpleName} not handled in a bubble")
                }
            }

            override fun navigateToGlobalNotificationSettings() {
                callback.openInApp(sessionId, null)
            }

            override fun navigateToDeveloperSettings() {
                callback.openInApp(sessionId, null)
            }
        }
        val roomInputs = RoomFlowNode.Inputs(
            roomIdOrAlias = inputs.roomId.toRoomIdOrAlias(),
            roomDescription = Optional.empty(),
            serverNames = emptyList(),
            trigger = Optional.empty(),
            initialElement = RoomNavigationTarget.Root(eventId = navTarget.focusedEventId),
        )
        return createNode<RoomFlowNode>(buildContext, plugins = listOf(roomInputs, joinedRoomCallback))
    }

    private fun onRoomLinkClick(roomLink: PermalinkData.RoomLink) {
        when (val action = roomLink.toBubbleLinkAction(inputs.roomId)) {
            is BubbleLinkAction.ShowInBubble -> showInBubble(action.eventId, action.threadId)
            is BubbleLinkAction.OpenRoomInApp -> callback.openInApp(sessionId, action.roomId, action.eventId, action.threadId)
            is BubbleLinkAction.OpenAliasInApp -> permalinkBuilder.permalinkForRoomAlias(action.roomAlias)
                .onSuccess(callback::openPermalinkInApp)
                .onFailure { Timber.w(it, "Unable to build a permalink for a room alias") }
        }
    }

    /**
     * Display the room again, focused on the event or thread, as the main screen of the application does.
     */
    private fun showInBubble(eventId: EventId?, threadId: ThreadId?) {
        val navTarget = NavTarget(focusedEventId = threadId?.asEventId() ?: eventId)
        backstack.replace(navTarget)
        if (threadId != null) {
            lifecycleScope.launch {
                waitForChildAttached<RoomFlowNode, NavTarget> { it == navTarget }
                    .attachThread(threadId, eventId)
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier = modifier)
    }
}
