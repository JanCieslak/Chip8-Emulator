import org.junit.Test
import java.lang.reflect.Field
import kotlin.test.assertEquals

class Tests {
    private fun executeOpcode(opcode: Int): Int {
        val array = ByteArray(2)
        array[1] = (opcode and 0xFF).toByte()
        array[0] = ((opcode shr 8) and 0xFF).toByte()

        val chip8 = Chip8(array.inputStream())
        return 0x0000FFFF and chip8.update()
    }

    private fun constructWithOpcode(opcode: Int): Chip8 {
        val array = ByteArray(2)
        array[1] = (opcode and 0xFF).toByte()
        array[0] = ((opcode shr 8) and 0xFF).toByte()

        return Chip8(array.inputStream())
    }

    private fun constructWithOpcodes(opcodes: IntArray): Chip8 {
        val array = ByteArray(opcodes.size * 2)
        var i = 0
        var k = 0
        while (i < array.size) {
            array[i + 1] = (opcodes[k] and 0xFF).toByte()
            array[i + 0] = ((opcodes[k] shr 8) and 0xFF).toByte()
            i += 2
            k += 1
        }

        return Chip8(array.inputStream())
    }

    private fun getField(chip8: Chip8, name: String): Field {
        val field = chip8::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field
    }

    @Test
    fun testOpcodeReading() {
        val opcode = executeOpcode(0x00E0)
//        println("Opcode: " + Integer.toHexString(opcode))
        assertEquals(0xE0, opcode)
    }

    //// Opcodes
    @Test
    fun `test 0x00E0 - clearing the screen`() {
        val chip8 = constructWithOpcode(0x00E0)
        chip8.graphicsMemory[0][0] = 1

        assertEquals(1, chip8.graphicsMemory[0][0])
        assertEquals(0x00E0, chip8.update())
        assertEquals(0, chip8.graphicsMemory[0][0])
    }

    @Test
    fun `test 0x00EE and 0x2000 - calling and returning from a subroutine`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x2101, 0x00EE))

        val programCounter = getField(chip8, "programCounter")
        val stack = getField(chip8, "stack")

        assertEquals(0, (stack.get(chip8) as ArrayList<*>).size)
        assertEquals(0xFF, programCounter.getInt(chip8))

        assertEquals(0x2101, chip8.update())
        assertEquals(257, programCounter.getInt(chip8))
        assertEquals(1, (stack.get(chip8) as ArrayList<*>).size)

        assertEquals(0x00EE, chip8.update())
        assertEquals(257, programCounter.getInt(chip8))
        assertEquals(0, (stack.get(chip8) as ArrayList<*>).size)
    }

    @Test
    fun `test 0x1000 - jump to address`() {
        val chip8 = constructWithOpcode(0x112C)

        val programCounter = getField(chip8, "programCounter")

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(300, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0x3000 - skip next instruction if VX == NN`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x300F, 0x300F))

        val programCounter = getField(chip8, "programCounter")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        registers[0] = 0xF

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0x4000 - skip next instruction if VX != NN`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x400F, 0x400F))

        val programCounter = getField(chip8, "programCounter")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(255, programCounter.getInt(chip8))
        registers[0] = 0xF
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        registers[0] = 0

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0x5000 - skip next instruction if VY != NN`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x5000, 0x5000))

        val programCounter = getField(chip8, "programCounter")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        registers[0] = 0xF

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0x6000 - set VX to NN`() {
        val chip8 = constructWithOpcode(0x600F)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0])
        chip8.update()
        assertEquals(0xF, registers[0])
    }

    @Test
    fun `test 0x7000 - add NN to VX`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x700F, 0x700F))

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0])
        chip8.update()
        assertEquals(15, registers[0])
        chip8.update()
        assertEquals(30, registers[0])
    }

    @Test
    fun `test 0x8000 - set VX to VY`() {
        val chip8 = constructWithOpcode(0x8010)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        registers[1] = 0xF

        assertEquals(0, registers[0])
        chip8.update()
        assertEquals(0xF, registers[0])
    }

    @Test
    fun `test 0x8001 - set VX to (VX or VY)`() {
        val chip8 = constructWithOpcode(0x8011)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0])

        registers[0] = 0xF0
        registers[1] = 0xF

        chip8.update()
        assertEquals(0xFF, registers[0])
    }

    @Test
    fun `test 0x8002 - set VX to (VX and VY)`() {
        val chip8 = constructWithOpcode(0x8012)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0])

        registers[0] = 0xF0
        registers[1] = 0xFF

        chip8.update()
        assertEquals(0xF0, registers[0])
    }

    @Test
    fun `test 0x8003 - set VX to (VX xor VY)`() {
        val chip8 = constructWithOpcode(0x8013)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0])

        registers[0] = 0xF00
        registers[1] = 0xFF0

        chip8.update()
        assertEquals(0x0F0, registers[0])
    }

    @Test
    fun `test 0x8004 - add VY to VX, VF is set to 1 when there is a carry, else to 0`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x8344, 0x8344))

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0xF])

        registers[3] = 2
        registers[4] = 4

        chip8.update()
        assertEquals(0, registers[0xF])

        registers[3] = 0xF0
        registers[4] = 0xF0

        chip8.update()
        assertEquals(1, registers[0xF])
    }

    @Test
    fun `test 0x8005 - sub VY from VX, VF is set to 0 when there is a borrow, else to 1`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x8345, 0x8345))

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0xF])

        registers[3] = 2
        registers[4] = 4

        chip8.update()
        assertEquals(0, registers[0xF])

        registers[3] = 4
        registers[4] = 2

        chip8.update()
        assertEquals(1, registers[0xF])
    }

    @Test
    fun `test 0x8006 - set VF with VX least significant bit and then shift VX to the right by 1`() {
        val chip8 = constructWithOpcode(0x8006)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0xF])
        registers[0] = 0b111 // 7

        chip8.update()
        assertEquals(1, registers[0xF])
        assertEquals(0b11, registers[0])
    }

    @Test
    fun `test 0x8007 - VF is set to 0 when there is a borrow, else to 1, set VX to VY sub VX`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x8017, 0x8017))

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0xF])
        registers[0] = 2
        registers[1] = 5

        chip8.update()
        assertEquals(0, registers[0xF])
        assertEquals(3, registers[0])

        registers[0] = 5
        registers[1] = 2

        chip8.update()
        assertEquals(1, registers[0xF])
        assertEquals(253, registers[0])
    }

    @Test
    fun `test 0x800E - store the most significant bit of VX in VF and then shift VX to the left by 1`() {
        val chip8 = constructWithOpcode(0x800E)

        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, registers[0xF])
        registers[0] = 0b10001000

        chip8.update()
        assertEquals(1, registers[0xF])
        assertEquals(0b00010000, registers[0])
    }

    @Test
    fun `test 0x9000 - skip next instruction if VX != VY`() {
        val chip8 = constructWithOpcodes(intArrayOf(0x9010, 0x9010))

        val programCounter = getField(chip8, "programCounter")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        registers[0] = 0
        registers[1] = 0

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        registers[1] = 1

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0xA000 - set indexRegister to NNN`() {
        val chip8 = constructWithOpcode(0xA010)

        val indexRegister = getField(chip8, "indexRegister")

        assertEquals(0, indexRegister.getInt(chip8))
        chip8.update()
        assertEquals(16, indexRegister.getInt(chip8))
    }

    @Test
    fun `test 0xB000 - jump to the address V0 + NNN`() {
        val chip8 = constructWithOpcode(0xB010)

        val programCounter = getField(chip8, "programCounter")

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(16, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0xC000 - set VX to random(0, 255) & NN`() {
        TODO()
    }

    @Test
    fun `test 0xE09E - skip if key is pressed`() {
        val chip8 = constructWithOpcodes(intArrayOf(0xE09E, 0xE09E))

        val programCounter = getField(chip8, "programCounter")
        val keysField = getField(chip8, "keys")
        val keys = keysField.get(chip8) as ByteArray

        keys[0] = 0

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        keys[0] = 1

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0xE0A1 - skip if key is not pressed`() {
        val chip8 = constructWithOpcodes(intArrayOf(0xE0A1, 0xE0A1))

        val programCounter = getField(chip8, "programCounter")
        val keysField = getField(chip8, "keys")
        val keys = keysField.get(chip8) as ByteArray

        keys[0] = 1

        assertEquals(255, programCounter.getInt(chip8))
        chip8.update()
        assertEquals(257, programCounter.getInt(chip8))

        keys[0] = 0

        chip8.update()
        assertEquals(261, programCounter.getInt(chip8))
    }

    @Test
    fun `test 0xF007 - set VX to delay timer`() {
        val chip8 = constructWithOpcode(0xF007)

        val delayTimer = getField(chip8, "delayTimer")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        delayTimer.setInt(chip8, 4)
        assertEquals(0, registers[0])
        chip8.update()
        assertEquals(4, registers[0])
    }

    @Test
    fun `test 0xF00A - TODO`() {
        TODO()
    }

    @Test
    fun `test 0xF015 - set delay timer to VX`() {
        val chip8 = constructWithOpcode(0xF015)

        val delayTimer = getField(chip8, "delayTimer")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        registers[0] = 4
        assertEquals(0, delayTimer.getInt(chip8))
        chip8.update()
        assertEquals(4, delayTimer.getInt(chip8))
    }

    @Test
    fun `test 0xF018 - set sound timer to VX`() {
        val chip8 = constructWithOpcode(0xF018)

        val soundTimer = getField(chip8, "soundTimer")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        registers[0] = 4
        assertEquals(0, soundTimer.getInt(chip8))
        chip8.update()
        assertEquals(4, soundTimer.getInt(chip8))
    }

    @Test
    fun `test 0xF01E - add VX to indexRegister`() {
        val chip8 = constructWithOpcode(0xF01E)

        val indexRegister = getField(chip8, "indexRegister")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        registers[0] = 4
        assertEquals(0, indexRegister.getInt(chip8))
        chip8.update()
        assertEquals(4, indexRegister.getInt(chip8))
    }

    @Test
    fun `test 0xF029 - set indexRegister to VX x 5`() {
        val chip8 = constructWithOpcode(0xF029)

        val indexRegister = getField(chip8, "indexRegister")
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, indexRegister.getInt(chip8))
        assertEquals(0, registers[0])
        registers[0] = 2
        chip8.update()
        assertEquals(10, indexRegister.getInt(chip8))
    }

    @Test
    fun `test 0xF033 - store binary coded decimal`() {
        val chip8 = constructWithOpcode(0xF033)

        val indexRegister = getField(chip8, "indexRegister")
        val memoryField = getField(chip8, "memory")
        val memory = memoryField.get(chip8) as IntArray
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, indexRegister.getInt(chip8))
        assertEquals(0, memory[0])
        assertEquals(0, memory[1])
        assertEquals(0, memory[2])
        
        registers[0] = 123
        chip8.update()

        assertEquals(1, memory[0])
        assertEquals(2, memory[1])
        assertEquals(3, memory[2])
    }

    @Test
    fun `test 0xF055 - store V0 to VX in memory starting at indexRegister`() {
        val chip8 = constructWithOpcode(0xF155)

        val indexRegister = getField(chip8, "indexRegister")
        val memoryField = getField(chip8, "memory")
        val memory = memoryField.get(chip8) as IntArray
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, indexRegister.getInt(chip8))
        assertEquals(0, memory[10])
        assertEquals(0, memory[11])

        registers[0] = 3
        registers[1] = 6
        indexRegister.set(chip8, 10)
        chip8.update()

        assertEquals(10, indexRegister.getInt(chip8))
        assertEquals(3, memory[indexRegister.getInt(chip8) + 0])
        assertEquals(6, memory[indexRegister.getInt(chip8) + 1])
    }

    @Test
    fun `test 0xF065 - store from 0 to x in registers from memory`() {
        val chip8 = constructWithOpcode(0xF165)

        val indexRegister = getField(chip8, "indexRegister")
        val memoryField = getField(chip8, "memory")
        val memory = memoryField.get(chip8) as IntArray
        val registersField = getField(chip8, "registers")
        val registers = registersField.get(chip8) as IntArray

        assertEquals(0, indexRegister.getInt(chip8))

        indexRegister.set(chip8, 10)
        memory[10] = 3
        memory[11] = 6
        chip8.update()

        assertEquals(10, indexRegister.getInt(chip8))
        assertEquals(3, memory[indexRegister.getInt(chip8)])
        assertEquals(6, memory[indexRegister.getInt(chip8) + 1])
        assertEquals(3, registers[0])
        assertEquals(6, registers[1])
    }
}
