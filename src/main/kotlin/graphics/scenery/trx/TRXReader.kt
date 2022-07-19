@file:OptIn(ExperimentalUnsignedTypes::class)

package graphics.scenery.trx

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import graphics.scenery.trx.utils.HalfFloat
import graphics.scenery.trx.utils.LazyLogger
import graphics.scenery.trx.utils.MatrixDeserializer
import graphics.scenery.trx.utils.VectorDeserializer
import org.joml.Matrix4f
import org.joml.Vector3i
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Reader class for TRX files.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class TRXReader {
    data class TRX(
        val header: TRXHeader,
        val streamlines: List<Streamline>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TRXHeader(
        @JsonProperty("DIMENSIONS")
        @JsonDeserialize(using = VectorDeserializer::class)
        val dimensions: Vector3i,

        @JsonProperty("NB_VERTICES")
        var vertexCount: Long,

        @JsonProperty("NB_STREAMLINES")
        var streamlineCount: Int,

        @JsonProperty("VOXEL_TO_RASMM")
        @JsonDeserialize(using = MatrixDeserializer::class)
        val voxelToRasMM: Matrix4f
    )

    data class Streamline(
        val vertices: FloatArray
    )

    companion object {
        private val logger by LazyLogger()

        /**
         * Reads a TRX file from a given [filename].
         */
        @JvmStatic fun readTRX(filename: String): TRX {
            logger.info("Reading from $filename")
            val zipFile = ZipInputStream(File(filename).inputStream())
            return readTRX(zipFile)
        }

        /**
         * Reads a TRX file from a given [stream].
         */
        @JvmStatic fun readTRXfromStream(stream: InputStream): TRX {
            return readTRX(ZipInputStream(stream))
        }

        /**
         * Reads a TRX file from a given ZIP'ed stream, given in [source].
         */
        @JvmStatic fun readTRX(source: ZipInputStream): TRX {
            val start = System.nanoTime()

            var entry = source.nextEntry
            val mapper = jsonMapper {
                addModule(kotlinModule())
            }
            var header: TRXHeader? = null
            var streamlines: List<Streamline>? = null
            var offsets: ULongArray? = null
            var vertices: FloatArray? = null

            while(entry != null) {
                if(entry.isDirectory) {
                    entry = source.nextEntry
                    continue
                }

                logger.debug("Found entry ${entry.name} with ${entry.size}/${entry.compressedSize} bytes")
                val contents = source.readBytes()
                logger.debug("Read ${contents.size} bytes")

                when {
                    entry.name == "header.json" -> {
                        logger.debug("Header is ${String(contents)}")
                        header = mapper.readValue(
                            String(contents, charset = Charset.forName("UTF-8")),
                            TRXHeader::class.java
                        )

                        logger.info("File has ${header.streamlineCount} streamlines with ${header.vertexCount} vertices, dimensions are ${header.dimensions}.")

                        streamlines = ArrayList(header.streamlineCount)
                    }

                    entry.name.startsWith("offsets.") -> {
                        offsets = if (entry.name.endsWith("uint32")) {
                            logger.info("Using 32bit offsets")
                            val b = ByteBuffer
                                .wrap(contents)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asIntBuffer()
                            val a = IntArray(contents.size/4)
                            b.get(a)
                            a.map { it.toULong() }.toULongArray()
                        } else {
                            logger.info("Using 64bit offsets")
                            val b = ByteBuffer
                                .wrap(contents)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asLongBuffer()
                            val a = LongArray(contents.size/8)
                            b.get(a)
                            a.toULongArray()
                        }
                    }

                    entry.name.startsWith("positions.3") -> {
                        vertices = if(entry.name.endsWith("float16")) {
                            logger.info("Using float16 vertices")
                            contents
                                .asSequence()
                                .windowed(2, 2)
                                .map { HalfFloat(it.toByteArray()).getFullFloat() }
                                .toList().toFloatArray()
                        } else if(entry.name.endsWith("float32")) {
                            logger.info("Using float32 vertices")
                            val b = ByteBuffer.wrap(contents)
                            val array = FloatArray(b.remaining()/4)
                            b.asFloatBuffer().get(array)
                            array
                        } else {
                            throw UnsupportedOperationException("float64 is not yet supported for positions.")
                        }

                        if(logger.isTraceEnabled) {
                            vertices.toList().windowed(3, 3)
                                .forEach { logger.trace("v=${it[0]},${it[1]},${it[2]}") }
                        }
                    }
                }

                entry = source.nextEntry
            }


            if(header != null && vertices != null && offsets != null && streamlines != null) {
                streamlines = offsets.windowed(size = 2, step = 1, partialWindows = true)
                    .map {
                        val range = if(it.size == 2) {
                            it[0] to it[1]-1UL
                        } else {
                            it[0] to vertices.size.toULong()
                        }

                        val length = (range.second - range.first).toInt()+1
                        logger.debug("Streamline: Range is ${range.first}-${range.second}, length $length")
                        val v = FloatArray(length)

                        for(i in range.first until range.second) {
                            v[i.toInt()-range.first.toInt()] = vertices[i.toInt()]
                        }

                        Streamline(v)
                    }.toList()

                val duration = System.nanoTime() - start
                logger.info("Created ${streamlines.size} streamlines in ${duration/10e6}ms.")
                assert(streamlines.size == header.streamlineCount)
                return TRX(header, streamlines)
            } else {
                throw IllegalStateException("File incomplete. Header: $header")
            }

        }
    }
}