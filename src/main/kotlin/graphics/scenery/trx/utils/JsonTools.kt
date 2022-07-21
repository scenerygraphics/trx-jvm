package graphics.scenery.trx.utils

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.joml.*
import java.nio.ByteBuffer

class VectorDeserializer : JsonDeserializer<Any>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Any {
        val text = if(p.currentToken == JsonToken.START_ARRAY) {
            var token = p.nextToken()
            var result = ""
            while(token != JsonToken.END_ARRAY) {
                result += p.text
                token = p.nextToken()
                if(token != JsonToken.END_ARRAY) {
                    result += ", "
                }
            }
            result
        } else {
            p.text
        }

        val elements = text.split(",")
        if(elements.any { it.contains(".") }) {
            val floats = elements.map { it.trim().trimStart().toFloat() }.toFloatArray()

            return when(floats.size) {
                2 -> Vector2f(floats[0], floats[1])
                3 -> Vector3f(floats[0], floats[1], floats[2])
                4 -> Vector4f(floats[0], floats[1], floats[2], floats[3])
                else -> throw IllegalStateException("Don't know how to deserialise a vector of dimension ${floats.size}")
            }
        } else {
            val ints = elements.map { it.trim().trimStart().toInt() }.toIntArray()

            return when(ints.size) {
                2 -> Vector2i(ints[0], ints[1])
                3 -> Vector3i(ints[0], ints[1], ints[2])
                4 -> Vector4i(ints[0], ints[1], ints[2], ints[3])
                else -> throw IllegalStateException("Don't know how to deserialise a vector of dimension ${ints.size}")
            }

        }
    }
}

class MatrixDeserializer: JsonDeserializer<Any>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Any {
        val text = if(p.currentToken == JsonToken.START_ARRAY) {
            var endTokens = 0
            var result = ""
            var token = p.nextToken()

            // This code is a bit weird but we need to make sure not
            // to screw up the Tokenizer, which is why we keep track of the
            // array end tokens
            while(true) {
                if(token == JsonToken.END_ARRAY || token == JsonToken.START_ARRAY) {
                    if(token == JsonToken.END_ARRAY) {
                        endTokens++

                        if(endTokens == 2) {
                            break
                        }
                    }
                    if(token == JsonToken.START_ARRAY) {
                        endTokens = maxOf(0, endTokens-1)
                    }
                    token = p.nextToken()
                } else {
                    result += p.text + ","
                    token = p.nextToken()
                }
            }
            result
        } else {
            p.text
        }

        val elements = text.split(",").filter { it.isNotEmpty() }
        val floats = elements.map { it.trim().trimStart().toFloat() }.toFloatArray()
        val wrap = ByteBuffer
            .allocateDirect(floats.size * 4)
            .asFloatBuffer()
            .put(floats, 0, floats.size)

        return when(floats.size) {
            4 -> Matrix2f(wrap)
            9 -> Matrix3f(wrap)
            12 -> Matrix4x3f(wrap)
            16 -> Matrix4f(wrap)
            else -> throw UnsupportedOperationException("Don't know what to do with matrix of size ${floats.size}")
        }
    }
}