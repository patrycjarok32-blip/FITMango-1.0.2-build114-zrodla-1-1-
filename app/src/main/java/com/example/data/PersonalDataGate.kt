package com.example.data

import kotlinx.coroutines.sync.Mutex

// Restore and reversible edits must not interleave their snapshots.
internal object PersonalDataGate { val mutex = Mutex() }
