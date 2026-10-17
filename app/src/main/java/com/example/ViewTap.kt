package com.example

import android.os.SystemClock
import android.view.View

/** Clique protegido pelo anti toque duplo global ([TapGate]). */
fun View.onTap(action: (View) -> Unit) {
    setOnClickListener { v ->
        if (TapGate.shared.accept(SystemClock.uptimeMillis())) action(v)
    }
}
