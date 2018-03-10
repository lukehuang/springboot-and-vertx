package com.jtao.example.springboot_and_vertx

data class Endpoint(val addr:Int, val channel:Int)

data class EndpointData(
        var humidity:Int, var temperature:Int,
        var longitide:Int, var latitude:Int) {
}

object Endpoints{
    private val map = HashMap<Endpoint, EndpointData>()
    fun save(e:Endpoint, d:EndpointData){
        if(map[e] == null){
            map[e] = d
        } else {
            map[e] = d
        }
    }
}