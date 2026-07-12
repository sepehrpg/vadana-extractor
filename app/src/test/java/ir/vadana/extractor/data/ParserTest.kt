package ir.vadana.extractor.data

import ir.vadana.extractor.domain.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    @Test
    fun parsesRecordingUrl() {
        val recording = RecordingUrlParser.parse(
            "https://vadavc30.ec.iau.ir/lykiz9mc3wqp/?session=tok123&proto=true"
        )
        assertEquals("https://vadavc30.ec.iau.ir", recording.host)
        assertEquals("lykiz9mc3wqp", recording.recordingId)
        assertEquals("tok123", recording.sessionToken)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLocalHost() {
        RecordingUrlParser.parse("https://localhost/abc/")
    }

    @Test
    fun findsAndDeduplicatesSharedFiles() {
        val xml = """
            <recording>
              <downloadUrl><![CDATA[/system/download?download-url=/_a7/11/source/&name=Slides.pdf]]></downloadUrl>
              <downloadUrl><![CDATA[/system/download?download-url=/_a7/22/source/&name=Slides.pdf]]></downloadUrl>
              <downloadUrl><![CDATA[/system/download?download-url=/_a7/11/source/&name=notes.docx]]></downloadUrl>
            </recording>
        """.trimIndent()
        val files = MainstreamParser.findSharedFiles(xml)
        assertEquals(listOf("Slides.pdf", "notes.docx"), files.map { it.fileName })
        assertTrue(files.all { it.sourceBase.startsWith('/') })
    }

    @Test
    fun parsesStreams() {
        val xml = """
            <startTime><![CDATA[1200]]></startTime>
            <streamId><![CDATA[x]]></streamId>
            <streamName><![CDATA[/cameraVoip0]]></streamName>
            <streamPublisherID><![CDATA[p1]]></streamPublisherID>
            <streamType><![CDATA[cameraVoip]]></streamType>
        """.trimIndent()
        val streams = TimelineParser.parseStreams(xml)
        assertEquals(1, streams.size)
        assertEquals(1200L, streams.single().startMs)
        assertEquals(StreamType.CAMERA_VOIP, streams.single().type)
    }
}
