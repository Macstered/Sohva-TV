package com.sohva.tv.app

import android.app.Application

/**
 * Process entry. Builds lazy holders only: no disk, database, preferences, WorkManager,
 * Keystore, network or image loader before the first frame (plan/03 §4.9).
 */
class SohvaApplication : Application()
