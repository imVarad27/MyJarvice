package com.example.myjarvice.ui.main

import android.content.Intent
import com.example.myjarvice.data.ReplyShareApproval

/** Plain text only: no recipients, history, attachments, grant flags or direct-send targets. */
internal fun replyShareChooser(text: String): Intent {
    require(ReplyShareApproval.validText(text))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return Intent.createChooser(send, "Choose where to use this text")
}
