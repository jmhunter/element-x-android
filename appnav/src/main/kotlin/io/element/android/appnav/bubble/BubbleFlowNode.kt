/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.bubble

import android.content.Context
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.newRoot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.DependencyInjectionGraphOwner
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Root node of the screen displayed in a notification bubble.
 *
 * Unlike the root node of the main screen, it only displays a single room, and it does not change the current session of the
 * application, so that the bubble and the main screen can be used at the same time.
 */
@ContributesNode(AppScope::class)
@AssistedInject
class BubbleFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    @ApplicationContext context: Context,
    private val matrixClientProvider: MatrixClientProvider,
) : BaseFlowNode<BubbleFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.Loading,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
), DependencyInjectionGraphOwner {
    interface Callback : Plugin {
        /**
         * The room is not displayed anymore, the bubble can be closed.
         */
        fun onDone()

        /**
         * Open the main screen of the application, on the given room if any.
         */
        fun openInApp(sessionId: SessionId, roomId: RoomId?)
    }

    data class Inputs(
        val sessionId: SessionId,
        val roomId: RoomId,
    ) : NodeInputs

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Loading : NavTarget

        @Parcelize
        data object Room : NavTarget
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    override val graph = (context as DependencyInjectionGraphOwner).graph

    override fun onBuilt() {
        super.onBuilt()
        lifecycleScope.launch {
            matrixClientProvider.getOrRestore(inputs.sessionId)
                .onSuccess { backstack.newRoot(NavTarget.Room) }
                .onFailure {
                    Timber.e(it, "Unable to restore session ${inputs.sessionId} to display room ${inputs.roomId} in a bubble")
                    callback.onDone()
                }
        }
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Loading -> node(buildContext) {}
            NavTarget.Room -> {
                val matrixClient = matrixClientProvider.getOrNull(inputs.sessionId) ?: return node(buildContext) {}
                createNode<BubbleSessionFlowNode>(
                    buildContext = buildContext,
                    plugins = listOf(BubbleSessionFlowNode.Inputs(matrixClient, inputs.roomId), callback),
                )
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier = modifier)
    }
}
