/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.bubble

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_ROOM_ALIAS
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_THREAD_ID
import org.junit.Test

class BubbleLinkActionTest {
    @Test
    fun `a link to the room of the bubble is displayed in the bubble`() {
        val link = PermalinkData.RoomLink(roomIdOrAlias = A_ROOM_ID.toRoomIdOrAlias(), eventId = AN_EVENT_ID, threadId = A_THREAD_ID)
        assertThat(link.toBubbleLinkAction(A_ROOM_ID)).isEqualTo(
            BubbleLinkAction.ShowInBubble(eventId = AN_EVENT_ID, threadId = A_THREAD_ID)
        )
    }

    @Test
    fun `a link to another room is opened in the app with its event and thread`() {
        val link = PermalinkData.RoomLink(roomIdOrAlias = A_ROOM_ID_2.toRoomIdOrAlias(), eventId = AN_EVENT_ID, threadId = A_THREAD_ID)
        assertThat(link.toBubbleLinkAction(A_ROOM_ID)).isEqualTo(
            BubbleLinkAction.OpenRoomInApp(roomId = A_ROOM_ID_2, eventId = AN_EVENT_ID, threadId = A_THREAD_ID)
        )
    }

    @Test
    fun `a link to a room alias is opened in the app`() {
        val link = PermalinkData.RoomLink(roomIdOrAlias = A_ROOM_ALIAS.toRoomIdOrAlias())
        assertThat(link.toBubbleLinkAction(A_ROOM_ID)).isEqualTo(
            BubbleLinkAction.OpenAliasInApp(A_ROOM_ALIAS)
        )
    }
}
