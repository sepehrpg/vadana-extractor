package ir.vadana.extractor.data

import ir.vadana.extractor.domain.StreamSegment
import ir.vadana.extractor.domain.StreamType

object TimelineParser {
    private val streamRegex = Regex(
        """<startTime><!\[CDATA\[(\d+)]]></startTime>\s*""" +
            """<streamId><!\[CDATA\[[^]]*]]></streamId>\s*""" +
            """<streamName><!\[CDATA\[/([^]]+)]]></streamName>\s*""" +
            """<streamPublisherID><!\[CDATA\[([^]]*)]]></streamPublisherID>\s*""" +
            """<streamType><!\[CDATA\[([^]]+)]]></streamType>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun parseStreams(xml: String): List<StreamSegment> {
        val seen = hashSetOf<String>()
        return buildList {
            streamRegex.findAll(xml).forEach { match ->
                val name = match.groupValues[2]
                if (seen.add(name)) {
                    add(
                        StreamSegment(
                            startMs = match.groupValues[1].toLong(),
                            name = name,
                            publisherId = match.groupValues[3],
                            type = StreamType.fromWire(match.groupValues[4]),
                        )
                    )
                }
            }
        }
    }
}
