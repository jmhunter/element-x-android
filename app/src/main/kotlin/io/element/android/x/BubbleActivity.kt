/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

/**
 * Activity displayed in a notification bubble, to chat in a room on top of other applications.
 * See https://developer.android.com/develop/ui/views/notifications/bubbles
 *
 * It hosts the same navigation tree as [MainActivity] and is started with a room deep link. Unlike [MainActivity],
 * it is not `singleTask`: the system creates one instance per bubble, embedded in the bubble's own task.
 */
class BubbleActivity : MainActivity() {
    override val isBubble: Boolean = true
}
