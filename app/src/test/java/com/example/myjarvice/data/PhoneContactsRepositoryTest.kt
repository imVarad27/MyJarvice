package com.example.myjarvice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneContactsRepositoryTest {

    private class FakeContactsProvider(
        var granted: Boolean = true,
        val contactList: MutableList<ContactCard> = mutableListOf()
    ) : ContactsDataProvider {
        override fun isPermissionGranted(): Boolean = granted
        override fun queryAllContacts(): List<ContactCard> = if (granted) contactList else emptyList()
    }

    @Test
    fun `search returns empty when permission not granted`() {
        val provider = FakeContactsProvider(
            granted = false,
            contactList = mutableListOf(
                ContactCard("c1", "Alice Smith", listOf("555-0101"), listOf("alice@example.com"))
            )
        )
        val repo = PhoneContactsRepository(provider)
        assertFalse(repo.isPermissionGranted())
        assertTrue(repo.search("Alice").isEmpty())
    }

    @Test
    fun `search by name matches exact, prefix, and case-insensitive`() {
        val provider = FakeContactsProvider(
            granted = true,
            contactList = mutableListOf(
                ContactCard("c1", "Alice Smith", listOf("555-0101"), listOf("alice@example.com")),
                ContactCard("c2", "Bob Jones", listOf("555-0202"), listOf("bob@example.com")),
                ContactCard("c3", "Princess Alice", listOf("555-0303"), listOf("princess@example.com"))
            )
        )
        val repo = PhoneContactsRepository(provider)

        val results = repo.search("alice")
        assertEquals(2, results.size)
        assertEquals("c1", results[0].id) // "Alice Smith" starts with "alice" (higher score)
        assertEquals("c3", results[1].id) // "Princess Alice" contains "alice"
    }

    @Test
    fun `reverse phone number lookup finds contact by digits`() {
        val provider = FakeContactsProvider(
            granted = true,
            contactList = mutableListOf(
                ContactCard("c1", "Doctor Who", listOf("+1 (555) 987-6543"), listOf()),
                ContactCard("c2", "Emergency Services", listOf("911"), listOf())
            )
        )
        val repo = PhoneContactsRepository(provider)

        val results = repo.search("9876543")
        assertEquals(1, results.size)
        assertEquals("Doctor Who", results.first().name)
    }

    @Test
    fun `search by email finds matching contact`() {
        val provider = FakeContactsProvider(
            granted = true,
            contactList = mutableListOf(
                ContactCard("c1", "Charlie Brown", listOf("555-1234"), listOf("charlie@peanuts.org"))
            )
        )
        val repo = PhoneContactsRepository(provider)

        val results = repo.search("peanuts.org")
        assertEquals(1, results.size)
        assertEquals("Charlie Brown", results.first().name)
    }

    @Test
    fun `findFirstNumber resolves raw digits directly`() {
        val provider = FakeContactsProvider(granted = true)
        val repo = PhoneContactsRepository(provider)

        assertEquals("+15551234567", repo.findFirstNumber("+1 (555) 123-4567"))
        assertEquals("9876543210", repo.findFirstNumber("9876543210"))
    }

    @Test
    fun `findFirstNumber resolves contact name to phone number`() {
        val provider = FakeContactsProvider(
            granted = true,
            contactList = mutableListOf(
                ContactCard("c1", "Tony Stark", listOf("555-3000", "555-3001"), listOf("tony@stark.com"))
            )
        )
        val repo = PhoneContactsRepository(provider)

        assertEquals("555-3000", repo.findFirstNumber("Tony Stark"))
        assertNull(repo.findFirstNumber("Unknown Person"))
        assertNull(repo.findFirstNumber("   "))
    }
}
