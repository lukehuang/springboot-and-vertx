package com.jtao.example.springboot_and_vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory
import java.util.*


class TcpVerticle(val port:Int, val webPort:Int): AbstractVerticle() {

    private val log = LoggerFactory.getLogger(TcpVerticle::class.java)!!

    val dbConfig = JsonObject().put("db_name", "monitor")

    override fun start() {
        val vertx = Vertx.vertx()
        val eb = vertx.eventBus()
        val server = vertx.createNetServer()
        val db = MongoClient.createNonShared(vertx, dbConfig)
        val webserver = vertx.createHttpServer()
        val router = Router.router(vertx)
        router.route("/").handler { routingContext -> routingContext.response().sendFile("html/ws.html") }
        webserver.requestHandler(router::accept)
        webserver.websocketHandler { websocket->
            if(websocket.path() != "/ws") websocket.close()
            else{
                eb.consumer<String>("receive tcp message", {message ->
                  websocket.writeFinalTextFrame(message.body())
                })
            }
        }
        webserver.listen(webPort)
        log.info("web port: $webPort")

        server.connectHandler { socket ->
            log.info("${socket.remoteAddress()} connect")

            socket.handler { msg ->
                val m = msg.bytes
                log.info("Date: ${Date()} Socket: ${socket.remoteAddress()} Message: ${m.toHexString()}")
                val l = JsonObject()
                l.put("Date", Date().toLocaleString()).put("socket", socket.remoteAddress().toString()).put("message", m.toHexString())
                db.insert("log", l, {
                    if (it.succeeded()) log.info("Insert db succeed. ${it.result()}")
                    else log.info("Insert db error.")
                })
                eb.publish("receive tcp message", m.toHexString())
            }

            socket.closeHandler {
                log.info("${socket.remoteAddress()} closed")
                eb.publish("receive tcp message", "${socket.remoteAddress()} closed")
            }

            socket.exceptionHandler{
                log.info("${socket.remoteAddress()} error")
            }
        }

        server.listen(port)
        log.info("TcpVerticle listen on $port")
    }
}