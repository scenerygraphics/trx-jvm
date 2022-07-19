package graphics.scenery.trx

import kotlin.test.Test

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
        TRXReader.readTRXfromStream(this.javaClass.getResource("dpsv.trx").openStream())
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