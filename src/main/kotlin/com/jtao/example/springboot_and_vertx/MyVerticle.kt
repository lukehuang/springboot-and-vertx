package com.jtao.example.springboot_and_vertx

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import io.vertx.ext.mongo.MongoClient
import io.vertx.mqtt.MqttClient
import org.slf4j.LoggerFactory
import java.util.*

class MyVerticle(
        val tcpPort:Int = 12345,
        val webPort:Int = 8080,
        val monitorDbHost:String = "111.230.197.145",
        val yunkongHost:String = "106.15.56.97",
        val yunkongPort:Int = 8989,
        val mqttHost:String = "localhost",
        val mqttPort:Int = 1883
): AbstractVerticle() {

    private val log = LoggerFactory.getLogger(MyVerticle::class.java)!!

    override fun start() {
        val vertx = Vertx.vertx()
        val eb = vertx.eventBus()

        //配置Tcp选项：KeepAlive
        val serverOption = NetServerOptions().setTcpKeepAlive(true)
        val server = vertx.createNetServer(serverOption)

        //配置监控数据库
        val dbConfig = JsonObject()
                .put("db_name", "monitor_new")
                .put("host", monitorDbHost)
        val db = MongoClient.createNonShared(vertx, dbConfig)

//        //配置WebSocket实时监控页面
//        val webserver = vertx.createHttpServer()
//        val router = Router.router(vertx)
//        router.route("/").handler { routingContext -> routingContext.response().sendFile("html/ws.html") }
//        webserver
//                .requestHandler(router::accept)
//                .websocketHandler { webSocket->
//                    eb.consumer<String>("receive tcp message", {message ->
//                        webSocket?.writeFinalTextFrame(message.body())
//                    })
//                    eb.consumer<String>("websocket publish", {message ->
//                        webSocket?.writeFinalTextFrame(message.body())
//                    })
//                    webSocket?.exceptionHandler{}
//                    webSocket?.closeHandler {}
//        }.listen(webPort)
//        log.info("web port: $webPort")

        // 连接至远程服务器
        val tcpClientOptions = NetClientOptions().setReconnectAttempts(10).setReconnectInterval(500)
        val tcpClient = vertx.createNetClient(tcpClientOptions)
        var clientSocket:NetSocket? = null
        tcpClient.connect(yunkongPort, yunkongHost, {res->
            if(res.succeeded()) {
                log.info("connect to server")
                clientSocket = res.result()
            }
            else {
                log.info("connect error")
            }
        })

        //连接至MQTT服务器
        val mqttclient = MqttClient.create(vertx).connect(mqttPort,mqttHost,{})

        //处理连接上来的socket
        server.connectHandler { socket ->
            log.info("${socket.remoteAddress()} connect")
            eb.publish("receive tcp message", "${socket.remoteAddress()} connected")

            var ismqttconnect = false

            socket.handler { msg ->
                val m = msg.bytes

                // 获取网关信道
                var channel = 0
                var s_channel = ""
                if(msg.length() > 3) {
                    log.info("Date: ${Date()} Socket: ${socket.remoteAddress()} Message: ${m.toHexString()}")

                    // 配置MQTT订阅
                    if (!ismqttconnect){
                        channel = (m[2].toInt() +256)%256
                        val bs_channel = byteArrayOf(m[2])
                        s_channel = bs_channel.toHexString()
                        mqttclient.publishHandler {event ->
                            println("topic: ${event.topicName()}")
                            println("content: ${event.payload()}")
                            val b = byteArrayOf(
                                    0x05,
                                    0xff.toByte(), 0xff.toByte(),0x00,
                                    0xff.toByte(), 0xfe.toByte(),0x00,
                                    0x26,0x01, 0x81.toByte())
                            val o = event.payload().toJsonObject()
                            log.info("mqtt receive $o")

                            b[1] = o.getString("addr0").toByte(16)
                            b[2] = o.getString("addr1").toByte(16)
                            when(o.getString("ctrl")){
                                "on"-> {
                                    b[9] = 0x81.toByte()
                                    socket.write(Buffer.buffer(b))
                                }
                                "off"->{
                                    b[9] = 0x80.toByte()
                                    socket.write(Buffer.buffer(b))
                                }
                            }
                        }.subscribe("valve/$s_channel",2)
                        ismqttconnect = true
                        println("mqtt sub valve/$s_channel")
                    }

                    val l = JsonObject()
                    l.put("Date", Date().toLocaleString()).put("timestamp", Date().time).put("socket", socket.remoteAddress().toString()).put("message", m.toHexString())
                    db?.insert("log", l, {
                        if (it.succeeded()) log.info("Insert db succeed. ${it.result()}")
                        else log.info("Insert db error.")
                    })

                    val addr = (((m[0]+256)%256)*256 + (m[1]+256)%256).toString(16)
                    val id = "0102030405060708090a0b0f"

                    //处理温湿度数据
                    if ((m.size == 9) and (m[3] == 0x25.toByte())) {
                        val tem = m[5] * 100 + m[6]
                        val hum = m[7] * 100 + m[8]
                        var s_t = "$tem"
                        while (s_t.length < 4) {
                            s_t = "0$s_t"
                        }
                        var s_h = "$hum"
                        while (s_h.length < 4) {
                            s_h = "0$s_h"
                        }
                        log.info("temperature = $s_t, humidity = $s_h")

                        val s_sensor = "34120C8E0f0f$addr$channel${id}19$s_t$s_h"
                        clientSocket?.write(s_sensor)
                        log.info("send to server: $s_sensor")

                        val j = JsonObject().put("Date", Date().toLocaleString()).put("timestamp", Date().time).put("温度", tem / 100.0).put("湿度", hum / 100.0)
                        db?.insert("sensor", j, {})
                        //eb.publish("websocket publish", """${Date()},温度:$s_t,湿度:$s_h""")

                        mqttclient.publish("sensor",
                                JsonObject().put("addr",addr).put("channel",s_channel).put("temperature", tem / 100.0).put("humidity", hum / 100.0).toBuffer(),
                                MqttQoS.EXACTLY_ONCE,
                                false,
                                false)
                    }

                    // 处理gps数据
                    if (m[3] == 0x29.toByte()) {
                        val jingdu = "113.340000"
                        val weidu = "023.160555"
                        var s_gps = "400e0c8e0d0d${addr}${channel}${id}1d${jingdu}${weidu}"
                        log.info("gps: ${jingdu},${weidu}")
                        clientSocket?.write(s_gps)
                        log.info("send to server: $s_gps")
                        db?.insert("gps",
                                JsonObject().put("Date", Date().toLocaleString()).put("经度", jingdu).put("纬度", weidu), {})

                        mqttclient.publish("sensor",
                                JsonObject().put("addr",addr).put("channel",s_channel).put("jingdu", jingdu).put("weidu", weidu).toBuffer(),
                                MqttQoS.EXACTLY_ONCE,
                                false,
                                false)
                    }
                }
            }

            socket.closeHandler {
                log.info("${socket.remoteAddress()} closed")
                //eb.publish("receive tcp message", "${socket.remoteAddress()} closed")
                mqttclient.unsubscribe("valve/00")
            }

            socket.exceptionHandler{
                log.info("${socket.remoteAddress()} error")
            }
        }

        server.listen(tcpPort)
        log.info("MyVerticle listen on $tcpPort")
    }
}