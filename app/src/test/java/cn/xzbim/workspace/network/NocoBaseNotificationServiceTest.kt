package cn.xzbim.workspace.network

import cn.xzbim.workspace.network.result.NotificationCountResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NocoBaseNotificationServiceTest {

    private val notificationService = NocoBaseNotificationService()

    @Test
    fun testParseUnreadCountFromDataObj() {
        val json = """
            {
                "data": {
                    "count": 12
                }
            }
        """.trimIndent()

        val result = notificationService.parseUnreadCountJson(json)
        assertTrue(result is NotificationCountResult.Success)
        assertEquals(12, (result as NotificationCountResult.Success).count)
    }

    @Test
    fun testParseUnreadCountZero() {
        val json = """
            {
                "data": {
                    "count": 0
                }
            }
        """.trimIndent()

        val result = notificationService.parseUnreadCountJson(json)
        assertTrue(result is NotificationCountResult.Success)
        assertEquals(0, (result as NotificationCountResult.Success).count)
    }

    @Test
    fun testParseUnreadCountTopLevel() {
        val json = """
            {
                "count": 99
            }
        """.trimIndent()

        val result = notificationService.parseUnreadCountJson(json)
        assertTrue(result is NotificationCountResult.Success)
        assertEquals(99, (result as NotificationCountResult.Success).count)
    }

    @Test
    fun testParseUnreadCountInvalid() {
        val json = """
            {
                "data": {}
            }
        """.trimIndent()

        val result = notificationService.parseUnreadCountJson(json)
        assertEquals(NotificationCountResult.InvalidResponse, result)
    }

    @Test
    fun testParseMalformedJson() {
        val result = notificationService.parseUnreadCountJson("not_json")
        assertEquals(NotificationCountResult.InvalidResponse, result)
    }
}
