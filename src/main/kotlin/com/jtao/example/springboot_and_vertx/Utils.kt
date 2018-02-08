package com.jtao.example.springboot_and_vertx

fun ByteArray.toHexString(): String {
    var s=""
    this.forEach {
        val b = ((it+256)%256).toString(16)
        s += if (b.length == 1) "0$b" else b
    }
    return s
}

val hexchar = "0123456789abcdef"

fun String.toByteArray(): ByteArray? {
    var i=0
    var str = this
    val  b = ArrayList<Byte>()
    if(this.length  %2 !=0){
        str = "0$this"
    }
    while(i<str.length){
        if((hexchar.contains(str[i]) == false) or (hexchar.contains(str[i+1]) == false)) return null;
        b.add((hexchar.indexOf(str[i])*16 + hexchar.indexOf(str[i+1])).toByte())
        i+=2
    }
    return b.toByteArray()
}