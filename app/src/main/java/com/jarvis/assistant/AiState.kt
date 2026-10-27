package com.jarvis.assistant

/** What the assistant is doing right now. Drives the orb and the status label. */
enum class AiState { Idle, Listening, Processing, Speaking, Error }

/** The five sections of the app. */
enum class Tab { Home, Conversations, Memory, Actions, Settings }
