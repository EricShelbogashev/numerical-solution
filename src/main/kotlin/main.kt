@file:Suppress("FunctionName", "LocalVariableName", "SpellCheckingInspection")

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin


data class Grid(val a: Double, val b: Double, val n: Int) : Iterable<Pair<Int, Double>> {
    val h: Double

    init {
        if (b <= a) throw IllegalArgumentException("правая граница сетки не должна быть меньше левой границы")
        if (n < 2) throw IllegalArgumentException("количество точек сетки не должно меньше двух")
        h = (b - a) / (n - 1)
    }

    override fun iterator(): Iterator<Pair<Int, Double>> {
        return object : Iterator<Pair<Int, Double>> {
            private var j: Int = 0
            private var jEnd: Int = n

            override fun hasNext(): Boolean = j < jEnd

            override fun next(): Pair<Int, Double> {
                if (j >= jEnd) throw NoSuchElementException("пройдены все точки сетки")

                val res = a + (j * h)
                j++
                return j to res
            }
        }
    }

    val size: Int = n
}

interface Iteration {
    /**
     * Произвести итерацию.
     *
     * @param j
     *       Шаг, к которому применяется итерация.
     * @param h
     *       Длина шага сетки.
     * @param x
     *       Точка, в которой производится итерация.
     * @param y
     *       Предыдущие итерации, от которых зависит текущая, в порядке возрастания индексов.
     *
     * @return y_{j+1}
     */
    fun make(j: Int, h: Double, x: Double, vararg y: Double): Double
}

abstract class AbstractIteration : Iteration {
    override fun make(j: Int, h: Double, x: Double, vararg y: Double): Double {
        if (j <= 0) {
            throw IllegalArgumentException("итерация должна производиться в первой и более точках")
        }

        return makeImpl(j, h, x, y)
    }

    abstract fun makeImpl(j: Int, h: Double, x: Double, y: DoubleArray): Double
}

class FirstSchemaIteration : AbstractIteration() {

    private fun g(x: Double): Double = exp(x) * cos(x)

    override fun makeImpl(j: Int, h: Double, x: Double, y: DoubleArray): Double {
        if (y.size != 1) {
            throw IllegalArgumentException("новая итерация зависит от прошлой и должна владеть y_{j-1}")
        }

        val y_prev = y[0]
        return g(x) * h + y_prev
    }

}

class ExactSolutionIteration : AbstractIteration() {
    override fun makeImpl(j: Int, h: Double, x: Double, y: DoubleArray): Double {
        return 0.5 * exp(x) * (sin(x) + cos(x))
    }
}

object Suggester {
    fun suggest(a: Double, b: Double, h: Double): Int {
        val n = ((b - a) / h).toInt()
        val actualH = (b - a) / n

        return if (actualH < h) {
            n + 1
        } else {
            n
        }
    }
}

fun main() {
    // Условие задачи Коши первого рода.
    val x_0 = 0.0
    val y_0 = 0.0

    // Параметры итерирования
    val a = x_0
    val b = 15.0
    val h = 1.0
    val n = Suggester.suggest(a, b, h)

    //
    val iteration = FirstSchemaIteration()
    val exactIteration = ExactSolutionIteration()
    val grid = Grid(a, b, n)
    val steps: MutableList<Double> = mutableListOf(y_0)
    val exactSteps: MutableList<Double> = mutableListOf(y_0)

    // Вычисление
    for ((j, x) in grid) {
        if (j == 0) continue

        steps.add(
            iteration.make(j, grid.h, x, steps[j - 1])
        )
        exactSteps.add(
            exactIteration.make(j, grid.h, x, steps[j - 1])
        )
    }

    // Вывод на экран
    val sb = StringBuilder()
    val maxLength = steps.maxOf { t -> t.toString().length }

    for (i in 0..grid.size) {
        val leftColumn = steps[i]
        val rightColumn = exactSteps[i]

        val padding = " ".repeat(maxLength - leftColumn.toString().length)

        sb.append("$leftColumn$padding : $rightColumn\n")
    }

    println(sb)
}