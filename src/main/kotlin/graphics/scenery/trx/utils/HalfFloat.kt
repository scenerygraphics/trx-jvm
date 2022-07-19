package graphics.scenery.trx.utils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Courtesy of dgestep, https://stackoverflow.com/a/50889284
 */
class HalfFloat {
    private var halfPrecision: Short = 0
    private var fullPrecision: Float? = null

    /**
     * Creates an instance of the class from the supplied the supplied
     * byte array.  The byte array must be exactly two bytes in length.
     *
     * @param bytes the two-byte byte array.
     */
    constructor(bytes: ByteArray) {
        if (bytes.size != 2) {
            throw IllegalArgumentException(
                "The supplied byte array " +
                        "must be exactly two bytes in length"
            )
        }
        val buffer: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        halfPrecision = buffer.getShort()
    }

    /**
     * Creates an instance of this class from the supplied short number.
     *
     * @param number the number defined as a short.
     */
    constructor(number: Short) {
        halfPrecision = number
        fullPrecision = toFullPrecision()
    }

    /**
     * Creates an instance of this class from the supplied
     * full-precision floating point number.
     *
     * @param number the float number.
     */
    constructor(number: Float) {
        if (number > Short.MAX_VALUE) {
            throw IllegalArgumentException(
                "The supplied float is too "
                        + "large for a two byte representation"
            )
        }
        if (number < Short.MIN_VALUE) {
            throw IllegalArgumentException(
                ("The supplied float is too "
                        + "small for a two byte representation")
            )
        }
        val `val` = fromFullPrecision(number)
        halfPrecision = `val`.toShort()
        fullPrecision = number
    }

    /**
     * Returns the half-precision float as a number defined as a short.
     *
     * @return the short.
     */
    fun getHalfPrecisionAsShort(): Short {
        return halfPrecision
    }

    /**
     * Returns a full-precision floating pointing number from the
     * half-precision value assigned on this instance.
     *
     * @return the full-precision floating pointing number.
     */
    fun getFullFloat(): Float {
        if (fullPrecision == null) {
            fullPrecision = toFullPrecision()
        }
        return fullPrecision!!
    }

    /**
     * Returns a full-precision double floating point number from the
     * half-precision value assigned on this instance.
     *
     * @return the full-precision double floating pointing number.
     */
    fun getFullDouble(): Double {
        return getFullFloat().toDouble()
    }

    /**
     * Returns the full-precision float number from the half-precision
     * value assigned on this instance.
     *
     * @return the full-precision floating pointing number.
     */
    private fun toFullPrecision(): Float {
        var mantisa = halfPrecision.toInt() and 0x03ff
        var exponent = halfPrecision.toInt() and 0x7c00
        if (exponent == 0x7c00) {
            exponent = 0x3fc00
        } else if (exponent != 0) {
            exponent += 0x1c000
            if (mantisa == 0 && exponent > 0x1c400) {
                return java.lang.Float.intBitsToFloat(
                    ((halfPrecision.toInt() and 0x8000) shl 16) or (exponent shl 13) or 0x3ff
                )
            }
        } else if (mantisa != 0) {
            exponent = 0x1c400
            do {
                mantisa = mantisa shl 1
                exponent -= 0x400
            } while ((mantisa and 0x400) == 0)
            mantisa = mantisa and 0x3ff
        }
        return java.lang.Float.intBitsToFloat(
            ((halfPrecision.toInt() and 0x8000) shl 16) or ((exponent or mantisa) shl 13)
        )
    }

    /**
     * Returns the integer representation of the supplied
     * full-precision floating pointing number.
     *
     * @param number the full-precision floating pointing number.
     * @return the integer representation.
     */
    private fun fromFullPrecision(number: Float): Int {
        val fbits = java.lang.Float.floatToIntBits(number)
        val sign = (fbits ushr 16) and 0x8000
        var `val` = (fbits and 0x7fffffff) + 0x1000
        if (`val` >= 0x47800000) {
            if ((fbits and 0x7fffffff) >= 0x47800000) {
                return if (`val` < 0x7f800000) {
                    sign or 0x7c00
                } else sign or 0x7c00 or ((fbits and 0x007fffff) ushr 13)
            }
            return sign or 0x7bff
        }
        if (`val` >= 0x38800000) {
            return sign or ((`val` - 0x38000000) ushr 13)
        }
        if (`val` < 0x33000000) {
            return sign
        }
        `val` = (fbits and 0x7fffffff) ushr 23
        return sign or (((((fbits and 0x7fffff) or 0x800000)
                + (0x800000 ushr (`val` - 102)))) ushr (126 - `val`))
    }
}