/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.bubble

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.permalink.PermalinkData

/**
 * What to do with a room link clicked in a notification bubble, which only displays a single room.
 */
sealed interface BubbleLinkAction {
    /**
     * The link targets the room displayed in the bubble: display it in the bubble, focused on the event or thread if any.
     */
    data class ShowInBubble(val eventId: EventId?, val threadId: ThreadId?) : BubbleLinkAction

    /**
     * The link targets another room: open it in the main screen of the application.
     */
    data class OpenRoomInApp(val roomId: RoomId, val eventId: EventId?, val threadId: ThreadId?) : BubbleLinkAction

    /**
     * The link targets a room alias, which the main screen of the application resolves.
     */
    data class OpenAliasInApp(val roomAlias: RoomAlias) : BubbleLinkAction
}

/**
 * Compute the [BubbleLinkAction] for a room link clicked in a bubble displaying the room [bubbleRoomId].
 */
fun PermalinkData.RoomLink.toBubbleLinkAction(bubbleRoomId: RoomId): BubbleLinkAction {
    return when (val target = roomIdOrAlias) {
        is RoomIdOrAlias.Id -> if (target.roomId == bubbleRoomId) {
            BubbleLinkAction.ShowInBubble(eventId = eventId, threadId = threadId)
        } else {
            BubbleLinkAction.OpenRoomInApp(roomId = target.roomId, eventId = eventId, threadId = threadId)
        }
        is RoomIdOrAlias.Alias -> BubbleLinkAction.OpenAliasInApp(target.roomAlias)
    }
}
