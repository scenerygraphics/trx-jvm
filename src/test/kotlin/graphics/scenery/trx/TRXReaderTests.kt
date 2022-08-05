package graphics.scenery.trx

import graphics.scenery.trx.utils.LazyLogger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [TRXReader].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class TRXReaderTests {
    private val logger by LazyLogger()

    /**
     * Tests the reader using the DPSV test file.
     */
    @Test fun readDPSV() {
        logger.info("Reading dpsv.trx...")
        val trx = TRXReader.readTRXfromStream(this.javaClass.getResource("dpsv.trx").openStream())
        trx.streamlines.forEachIndexed { index, streamline ->
            assert(streamline.vertices.isNotEmpty()) { "Streamline $index/${trx.streamlines.size} should not be empty" }
        }

        assertEquals(trx.header.streamlineCount, 460)
        assertEquals(trx.header.vertexCount, 95865)
    }

    /**
     * Tests the reader using the Small test file.
     */
    @Test fun readSmall() {
        logger.info("Reading small.trx...")
        val trx = TRXReader.readTRXfromStream(this.javaClass.getResource("small.trx").openStream())
        trx.streamlines.forEachIndexed { index, streamline ->
            assert(streamline.vertices.isNotEmpty()) { "Streamline $index/${trx.streamlines.size} should not be empty" }
        }

        assertEquals(trx.header.streamlineCount, 1000)
        assertEquals(trx.header.vertexCount, 33886)
    }


    /**
     * Tests the reader using a given file, or a test file given in the trx.filename system property.
     */
    @Test fun readFile() {
        val file = System.getProperty("trx.filename")
        val trx = if(file == null) {
            logger.info("Reading dpsv.trx...")
            TRXReader.readTRXfromStream(this.javaClass.getResource("dpsv.trx").openStream())
        } else {
            logger.info("Reading $file...")
            TRXReader.readTRX(file)
        }

        trx.streamlines.forEachIndexed { index, streamline ->
            assert(streamline.vertices.isNotEmpty()) { "Streamline $index/${trx.streamlines.size} should not be empty" }
        }
    }

}