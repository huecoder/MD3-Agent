package dev.huecoder.md3agent.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpToolNamesTest {
    @Test
    fun `snake case names become words`() {
        assertEquals("Search repositories", McpTool.readable("search_repositories"))
        assertEquals("Get file contents", McpTool.readable("get_file_contents"))
    }

    @Test
    fun `dashes and repeated separators are folded`() {
        assertEquals("List all issues", McpTool.readable("list--all__issues"))
    }

    @Test
    fun `a name without separators only gets a capital letter`() {
        assertEquals("Fetch", McpTool.readable("fetch"))
        assertEquals("ListIssues", McpTool.readable("listIssues"))
    }

    @Test
    fun `leading and trailing separators are dropped`() {
        assertEquals("Ping", McpTool.readable("_ping_"))
    }
}
