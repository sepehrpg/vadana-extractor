package ir.vadana.extractor.data

import android.graphics.Color
import android.graphics.PointF
import android.text.Html
import ir.vadana.extractor.domain.BoardEvent
import ir.vadana.extractor.domain.BoardShape
import ir.vadana.extractor.domain.PageKey
import ir.vadana.extractor.domain.PageNavigationEvent
import ir.vadana.extractor.domain.PointerEvent
import ir.vadana.extractor.domain.Whiteboard
import kotlin.math.roundToInt

object WhiteboardParser {
    private val messageRegex = Regex("<Message time=\"(\\d+)\"[^>]*>(.*?)</Message>", RegexOption.DOT_MATCHES_ALL)
    private val changeRegex = Regex(
        "<code><!\\[CDATA\\[([a-z]+)]]></code>\\s*" +
            "<name><!\\[CDATA\\[(\\d+)]]></name>\\s*" +
            "<newValue>(.*?)</newValue>",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val pageRegex = Regex("<String><!\\[CDATA\\[set_WB_So_(\\d+)]]>")
    private val pointRegex = Regex("<x><!\\[CDATA\\[([^]]*)]]></x>\\s*<y><!\\[CDATA\\[([^]]*)]]></y>")
    private val currentPageRegex = Regex(
        "<name><!\\[CDATA\\[currentPage]]></name>\\s*<newValue><!\\[CDATA\\[(\\d+)]]></newValue>"
    )
    private val pdfPageRegex = Regex("tPgNum-(\\d+)")
    private val pointerChangeRegex = Regex(
        "<name><!\\[CDATA\\[([^]]+)]]></name>\\s*<newValue><!\\[CDATA\\[([^]]*)]]>"
    )

    private data class PodBoard(
        val finalShapes: MutableMap<Int, MutableMap<String, BoardShape>> = mutableMapOf(),
        val events: MutableList<Triple<Long, Int, Pair<String, BoardShape?>>> = mutableListOf(),
        val navigation: MutableList<Pair<Long, Int>> = mutableListOf(),
    )

    fun loadFromPackage(archive: PackageArchive): Whiteboard {
        val names = archive.names().filter { Regex("ftcontent\\d+\\.xml").matches(it) }.sorted()
        val mergedFinal = linkedMapOf<PageKey, Map<String, BoardShape>>()
        val mergedEvents = mutableListOf<BoardEvent>()
        val mergedNavigation = mutableListOf<PageNavigationEvent>()

        names.forEachIndexed { podIndex, name ->
            val xml = archive.readText(name)
            if (!xml.contains("set_WB_So")) return@forEachIndexed
            val pod = parsePod(xml)
            pod.finalShapes.forEach { (page, shapes) ->
                if (shapes.isNotEmpty()) mergedFinal[PageKey(podIndex, page)] = shapes.toMap()
            }
            pod.events.forEach { (time, page, payload) ->
                mergedEvents += BoardEvent(time, PageKey(podIndex, page), payload.first, payload.second)
            }
            pod.navigation.forEach { (time, page) ->
                mergedNavigation += PageNavigationEvent(time, PageKey(podIndex, page))
            }
        }

        return Whiteboard(
            finalShapes = mergedFinal,
            events = mergedEvents.sortedBy { it.timestampMs },
            navigation = mergedNavigation.sortedBy { it.timestampMs },
        )
    }

    fun loadPdfNavigation(archive: PackageArchive): List<Pair<Long, Int>> = buildList {
        archive.names().filter { Regex("ftcontent\\d+\\.xml").matches(it) }.sorted().forEach { name ->
            val xml = archive.readText(name)
            if (!xml.contains("pdfContent")) return@forEach
            messageRegex.findAll(xml).forEach { message ->
                val page = pdfPageRegex.find(message.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()
                if (page != null) add(message.groupValues[1].toLong() to page)
            }
        }
    }.sortedBy { it.first }

    fun loadPointer(archive: PackageArchive): List<PointerEvent> {
        val result = mutableListOf<PointerEvent>()
        var x = 50f
        var y = 50f
        var visible = false
        archive.names().filter { Regex("ftcontent\\d+\\.xml").matches(it) }.sorted().forEach { name ->
            val xml = archive.readText(name)
            if (!xml.contains("setPointerSo")) return@forEach
            messageRegex.findAll(xml).forEach { message ->
                val body = message.groupValues[2]
                if (!body.contains("setPointerSo")) return@forEach
                var touched = false
                pointerChangeRegex.findAll(body).forEach { change ->
                    val key = change.groupValues[1]
                    val value = change.groupValues[2]
                    when (key) {
                        "x" -> value.toFloatOrNull()?.let { x = it; touched = true }
                        "y" -> value.toFloatOrNull()?.let { y = it; touched = true }
                        "visible" -> { visible = value == "true"; touched = true }
                    }
                }
                if (touched) result += PointerEvent(message.groupValues[1].toLong(), x, y, visible)
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    private fun parsePod(xml: String): PodBoard {
        val pod = PodBoard()
        messageRegex.findAll(xml).forEach { message ->
            val time = message.groupValues[1].toLong()
            val body = message.groupValues[2]
            currentPageRegex.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { page ->
                pod.navigation += time to page
            }
            val page = pageRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            val pageShapes = pod.finalShapes.getOrPut(page) { linkedMapOf() }
            changeRegex.findAll(body).forEach { change ->
                val code = change.groupValues[1]
                val shapeId = change.groupValues[2]
                val newValue = change.groupValues[3]
                if (code == "delete") {
                    pageShapes.remove(shapeId)
                    pod.events += Triple(time, page, shapeId to null)
                } else if (newValue.contains("<type>")) {
                    parseShape(newValue, time)?.let { shape ->
                        pageShapes[shapeId] = shape
                        pod.events += Triple(time, page, shapeId to shape)
                    }
                }
            }
        }
        return pod
    }

    private fun parseShape(xml: String, timestampMs: Long): BoardShape? {
        val type = Regex("<type><!\\[CDATA\\[([^]]*)]]>").find(xml)?.groupValues?.get(1) ?: return null
        val withoutPoints = xml.replace(Regex("<pts>.*?</pts>", RegexOption.DOT_MATCHES_ALL), "")
        val baseX = number(withoutPoints, "x")
        val baseY = number(withoutPoints, "y")
        val width = number(withoutPoints, "width")
        val height = number(withoutPoints, "height")
        val depth = number(xml, "depth").roundToInt()

        if (type == "pencil") {
            val pointsBlock = Regex("<pts>(.*?)</pts>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1).orEmpty()
            val points = pointRegex.findAll(pointsBlock).mapNotNull { match ->
                val rx = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val ry = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                PointF(baseX + rx * width, baseY + ry * height)
            }.toList()
            val colorValue = Regex("<strokeCol><!\\[CDATA\\[([^]]*)]]>").find(xml)?.groupValues?.get(1)
            return BoardShape.Pencil(
                depth = depth,
                timestampMs = timestampMs,
                points = points,
                color = decimalColor(colorValue),
                width = number(xml, "strokeWeight", 2f).coerceIn(1f, 30f),
            )
        }

        val raw = Regex("<htmlText><!\\[CDATA\\[(.*?)]]></htmlText>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)
            ?: Regex("<text><!\\[CDATA\\[(.*?)]]></text>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1).orEmpty()
        val lines = raw.split(Regex("</TEXTFORMAT>|</P>", RegexOption.IGNORE_CASE))
            .map { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trimEnd() }
            .filter { it.isNotBlank() }
        val hex = Regex("COLOR=\"#([0-9A-Fa-f]{6})\"").find(raw)?.groupValues?.get(1)
        val size = Regex("SIZE=\"(\\d+)\"").find(raw)?.groupValues?.get(1)?.toFloatOrNull() ?: 21f
        return BoardShape.Text(
            depth = depth,
            timestampMs = timestampMs,
            x = baseX,
            y = baseY,
            lines = lines,
            color = hex?.let { Color.parseColor("#$it") } ?: Color.BLACK,
            textSize = size,
        )
    }

    private fun number(xml: String, tag: String, default: Float = 0f): Float =
        Regex("<$tag><!\\[CDATA\\[([^]]*)]]></$tag>")
            .find(xml)?.groupValues?.get(1)?.toFloatOrNull() ?: default

    private fun decimalColor(value: String?): Int {
        val color = value?.toDoubleOrNull()?.toLong() ?: return Color.BLACK
        return Color.rgb(
            ((color shr 16) and 0xff).toInt(),
            ((color shr 8) and 0xff).toInt(),
            (color and 0xff).toInt(),
        )
    }
}
