package com.deuterium.app.repository

import com.deuterium.app.data.ChatFeedItem

data class ChatFeedMergeResult(
    val messages: List<ChatFeedItem>,
    val inserted: List<ChatFeedItem>
)

fun mergeChatFeedItems(
    current: List<ChatFeedItem>,
    incoming: List<ChatFeedItem>,
    maxMessages: Int = 500
): ChatFeedMergeResult {
    if (incoming.isEmpty()) return ChatFeedMergeResult(current, emptyList())

    val mergedById = LinkedHashMap<String, ChatFeedItem>()
    current.forEach { item -> mergedById[item.id] = item }

    val inserted = mutableListOf<ChatFeedItem>()
    incoming.forEach { item ->
        if (!mergedById.containsKey(item.id)) {
            mergedById[item.id] = item
            inserted.add(item)
        }
    }

    if (inserted.isEmpty()) return ChatFeedMergeResult(current, emptyList())

    val sorted = mergedById.values.sortedWith(
            compareBy<ChatFeedItem> { it.sentAt.orEmpty() }
                .thenBy { it.id }
        )
        .takeLast(maxMessages)

    return ChatFeedMergeResult(
        messages = sorted,
        inserted = inserted
    )
}

