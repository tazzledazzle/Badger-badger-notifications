package com.badger.notifications.broker

interface BrokerPublisher {
    suspend fun publish(stream: NotificationStream, body: String): String
}
