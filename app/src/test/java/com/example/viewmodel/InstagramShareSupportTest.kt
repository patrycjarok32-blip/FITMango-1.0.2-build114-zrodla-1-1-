package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class InstagramShareSupportTest {
    @Test fun readerAcceptsOnlyHttpsInstagramAndReadsOnlyPostPages() {
        assertTrue(isInstagramRecipePage("https://www.instagram.com/reel/abc/?x=1"))
        assertTrue(isInstagramReaderUrl("https://www.instagram.com/accounts/login/"))
        assertFalse(isInstagramRecipePage("https://www.instagram.com/accounts/login/"))
        assertFalse(isInstagramReaderUrl("https://instagram.com.attacker.example/reel/abc/"))
        assertFalse(isInstagramReaderUrl("https://user:pass@instagram.com/reel/abc/"))
        assertFalse(isInstagramReaderUrl("http://instagram.com/reel/abc/"))
        assertFalse(isInstagramReaderUrl("file:///etc/passwd"))
    }
    @Test fun sharedIntroAndLinkResolveToInstagramPostWithoutIntro() {
        assertEquals("https://www.instagram.com/reel/abc/?x=1",
            instagramPostUriOrNull("Zobacz ten przepis https://www.instagram.com/reel/abc/?x=1")?.toString())
        assertNull(instagramPostUriOrNull("https://instagram.com.attacker.example/reel/abc/"))
        assertNull(instagramPostUriOrNull("https://example.com/recipe"))
    }
    @Test fun clipboardLinkIsNotMistakenForRecipeCaption() {
        assertNull(usableInstagramCaption("https://www.instagram.com/reel/abc/"))
        assertNull(usableInstagramCaption("Zobacz przepis https://www.instagram.com/reel/abc/"))
        assertNull(usableInstagramCaption(""))
        val caption = "Zupa\nSkładniki:\n200 g ziemniaków\nPrzygotowanie:\nUgotuj ziemniaki i zmiksuj."
        assertEquals(caption, usableInstagramCaption(caption))
        assertEquals(caption + "\nhttps://www.instagram.com/reel/abc/",
            usableInstagramCaption(caption + "\nhttps://www.instagram.com/reel/abc/"))
    }
}
