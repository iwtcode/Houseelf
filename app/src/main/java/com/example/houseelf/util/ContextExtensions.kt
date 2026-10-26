package com.example.houseelf.util

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle

/** Идёт ли активити на экране (запрос разрешения показывает только та, что на виду). */
internal fun Context.isActivityStarted(): Boolean =
    (this as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
