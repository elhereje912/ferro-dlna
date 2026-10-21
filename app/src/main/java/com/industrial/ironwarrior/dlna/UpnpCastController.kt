package com.industrial.ironwarrior.dlna

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Cliente UPnP/AVTransport mínimo.
 *
 * Esto es lo que faltaba para que la app haga "Cast" de verdad: toma el XML
 * de descripción de un dispositivo DLNA (lo que SSDP devuelve como LOCATION),
 * busca el servicio AVTransport y su controlURL, y le manda las acciones
 * SOAP estándar SetAVTransportURI + Play. Eso es exactamente lo que hace
 * cualquier app de "enviar a TV": no reproduce el video en el celular, le
 * ordena al renderer (la TV/parlante) que lo reproduzca él desde la URL.
 *
 * Se implementa a mano con HttpURLConnection + XmlPullParser (ambos ya
 * incluidos en Android) para no sumar librerías UPnP pesadas como Cling,
 * siguiendo el mismo criterio de bajo consumo de RAM del resto del proyecto.
 */
object UpnpCastController {

    private val executor = Executors.newCachedThreadPool()

    /**
     * Envía [mediaUrl] para que [device] (debe ser un MediaRenderer) lo
     * reproduzca. [callback] se llama en un hilo de fondo con el resultado.
     */
    fun cast(device: DlnaDevice, mediaUrl: String, mediaTitle: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val controlUrl = resolveAvTransportControlUrl(device.locationUrl)
                    ?: throw IllegalStateException("Este dispositivo no expone un servicio AVTransport (¿es un MediaServer, no un renderer?)")

                sendSoapAction(
                    controlUrl = controlUrl,
                    action = "SetAVTransportURI",
                    body = """
                        <CurrentURI>${escapeXml(mediaUrl)}</CurrentURI>
                        <CurrentURIMetaData></CurrentURIMetaData>
                    """.trimIndent()
                )

                sendSoapAction(
                    controlUrl = controlUrl,
                    action = "Play",
                    body = "<Speed>1</Speed>"
                )

                callback(true, "Transmitiendo a ${device.name}")
            } catch (e: Exception) {
                callback(false, "No se pudo transmitir: ${e.message}")
            }
        }
    }

    /**
     * Descarga el XML de descripción del dispositivo y extrae la URL de
     * control (controlURL) del servicio AVTransport:1.
     */
    private fun resolveAvTransportControlUrl(descriptionUrl: String): String? {
        val url = URL(descriptionUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }

        var urlBase: String? = null
        var controlPathFound: String? = null

        connection.inputStream.use { input ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setInput(input, null)

            var eventType = parser.eventType
            var currentTag = ""
            var currentServiceType: String? = null
            var currentControlUrl: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            when (currentTag) {
                                "URLBase" -> urlBase = text
                                "serviceType" -> currentServiceType = text
                                "controlURL" -> currentControlUrl = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "service") {
                            if (currentServiceType?.contains("AVTransport") == true) {
                                controlPathFound = currentControlUrl
                            }
                            currentServiceType = null
                            currentControlUrl = null
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        connection.disconnect()

        val path = controlPathFound ?: return null
        if (path.startsWith("http")) return path

        val base = urlBase ?: "${url.protocol}://${url.host}:${if (url.port > 0) url.port else 80}"
        return base.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun sendSoapAction(controlUrl: String, action: String, body: String) {
        val soapEnvelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  $body
                </u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val connection = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(soapEnvelope) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("El dispositivo rechazó la acción $action (HTTP $responseCode)")
        }
        connection.disconnect()
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
