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
import net.imglib2.img.Img
import net.imglib2.img.cell.CellImgFactory
import net.imglib2.type.numeric.integer.LongType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.Views
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
            var offsets: Img<UnsignedLongType>? = null
            var vertices: Img<FloatType>? = null

            while(entry != null) {
                if(entry.isDirectory) {
                    entry = source.nextEntry
                    continue
                }

                logger.debug("Found entry ${entry.name} with ${entry.size}/${entry.compressedSize} bytes")
                var size = entry.size

                when {
                    entry.name == "header.json" -> {
                        val contents = source.readBytes()
                        logger.debug("Read ${contents.size}/${size} bytes")
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
                            if(size == -1L) {
                                logger.warn("TRX file did not specify size for offsets, trying to use header information")
                                size = header?.streamlineCount?.toLong() ?: throw IllegalStateException("Unable to determine offset count")
                                size *= 4L
                                logger.warn("Size from header is $size")
                            }

                            val storage = CellImgFactory(UnsignedLongType(), 100000000, 1).create(size/4)
                            logger.info("Using 32bit offsets for ${storage.dimension(0)} offsets")
                            val iterator = storage.cursor()
                            val br = source.buffered(4096)
                            val buf = ByteArray(4)

                            while(iterator.hasNext()) {
                                val p = iterator.next()
                                br.read(buf)
                                val b = ByteBuffer
                                    .wrap(buf)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asIntBuffer()
                                p.set(b.get().toLong())
                            }
                            storage
                        } else {
                            if(size == -1L) {
                                logger.warn("TRX file did not specify size for offsets, trying to use header information")
                                size = header?.streamlineCount?.toLong() ?: throw IllegalStateException("Unable to determine offset count")
                                size *= 8L
                                logger.warn("Size from header is $size")
                            }
                            val storage = CellImgFactory(UnsignedLongType(), 100000000, 1).create(size/8)
                            logger.info("Using 64bit offsets for ${storage.dimension(0)} offsets")
                            val iterator = storage.cursor()
                            val br = source.buffered(4096)
                            val buf = ByteArray(8)

                            while(iterator.hasNext()) {
                                val p = iterator.next()
                                br.read(buf)
                                val b = ByteBuffer
                                    .wrap(buf)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asLongBuffer()
                                p.set(b.get())
                            }
                            storage
                        }

                        logger.info("Read ${offsets.dimension(0)} offsets")
                        if(logger.isTraceEnabled) {
                            val it = offsets.cursor()
                            while(it.hasNext()) {
                                logger.trace("o=${it.next().get()}")
                            }
                        }
                    }

                    entry.name.startsWith("positions.3") -> {
                        vertices = if(entry.name.endsWith("float16")) {
                            if(size == -1L) {
                                logger.warn("TRX file did not specify size for positions, trying to use header information")
                                size = 2 * 3 * (header?.vertexCount ?: throw IllegalStateException("Unable to determine offset count"))
                                logger.warn("Byte size from header is $size")
                            }

                            logger.info("Using float16 vertices")
                            val storage = CellImgFactory(FloatType(), 100000000, 1).create(size/2)
                            val iterator = storage.cursor()
                            val br = source.buffered(4096)
                            val buf = ByteArray(2)

                            while(iterator.hasNext()) {
                                val p = iterator.next()
                                br.read(buf)
                                p.set(HalfFloat(buf).getFullFloat())
                            }
                            storage
                        } else if(entry.name.endsWith("float32")) {
                            if(size == -1L) {
                                logger.warn("TRX file did not specify size for positions, trying to use header information")
                                size = 4 * 3 * (header?.vertexCount ?: throw IllegalStateException("Unable to determine offset count"))
                                logger.warn("Byte size from header is $size")
                            }

                            logger.info("Using float32 vertices")
                            val storage = CellImgFactory(FloatType(), 100000000, 1).create(size/4)
                            val iterator = storage.cursor()
                            val br = source.buffered(4096)
                            val buf = ByteArray(4)

                            while(iterator.hasNext()) {
                                val p = iterator.next()
                                br.read(buf)
                                val b = ByteBuffer
                                    .wrap(buf)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                p.set(b.float)
                            }
                            storage
                        } else {
                            throw UnsupportedOperationException("float64 is not yet supported for positions.")
                        }

                        logger.info("Read ${vertices.dimension(0)/3} vertices")
                        if(logger.isTraceEnabled) {
                            val it = vertices.cursor()
                            while(it.hasNext()) {
                                val x = it.next().get()
                                val y = it.next().get()
                                val z = it.next().get()
//                                logger.trace("v=$x,$y,$z")
                            }
                        }
                    }
                }

                entry = source.nextEntry
            }


            if(header != null && vertices != null && offsets != null && streamlines != null) {
                val offsetIterator = offsets.iterator()
                val ra = vertices.randomAccess()
                logger.trace("Have ${offsets.dimension(0)} offsets and ${vertices.dimension(0)/3} vertices (${vertices.dimension(0)} floats)")

                streamlines = ArrayList(header.streamlineCount)
                var begin = offsetIterator.next().get().toULong()
                var end = offsetIterator.next().get().toULong() - 1UL

                while(streamlines.size < header.streamlineCount) {
                    val range = begin to end

                    val length = (range.second - range.first).toInt()+1
                    logger.trace("Streamline #${streamlines.size+1}: Range is {}-{}, length {}", range.first, range.second, length)
                    if(length == 0) {
                        logger.warn("Streamline #${streamlines.size+1} with zero length! Range is {}-{}, {} vertices", range.first, range.second, length)
                    }
                    val v = FloatArray(length*3)

                    for(i in range.first until range.second) {
                        ra.setPosition(longArrayOf(i.toLong(), 1L))
                        v[(i-range.first).toInt()] = ra.get().get()
                        ra.setPosition(longArrayOf(i.toLong()+1, 1L))
                        v[((i+1U)-range.first).toInt()] = ra.get().get()
                        ra.setPosition(longArrayOf(i.toLong()+2, 1L))
                        v[((i+2U)-range.first).toInt()] = ra.get().get()
                    }

                    streamlines.add(Streamline(v))
                    begin = end + 1UL
                    end = if(offsetIterator.hasNext()) {
                        offsetIterator.next().get().toULong() - 1UL
                    } else {
                        logger.warn("End of offsets, returning vertex size")
                        header.vertexCount.toULong() - 1UL
                    }

                    if(end == 0UL) {
                        end = header.vertexCount.toULong() - 1UL
                    }
                }

                val duration = System.nanoTime() - start
                logger.info("Created ${streamlines.size} streamlines in ${duration/10e5}ms.")
                assert(streamlines.size == header.streamlineCount)
                return TRX(header, streamlines)
            } else {
                throw IllegalStateException("File incomplete. Header: $header")
            }

        }
    }
}