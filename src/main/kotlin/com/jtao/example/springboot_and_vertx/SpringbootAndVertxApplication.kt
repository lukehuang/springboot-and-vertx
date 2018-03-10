package com.jtao.example.springboot_and_vertx

import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
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
    var tcpPort:Int = 12345
    var webPort:Int = 8080
    var monitorDbHost:String = "111.230.197.145"
    var yunkongHost:String = "106.15.56.97"
    var yunkongPort:Int = 8989
    var mqttHost:String = "localhost"
    var mqttPort:Int = 1883

    if(args.size % 2 == 0) {
        val i = args.size / 2
        val json = JsonObject()

        var j = 0
        while(j<i){
            json.put(args[j*2], args[j*2+1])
            j++
        }
        println(json)

        if(json.getInteger("tcp") != null)
            tcpPort = json.getInteger("tcp")
        if(json.getInteger("web") != null)
            webPort = json.getInteger("web")
        if(json.getString("mqttHost") != null)
            mqttHost = json.getString("mqttHost")
    }

    val ctx = SpringApplication.run(SpringbootAndVertxApplication::class.java, *args)
    val vertx = ctx.getBean("vertx") as Vertx
    vertx.deployVerticle(DemoVerticle())
    vertx.deployVerticle(MyVerticle(tcpPort = tcpPort, webPort = webPort, mqttHost = mqttHost))
}
