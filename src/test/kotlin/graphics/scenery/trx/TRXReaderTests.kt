package graphics.scenery.trx

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [TRXReader].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class TRXReaderTests {
    /**
     * Tests the reader using the DPSV test file.
     */
    @Test fun readDPSV() {
        val trx = TRXReader.readTRXfromStream(this.javaClass.getResource("dpsv.trx").openStream())
        assertEquals(trx.header.streamlineCount, 460)
        assertEquals(trx.header.vertexCount, 95865)
    }

    /**
     * Tests the reader using the Small test file.
     */
    @Test fun readSmall() {
        val trx = TRXReader.readTRXfromStream(this.javaClass.getResource("small.trx").openStream())
        assertEquals(trx.header.streamlineCount, 1000)
        assertEquals(trx.header.vertexCount, 33886)
    }


    /**
     * Tests the reader using a given file, or a test file given in the trx.filename system property.
     */
    @Test fun readFile() {
        val file = System.getProperty("trx.filename")
        if(file == null) {
            TRXReader.readTRXfromStream(this.javaClass.getResource("dpsv.trx").openStream())
        } else {
            TRXReader.readTRX(file)
        }
    }

}