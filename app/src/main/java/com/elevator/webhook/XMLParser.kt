package com.elevator.webhook

import org.xmlpull.v1.XmlPullParser
import android.util.Xml

object XMLParser {
    fun extractFloor(xmlString: String): String? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlString.byteInputStream(), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "floor") {
                    eventType = parser.next()
                    if (eventType == XmlPullParser.TEXT) {
                        return parser.text
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
