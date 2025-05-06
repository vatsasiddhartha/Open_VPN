package com.dzboot.ovpn.custom

import com.google.gson.*
import java.lang.reflect.Type


internal class BooleanTypeAdapter : JsonDeserializer<Boolean> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Boolean {
        return when {
            (json as JsonPrimitive).isBoolean -> json.getAsBoolean()
            json.isString -> arrayListOf("1", "true", "TRUE").contains(json.getAsString())
            json.isNumber -> json.getAsInt() == 1
            else -> false
        }
    }
}