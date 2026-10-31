package com.example.myjarvice.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** A contact card containing phone numbers and email addresses. */
data class ContactCard(
    val id: String,
    val name: String,
    val numbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String = ""
)

/** Decouples ContactsContract ContentResolver operations for testing without Android dependencies. */
interface ContactsDataProvider {
    fun isPermissionGranted(): Boolean
    fun queryAllContacts(): List<ContactCard>
}

/** Implementation querying Android system ContactsContract via ContentResolver. */
class AndroidContactsDataProvider(private val context: Context) : ContactsDataProvider {
    override fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun queryAllContacts(): List<ContactCard> {
        if (!isPermissionGranted()) return emptyList()

        return runCatching {
            val contactMap = linkedMapOf<String, ContactBuilder>()

            // 1. Query Phone Numbers
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    if (id.isNotBlank() && name.isNotBlank()) {
                        val builder = contactMap.getOrPut(id) { ContactBuilder(id, name) }
                        if (number.isNotBlank() && number !in builder.numbers) {
                            builder.numbers.add(number)
                        }
                    }
                }
            }

            // 2. Query Email Addresses
            val emailProjection = arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.DATA
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                emailProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
                val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val email = if (emailIdx >= 0) cursor.getString(emailIdx) ?: "" else ""
                    if (id.isNotBlank()) {
                        val builder = contactMap.getOrPut(id) { ContactBuilder(id, name.ifBlank { "Contact $id" }) }
                        if (email.isNotBlank() && email !in builder.emails) {
                            builder.emails.add(email)
                        }
                    }
                }
            }

            contactMap.values.map { it.build() }
        }.getOrDefault(emptyList())
    }

    private class ContactBuilder(
        val id: String,
        val name: String,
        val numbers: MutableList<String> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf()
    ) {
        fun build() = ContactCard(id = id, name = name, numbers = numbers, emails = emails)
    }
}

/**
 * High-performance, read-only contact lookup and reverse phone number search.
 * Strictly local and offline: contact information never leaves the phone.
 */
class PhoneContactsRepository internal constructor(
    private val provider: ContactsDataProvider
) {
    constructor(context: Context) : this(AndroidContactsDataProvider(context))

    fun isPermissionGranted(): Boolean = provider.isPermissionGranted()

    /**
     * Search contacts by name, partial name, phone number, or email address.
     * Supports reverse phone lookup (e.g. searching "555" or full number to find the contact).
     */
    fun search(query: String, maxResults: Int = 10): List<ContactCard> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()

        val all = provider.queryAllContacts()
        if (all.isEmpty()) return emptyList()

        val queryDigits = q.filter { it.isDigit() }
        val matchesNumber = queryDigits.length >= 3

        return all.asSequence()
            .map { contact ->
                val score = calculateMatchScore(contact, q, queryDigits, matchesNumber)
                contact to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
            .toList()
    }

    /**
     * Resolves a raw name or phone query to a dialable phone number.
     * If query is already a phone number, returns formatted/clean digits.
     * If query is a contact name, returns their primary phone number.
     */
    fun findFirstNumber(nameOrQuery: String): String? {
        val trimmed = nameOrQuery.trim()
        if (trimmed.isBlank()) return null

        val digits = trimmed.filter { it.isDigit() || it == '+' }
        val looksLikeNumber = trimmed.none { it.isLetter() } && digits.count { it.isDigit() } >= 3
        if (looksLikeNumber) return digits

        val contacts = search(trimmed, maxResults = 3)
        return contacts.firstOrNull { it.numbers.isNotEmpty() }?.numbers?.firstOrNull()
    }

    private fun calculateMatchScore(
        contact: ContactCard,
        q: String,
        queryDigits: String,
        matchesNumber: Boolean
    ): Int {
        val nameLower = contact.name.lowercase()
        if (nameLower == q) return 100
        if (nameLower.startsWith(q)) return 80
        if (nameLower.contains(q)) return 60

        if (contact.emails.any { it.lowercase().contains(q) }) return 50

        if (matchesNumber) {
            val hasPhoneMatch = contact.numbers.any { num ->
                val numDigits = num.filter { it.isDigit() }
                numDigits.contains(queryDigits)
            }
            if (hasPhoneMatch) return 70
        }

        return 0
    }
}
