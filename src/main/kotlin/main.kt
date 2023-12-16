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

    operator fun get(j: Int): Double = a + (j * h)

    val size: Int = n
}

abstract class AbstractSchema {

    /**
     * Вычислить значение в точке x.
     * @param j
     *       Номер текущей точки на сетке.
     *       То есть для нахождения y_{j} = y_{j - 1} + g_{j - 1} * h необходимо передать j.
     * @param grid
     *       Сетка, на которой происходит вычисление функции.
     * @param conditions
     *       Начальные условия для схемы аппроксимации. Допустим, y_0 для схемы "разность вперед" 1 порядка.
     *
     * @return Отображение x_{j} -> y_{j}.
     */
    fun compute(j: Int, grid: Grid, conditions: Map<Double, Double>): Double {
        if (j < conditions.size) {
            throw IllegalArgumentException("итерация не должна производиться в точках условий")
        }

        return computeImpl(j, grid, conditions)
    }

    /**
     * При реализации необходимо добавить условия, которые обязаны быть для реализации вычисления.
     */
    abstract fun computeImpl(j: Int, grid: Grid, conditions: Map<Double, Double>): Double

    /**
     * @param grid
     *       Сетка, на которой производится вычисление.
     * @param conditions
     *       Предопределенные, начальные члены рекурентного соотношения. Должны быть совместимы с сеткой.
     */
    fun iterator(grid: Grid, conditions: Map<Double, Double>) =
        object : Iterator<Double> {
            private val steps = HashMap<Double, Double>(conditions)
            private val gridIterator = grid.iterator()

            init {
                var i = steps.size - 1
                while (i > 0 && gridIterator.hasNext()) {
                    gridIterator.next()
                    i--
                }
            }

            override fun hasNext(): Boolean = gridIterator.hasNext()

            override fun next(): Double {
                val (j, _) = gridIterator.next()
                val value = compute(j, grid, steps)
                val key = grid[j]
                steps[key] = value
                return value
            }

        }
}

class FirstSchema : AbstractSchema() {

    private fun g(x: Double): Double = exp(x) * cos(x)

    /**
     * Необходимо условие (x_0, y_0). Ожидается, что j >= 1.
     */
    override fun computeImpl(j: Int, grid: Grid, conditions: Map<Double, Double>): Double {
        if (conditions.isEmpty() || !conditions.contains(grid[j - 1])) {
            throw IllegalArgumentException("новая итерация y_{j} зависит от прошлой и должна владеть y_{j-1}")
        }

        val `x_{j-1}`: Double = grid[j - 1]
        val `y_{j-1}`: Double = conditions[grid[j - 1]]!!
        return g(`x_{j-1}`) * grid.h + `y_{j-1}`
    }

}

//class SecondSchemaSchema : AbstractSchema() {
//
//    private fun g(x: Double): Double = exp(x) * cos(x)
//
//    override fun computeImpl(j: Int, grid: Grid, conditions: Map<Double, Double>): Double {
//        if (conditions.size <= 2 || !conditions.contains(grid[j - 2])) {
//            throw IllegalArgumentException("новая итерация y_{j} зависит от прошлой и должна владеть y_{j-2}")
//        }
//
//
//    }
//
//}

// y(x) = 1/2 (-e^a sin(a) - e^a cos(a) + 2 b + e^x sin(x) + e^x cos(x))
class ExactSolutionSchema : AbstractSchema() {

    override fun computeImpl(j: Int, grid: Grid, conditions: Map<Double, Double>): Double {
        if (conditions.isEmpty() || !conditions.containsKey(grid.a)) {
            throw IllegalArgumentException("новая итерация y_{j} зависит от начальных данных и должна владеть y_{0}")
        }

        val x0 = grid.a
        val y0 = conditions[x0]!!
        val x = grid[j - 1]
        return 0.5 * ((-exp(x0) * sin(x0)) + (-exp(x0) * cos(x0)) + (2 * y0) + (exp(x) * sin(x)) + (exp(x) * cos(x)))
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
    val (a, b, h) = listOf(0.0, 4.0, 0.01)

    val n = Suggester.suggest(a, b, h)
    val grid = Grid(a, b, n)

    val solutionSchema = ExactSolutionSchema()
    val firstSchema = FirstSchema()

    val conditionsI = mapOf(0.0 to 0.0)
    val solutionSchemaIterator = solutionSchema.iterator(grid, conditionsI)
    val firstSchemaIterator = firstSchema.iterator(grid, conditionsI)

    while (solutionSchemaIterator.hasNext() && firstSchemaIterator.hasNext()) {
        val solution_y_j = solutionSchemaIterator.next()
        val firstSchema_y_j = firstSchemaIterator.next()
        println("$solution_y_j \t $firstSchema_y_j")
    }
}