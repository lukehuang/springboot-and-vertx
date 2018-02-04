package com.jtao.example.springboot_and_vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import org.slf4j.LoggerFactory
import java.util.*

class TcpVerticle(val port:Int): AbstractVerticle() {

    private val log = LoggerFactory.getLogger(TcpVerticle::class.java)!!

    override fun start() {
        val vertx = Vertx.vertx()
        val server = vertx.createNetServer()
        server.connectHandler { socket ->
            log.info("${socket.remoteAddress()} connect")

            val handshake = byteArrayOf(0x01,0x02,0x03,0x04,0x05)
            socket.write(Buffer.buffer(handshake))

            socket.handler { msg ->
                log.info("Date: ${Date()} Socket: ${socket.remoteAddress()} Message length: ${msg.length()}")
            }

            socket.closeHandler {
                log.info("${socket.remoteAddress()} closed")
            }

            socket.exceptionHandler{
                log.info("${socket.remoteAddress()} error")
            }
        }

        server.listen(port)
        log.info("TcpVerticle listen on $port")
    }
}