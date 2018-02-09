package com.jtao.example.springboot_and_vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetSocket
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory
import java.util.*


open class TcpVerticle(val port:Int, val webPort:Int): AbstractVerticle() {

    private val log = LoggerFactory.getLogger(TcpVerticle::class.java)!!

    val dbConfig = JsonObject().put("db_name", "monitor").put("host", "111.230.197.145")

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
                eb.consumer<String>("websocket publish", {message ->
                    websocket.writeFinalTextFrame(message.body())
                })
            }
        }
        webserver.listen(webPort)
        log.info("web port: $webPort")

        val tcpClientOptions = NetClientOptions().setReconnectAttempts(10).setReconnectInterval(500)
        val tcpClient = vertx.createNetClient(tcpClientOptions)
        var clientSocket:NetSocket? = null
        tcpClient.connect(8989, "106.15.56.97", {res->
            if(res.succeeded()) {
                log.info("connect to server")
                clientSocket = res.result()
            }
            else {
                log.info("connect error")
            }
        })

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
                //eb.publish("receive tcp message", m.toHexString())


                val addr = "0000"
                val channel = "00"
                val id = "0102030405060708090a0b0f"

                if((m.size == 9) and (m[3] == 0x25.toByte())){
                    val tem = m[5]*100+m[6]
                    val hum = m[7]*100+m[8]
                    var s_t = "$tem"
                    while(s_t.length<4){
                        s_t = "0$s_t"
                    }
                    var s_h = "$hum"
                    while(s_h.length<4){
                        s_h = "0$s_h"
                    }
                    log.info("temperature = $s_t, humidity = $s_h")

                    var s_sensor = "34120C8E0f0f$addr$channel${id}19$s_t$s_h"
                    clientSocket?.write(s_sensor)
                    log.info("send to server: $s_sensor")

                    val j =  JsonObject().put("Date", Date().toLocaleString()).put("温度", tem).put("湿度",hum)
                    db.insert("sensor", j , {})
                    eb.publish("websocket publish",
                            """${Date()},
                                |温度:$s_t,
                                |湿度:$s_h
                                | """.trimMargin())
                }

                if(m[3] == 0x29.toByte()) {
                    val jingdu = "113.340000"
                    val weidu = "023.160555"
                    var s_gps = "400e0c8e0d0d${addr}${channel}${id}1d${jingdu}${weidu}"
                    log.info("gps: ${jingdu},${weidu}")
                    clientSocket?.write(s_gps)
                    log.info("send to server: $s_gps")
                    db.insert("gps",
                            JsonObject().put("Date", Date().toLocaleString()).put("经度", jingdu).put("纬度",weidu), {})
                }
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