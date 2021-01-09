import java.awt.BorderLayout.CENTER
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Color.WHITE
import java.awt.Color.BLACK
import java.awt.event.KeyEvent
import javax.swing.border.EmptyBorder
import java.io.*
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

// todo renderer -> set pixel etc. - wrapping and xor-ing behaviours

class Chip8(rom: InputStream) {
    var paused = false
    var shouldDraw = false

    private val memory = IntArray(4096)
    private val registers = IntArray(16)

    private var indexRegister = 0
    private var programCounter = 0x00FF

    private var delayTimer = 0
    private var soundTimer = 0

    private val stack = ArrayList<Int>(16)

    val keys = ByteArray(16)

    val graphicsMemory = Array(64) { ByteArray(32) }

    init {
        // load sprites into memory
// Array of hex values for each sprite. Each sprite is 5 bytes.
        // The technical reference provides us with each one of these values.
        val sprites = arrayOf(
                0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
                0x20, 0x60, 0x20, 0x20, 0x70, // 1
                0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
                0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
                0x90, 0x90, 0xF0, 0x10, 0x10, // 4
                0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
                0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
                0xF0, 0x10, 0x20, 0x40, 0x40, // 7
                0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
                0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
                0xF0, 0x90, 0xF0, 0x90, 0x90, // A
                0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
                0xF0, 0x80, 0x80, 0x80, 0xF0, // C
                0xE0, 0x90, 0x90, 0x90, 0xE0, // D
                0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
                0xF0, 0x80, 0xF0, 0x80, 0x80  // F
        )

        for (i in sprites.indices) {
            memory[i] = sprites[i]
        }

        // load rom into memory
        DataInputStream(rom).use {
            var i = 0
            while (it.available() > 0) {
                memory[0x00FF + i] = it.readUnsignedByte()
                i++
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun update(): Int {
        val opcode = memory[programCounter] shl 8 or memory[programCounter + 1]
        val x = ((opcode and 0x0F00) ushr 8)
        val y = ((opcode and 0x00F0) ushr 4)
        val n = (opcode and 0x000F)
        val nn = (opcode and 0x00FF)
        val nnn = (opcode and 0x0FFF)

        println("Hex opcode: " + Integer.toHexString(opcode))
//        println("Hex opcode: " + Integer.toHexString((opcode and 0xF000)))
        programCounter += 2

        when (opcode and 0xF000) {
            0x0000 -> {
                when (opcode and 0x00FF) {
                    0x00E0 -> {
                        // clear the screen
                        for (row in graphicsMemory.indices) {
                            for (col in graphicsMemory[row].indices) {
                                graphicsMemory[row][col] = 0
                            }
                        }
                        shouldDraw = true
                    }
                    0x00EE -> {
                        // return from subroutine
                        programCounter = stack.removeLast()
                    }
                    else -> error("Unknown opcode")
                }
            }
            0x1000 -> {
                // jump to address nnn
                programCounter = nnn
            }
            0x2000 -> {
                // calls subroutine at nnn
                stack.add(programCounter)
            }
            0x3000 -> {
                // skips next instruction if:
                if (registers[x] == nn) {
                    programCounter += 2
                }
            }
            0x4000 -> {
                // skips next instruction if:
                if (registers[x] != nn) {
                    programCounter += 2
                }
            }
            0x5000 -> {
                // skips next instruction if:
                if (registers[y] != nn) {
                    programCounter += 2
                }
            }
            0x6000 -> {
                // set vx to nn
                registers[x] = nn
            }
            0x7000 -> {
                // add nn to vx
                registers[x] += nn
            }
            0x8000 -> {
                when (opcode and 0x000F) {
                    0x0000 -> {
                        // set vx to vy
                        registers[x] = registers[y]
                    }
                    0x0001 -> {
                        // set vx to vx | vy
                        registers[x] = registers[x] or registers[y]
                    }
                    0x0002 -> {
                        // set vx to vx & vy
                        registers[x] = registers[x] and registers[y]
                    }
                    0x0003 -> {
                        // set vx to vx ^ vy
                        registers[x] = registers[x] xor registers[y]
                    }
                    0x0004 -> {
                        // add VY to VX, VF is set to 1 when there is a carry, else to 0
                        registers[x] += registers[y]

                        if (registers[x] > 0xFF) {
                            registers[0xF] = 1
                        } else {
                            registers[0xF] = 0
                        }

//                        if (registers[x] > (0xFF - registers[y])) {
//                            registers[0xF] = 1
//                        } else {
//                            registers[0xF] = 0
//                        }

                    }
                    0x0005 -> {
                        // VF is set to 0 when there is a borrow, else to 1, sub VY from VX
                        if (registers[x] > registers[y]) {
                            registers[0xF] = 1
                        } else {
                            registers[0xF] = 0
                        }

                        registers[x] -= registers[y]
                    }
                    0x0006 -> {
                        // set VF with VX least significant bit and then shift VX to the right by 1
                        registers[0xF] = (registers[x] and 1)
                        registers[x] = registers[x] ushr 1
                    }
                    0x0007 -> {
                        // VF is set to 0 when there is a borrow, else to 1, set VX to VY sub VX
                        if (registers[x] > registers[y]) {
                            registers[0xF] = 1
                        } else {
                            registers[0xF] = 0
                        }

                        // unsiged byte subtraction
                        registers[x] = (registers[y].toUByte() - registers[x].toUByte()).toUByte().toInt()
                    }
                    0x000E -> {
                        // store the most significant bit of VX in VF and then shift VX to the left by 1
                        registers[0xF] = (registers[x] ushr 7)
                        registers[x] = (registers[x] shl 1) and 0xFF
                    }
                    else -> error("Unknown opcode")
                }
            }
            0x9000 -> {
                // skips next instruction if:
                if (registers[x] != registers[y]) {
                    programCounter += 2
                }
            }
            0xA000 -> {
                // set i to NNN
                indexRegister = nnn
            }
            0xB000 -> {
                // jump to the address V0 + NNN
                programCounter = registers[0] + nnn
            }
            0xC000 -> {
                // set VX to random(0 to 255) & NN
                registers[x] = ((Math.random() * 255).roundToInt() and nn)
            }
            0xD000 -> {
                // Draws a sprite at coordinate (VX, VY) that has a width of 8
                // pixels and a height of N pixels.
                // Each row of 8 pixels is read as bit-coded starting from memory
                // location I;
                // I value doesn't change after the execution of this instruction.
                // VF is set to 1 if any screen pixels are flipped from set to unset
                // when the sprite is drawn, and to 0 if that doesn't happen
                val width = 8
                val height = n
                registers[0xF] = 0

                for (row in 0 until height) {
                    val pixel = memory[indexRegister + row]

                    for (col in 0 until width) {
                        if ((pixel and 0x80) > 0) {
                            if (graphicsMemory[registers[x] + col][registers[y] + row] == 1.toByte()) {
                                registers[0xF] = 1
                            }
                        }

                        pixel shl 1
                    }
                }

                shouldDraw = true
            }
            0xE000 -> {
                when (opcode and 0x00FF) {
                    0x009E -> {
                        if (keys[registers[x]] != 0.toByte()) {
                            programCounter += 2
                        }
                    }
                    0x00A1 -> {
                        if (keys[registers[x]] == 0.toByte()) {
                            programCounter += 2
                        }
                    }
                    else -> error("Unknown opcode")
                }
            }
            0xF000 -> {
                when (opcode and 0x00FF) {
                    0x0007 -> {
                        // set VX to delay timer
                        registers[x] = delayTimer
                    }
                    0x000A -> {
                        // pause the emulation until key is pressed
                        paused = true

                        // todo get input
                    }
                    0x0015 -> {
                        // set delay timer to VX
                        delayTimer = registers[x]
                    }
                    0x0018 -> {
                        // set sound timer to VX
                        soundTimer = registers[x]
                    }
                    0x001E -> {
                        // add VX to I
                        indexRegister += registers[x]
                    }
                    0x0029 -> {
                        // set I to the location of the sprite at Vx. It's multiplied by 5 because each sprite is 5 bytes long.
                        indexRegister = registers[x] * 5
                    }
                    0x0033 -> {
                        // store the Binary-coded decimal representation of VX
                        // at the addresses I, I plus 1, and I plus 2
                        memory[indexRegister] = registers[x] / 100
                        memory[indexRegister + 1] = (registers[x] % 100) / 10
                        memory[indexRegister + 2] = registers[x] % 10
                    }
                    0x0055 -> {
                        // stores V0 to VX in memory starting at address I
                        for (i in 0..x) {
                            memory[indexRegister + i] = registers[i]
                        }
                    }
                    0x0065 -> {
                        for (i in 0..x) {
                            registers[i] = memory[indexRegister + i]
                        }
                    }
                    else -> error("Unknown opcode")
                }
            }
            else -> error("Unknown opcode")
        }

        return opcode
    }
}

class Window(padding: Int, pixels: Array<ByteArray>, keys: ByteArray, scale: Int) : javax.swing.JFrame() {
    private val canvas = Canvas(pixels, scale)

    init {
        layout = java.awt.GridBagLayout()
        add(canvas, java.awt.GridBagConstraints())

        minimumSize = Dimension(canvas.minimumSize.width + (2 * padding), canvas.minimumSize.height + (2 * padding))
        preferredSize = Dimension(canvas.preferredSize.width + (2 * padding), canvas.preferredSize.height + (2 * padding))
        maximumSize = Dimension(canvas.maximumSize.width + (2 * padding), canvas.maximumSize.height + (2 * padding))

        addKeyListener(object : java.awt.event.KeyListener {
            val keymap = mapOf(
                49 to 0x1, // 1
                50 to 0x2, // 2
                51 to 0x3, // 3
                52 to 0xc, // 4
                81 to 0x4, // Q
                87 to 0x5, // W
                69 to 0x6, // E
                82 to 0xD, // R
                65 to 0x7, // A
                83 to 0x8, // S
                68 to 0x9, // D
                70 to 0xE, // F
                90 to 0xA, // Z
                88 to 0x0, // X
                67 to 0xB, // C
                86 to 0xF  // V
            )

            override fun keyTyped(e: KeyEvent?) {}

            override fun keyPressed(e: KeyEvent?) {
                keys[keymap.getValue(e!!.keyCode)] = 1
            }

            override fun keyReleased(e: KeyEvent?) {
                keys[keymap.getValue(e!!.keyCode)] = 0
            }
        })

        defaultCloseOperation = EXIT_ON_CLOSE
        isUndecorated = true
        setLocationRelativeTo(null)
        pack()
        isVisible = true
    }

    fun draw() {
        canvas.repaint()
    }
}

class Canvas(private val pixels: Array<ByteArray>, private val scale: Int) : javax.swing.JPanel() {
    init {
        minimumSize = Dimension(pixels.size * scale, pixels[0].size * scale)
        preferredSize = Dimension(pixels.size * scale, pixels[0].size * scale)
        maximumSize = Dimension(pixels.size * scale, pixels[0].size * scale)
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)

        for (col in pixels.indices) {
            for (row in pixels[col].indices) {
                g?.color = if (pixels[col][row] == 1.toByte()) WHITE else BLACK
                g?.fillRect(col * scale, row * scale, scale, scale)
            }
        }
    }
}

fun main() {
    val chip8 = Chip8(FileInputStream("src/main/resources/pong.ch8"))
    val window = Window(25, chip8.graphicsMemory, chip8.keys, 8)
//    val keys = ByteArray(16)
//    val gfx = Array(64) { ByteArray(32) }
//    val window = Window(25, gfx, keys, 8)

    val fps = 2
    val frameTime = 1000 / fps
    var lastDrawCall = System.currentTimeMillis()
//    var frameCount = 0
//    val programStart = System.currentTimeMillis()

    while (true) {
        val currentTime = System.currentTimeMillis()
        chip8.update()

//        if (chip8.shouldDraw)

        if ((currentTime - lastDrawCall) > frameTime) {
//            println("$frameCount Draw, time from the program start: ${currentTime - programStart}")
//            frameCount++
            window.draw()
            lastDrawCall = currentTime
        }
    }
}

