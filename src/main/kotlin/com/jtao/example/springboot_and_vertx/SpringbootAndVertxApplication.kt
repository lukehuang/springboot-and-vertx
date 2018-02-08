package com.jtao.example.springboot_and_vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class SpringbootAndVertxApplication{
    @Bean("vertx")
    fun vertx() = Vertx.vertx()
}

class DemoVerticle: AbstractVerticle(){

    private val log = LoggerFactory.getLogger(DemoVerticle::class.java)!!

    override fun start(){
        log.info("Hello world")
    }
}

fun main(args: Array<String>) {
    var tcpPort = 23456
    var webPort = 8080
    if (args.size > 0) {
        try {
            tcpPort = args[0].toInt()
        } catch (e:Exception){}
    }
    if (args.size > 1){
        try {
            webPort = args[1].toInt()
        } catch (e:Exception){}
    }

    val ctx = SpringApplication.run(SpringbootAndVertxApplication::class.java, *args)
    val vertx = ctx.getBean("vertx") as Vertx
    vertx.deployVerticle(DemoVerticle())
    vertx.deployVerticle(TcpVerticle(tcpPort, webPort))
}
