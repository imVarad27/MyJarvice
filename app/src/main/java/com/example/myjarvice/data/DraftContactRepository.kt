package com.example.myjarvice.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

/** Queries only the single URI returned by the explicit picker; never enumerates contacts. */
internal class DraftContactRepository internal constructor(
    private val query: (Uri, Array<String>) -> Cursor?
) {
    constructor(context: Context) : this({ uri, columns ->
        context.contentResolver.query(uri, columns, null, null, null)
    })

    fun selectedName(uri: Uri): String {
        require(isSingleContact(uri)) { "Select one contact in the contact picker." }
        val columns = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        return query(uri, columns)?.use { cursor ->
            check(cursor.moveToFirst()) { "This contact is no longer available." }
            val name = DraftRecipientName.normalize(cursor.getString(cursor.getColumnIndexOrThrow(columns[0])))
            check(!cursor.moveToNext()) { "Select one contact in the contact picker." }
            checkNotNull(name) { "This contact has no readable name. Type a recipient instead." }
        } ?: error("The selected contact could not be read. Type a recipient instead.")
    }

    private fun isSingleContact(uri: Uri): Boolean {
        if (uri.toString().length > 2048 || uri.scheme != "content" ||
            uri.authority != ContactsContract.AUTHORITY || uri.query != null || uri.fragment != null) return false
        val path = uri.pathSegments
        fun id(value: String) = value.toLongOrNull()?.let { it > 0 } == true
        return path.firstOrNull() == "contacts" && when (path.size) {
            2 -> id(path[1])
            3 -> path[1] == "lookup" && path[2].isNotBlank()
            4 -> path[1] == "lookup" && path[2].isNotBlank() && id(path[3])
            else -> false
        }
    }
}
