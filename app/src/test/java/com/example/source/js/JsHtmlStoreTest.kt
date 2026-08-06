package com.example.source.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JsHtmlStoreTest {

    @Test
    fun scriptElementTextReturnsData() {
        val html = """
            <html><body>
              <script id="sv-data" type="application/json">{"data":[{"id":1,"title":"One Piece"}]}</script>
              <p>普通文本</p>
            </body></html>
        """.trimIndent()
        val store = JsHtmlStore()
        val docId = store.parse(0, html)
        val elemId = store.getElementById(docId, "sv-data")
        assertNotNull(elemId)
        val text = store.getText(elemId!!)
        assertEquals("{\"data\":[{\"id\":1,\"title\":\"One Piece\"}]}", text)
    }

    @Test
    fun normalElementTextStillWorks() {
        val html = "<html><body><p id=\"p1\">hello</p></body></html>"
        val store = JsHtmlStore()
        val docId = store.parse(0, html)
        val elemId = store.getElementById(docId, "p1")
        assertNotNull(elemId)
        assertEquals("hello", store.getText(elemId!!))
    }

    @Test
    fun secondDocumentLookupWorks() {
        val store = JsHtmlStore()
        val doc0 = store.parse(0, "<html><body><p id=\"a\">first</p></body></html>")
        val doc1 = store.parse(1, """
            <html><body>
              <script type="application/json" id="comic-data">{"title":"One Piece"}</script>
            </body></html>
        """.trimIndent())
        assertEquals(0, doc0)
        assertEquals(1, doc1)
        val elem = store.getElementById(doc1, "comic-data")
        assertNotNull("second doc lookup failed", elem)
        assertEquals("{\"title\":\"One Piece\"}", store.getText(elem!!))
    }
}
