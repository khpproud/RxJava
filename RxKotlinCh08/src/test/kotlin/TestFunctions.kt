import org.junit.Test
import java.util.*
import kotlin.test.*

class TestFunctions {
    @Test
    fun `expected block evaluation`() {
        expect(10, {
            val x = 5
            val y = 2
            x * y
        })
    }

    @Test
    fun `assert illegal value`() {
        assertNotEquals(-1, Random().nextInt(1))
    }

    @Test
    fun `assert true boolean value`() {
        assertTrue { 2 < 5 }
    }

    @Test
    fun `assert false boolean value`() {
        assertFalse { 2 > 5 }
    }

    @Test
    fun `assert that passed value is null`() {
        assertNull(null)
    }

    @Test
    fun `assert that passed value is not null`() {
        assertNotNull(10)
    }
}