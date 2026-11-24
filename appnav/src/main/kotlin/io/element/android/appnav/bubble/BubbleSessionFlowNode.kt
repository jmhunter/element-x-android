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
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.bumble.appyx.core.composable.Children
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.navigation.model.permanent.PermanentNavModel
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.ParentNode
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.appnav.di.SessionGraphFactory
import io.element.android.appnav.room.RoomFlowNode
import io.element.android.appnav.room.RoomNavigationTarget
import io.element.android.appnav.room.joined.JoinedRoomLoadedFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.DependencyInjectionGraphOwner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrixmedia.api.ImageLoaderHolder
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
) : ParentNode<BubbleSessionFlowNode.NavTarget>(
    navModel = PermanentNavModel(
        navTargets = setOf(NavTarget),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
), DependencyInjectionGraphOwner {
    @Parcelize
    object NavTarget : Parcelable

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
                val roomIdOrAlias = (data as? PermalinkData.RoomLink)?.roomIdOrAlias
                if (roomIdOrAlias is RoomIdOrAlias.Id) {
                    callback.openInApp(sessionId, roomIdOrAlias.roomId)
                } else {
                    Timber.w("Permalink not handled in a bubble")
                }
            }

            override fun navigateToGlobalNotificationSettings() {
                callback.openInApp(sessionId, null)
            }

            override fun navigateToDeveloperSettings() {
                callback.openInApp(sessionId, null)
            }
        }
        val inputs = RoomFlowNode.Inputs(
            roomIdOrAlias = inputs.roomId.toRoomIdOrAlias(),
            roomDescription = Optional.empty(),
            serverNames = emptyList(),
            trigger = Optional.empty(),
            initialElement = RoomNavigationTarget.Root(),
        )
        return createNode<RoomFlowNode>(buildContext, plugins = listOf(inputs, joinedRoomCallback))
    }

    @Composable
    override fun View(modifier: Modifier) {
        Children(
            navModel = navModel,
            modifier = modifier,
        )
    }
}
