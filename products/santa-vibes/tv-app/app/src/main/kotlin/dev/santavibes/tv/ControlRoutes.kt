package dev.santavibes.tv

import android.content.res.AssetManager
import dev.santavibes.core.Json
import dev.santavibes.core.JsonOut
import dev.santavibes.tv.ControlServer.Request
import dev.santavibes.tv.ControlServer.Response

/**
 * The control page's routes (docs/ARQUITETURA.md, Controlo). Static files come from the APK
 * assets (control-web/, copied at build time); scene schemas are the scene.json files as they
 * are, so the page builds its sliders from the same source as the box.
 */
class ControlRoutes(
    private val assets: AssetManager,
    library: SceneLibrary,
    private val controller: Controller,
    /** Live status for the page: signal, tempo, image, fps. Called on the HTTP thread. */
    private val live: () -> Map<String, Any?>,
) {
    private val scenesJson: String = library.scenes.joinToString(",", "[", "]") { e ->
        assets.open("scenes/${e.def.id}/scene.json").bufferedReader().use { it.readText() }
    }

    fun handle(r: Request): Response {
        val p = r.path
        return when {
            r.method == "GET" && (p == "/" || p == "/index.html") -> asset("index.html", "text/html; charset=utf-8")
            r.method == "GET" && p == "/app.js" -> asset("app.js", "text/javascript; charset=utf-8")
            r.method == "GET" && p == "/style.css" -> asset("style.css", "text/css; charset=utf-8")
            p == "/api/scenes" -> if (r.method == "GET") Response.json(scenesJson) else notAllowed()
            p == "/api/state" -> when (r.method) {
                "GET" -> state()
                "PATCH" -> body(r) { controller.patch(it); state() }
                else -> notAllowed()
            }
            p == "/api/step" && r.method == "POST" -> body(r) { controller.step(((it["delta"] as? Number)?.toInt() ?: 1).coerceIn(-1, 1)); state() }
            p == "/api/presets" -> when (r.method) {
                "GET" -> Response.json(JsonOut.write(controller.presets()))
                "POST" -> body(r) { o ->
                    val name = (o["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) Response.text("o preset precisa de um nome", 400) else Response.json(JsonOut.write(controller.savePreset(name)), 201)
                }
                else -> notAllowed()
            }
            p.startsWith("/api/presets/") -> preset(r, p.removePrefix("/api/presets/"))
            else -> Response.text("não existe: $p", 404)
        }
    }

    private fun preset(r: Request, rest: String): Response {
        val id = rest.substringBefore('/')
        val action = rest.substringAfter('/', "")
        return when {
            r.method == "DELETE" && action.isEmpty() -> if (controller.deletePreset(id)) Response.json("{}") else missing(id)
            r.method == "POST" && action == "apply" -> if (controller.applyPreset(id)) state() else missing(id)
            r.method == "GET" && action == "export" -> controller.exportPreset(id)?.let { Response.json(JsonOut.write(it)) } ?: missing(id)
            else -> notAllowed()
        }
    }

    private fun state(): Response {
        val o = LinkedHashMap(controller.state())
        o["profileDefaults"] = controller.profileDefaults()
        o["live"] = live()
        return Response.json(JsonOut.write(o))
    }

    private inline fun body(r: Request, f: (Map<*, *>) -> Response): Response {
        val o = runCatching { Json.parse(r.body.ifBlank { "{}" }) }.getOrNull() as? Map<*, *>
            ?: return Response.text("o corpo tem de ser um objeto JSON", 400)
        return f(o)
    }

    private fun asset(name: String, type: String): Response =
        Response(200, type, assets.open("control-web/$name").use { it.readBytes() })

    private fun missing(id: String) = Response.text("não há preset $id", 404)

    private fun notAllowed() = Response.text("método não permitido", 405)
}
