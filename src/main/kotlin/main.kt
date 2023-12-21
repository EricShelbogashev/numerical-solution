@file:Suppress("LocalVariableName", "PropertyName")

import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.math.*


data class Grid(val a: Double, val b: Double, val n: Int) : Iterable<Pair<Int, Double>> {
    val h: Double

    init {
        if (b <= a) throw IllegalArgumentException("правая граница сетки не должна быть меньше левой границы")
        if (n < 2) throw IllegalArgumentException("количество точек сетки не должно меньше двух")
        h = (b - a) / (n)
    }

    override fun iterator(): Iterator<Pair<Int, Double>> {
        return object : Iterator<Pair<Int, Double>> {
            private var j: Int = 0
            private var jEnd: Int = n

            override fun hasNext(): Boolean = j < jEnd

            override fun next(): Pair<Int, Double> {
                if (!hasNext()) throw NoSuchElementException("пройдены все точки сетки")

                val res = a + (j * h)
                val computed = j to res
                j++
                return computed
            }
        }
    }

    operator fun get(j: Int): Double {
        if (j >= n || j < 0) throw IllegalArgumentException("выход за пределы сетки")
        return a + (j * h)
    }

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
     *       Начальные условия для схемы аппроксимации. Допустим, (0, y_0) для схемы "разность вперед" 1 порядка.
     *
     * @return Отображение x_{j} -> y_{j}.
     */
    fun compute(j: Int, grid: Grid, conditions: Conditions): Double {
        if (j <= conditions.size - 1) {
            throw IllegalArgumentException("итерация не должна производиться в точках условий")
        } else if (j >= grid.size) {
            throw IllegalArgumentException("не достаточно точек на сетке для совершения операции")
        }

        return computeImpl(j, grid, conditions)
    }

    /**
     * При реализации необходимо добавить условия, которые обязаны быть для реализации вычисления.
     */
    protected abstract fun computeImpl(j: Int, grid: Grid, conditions: Conditions): Double

    /**
     * @param grid
     *       Сетка, на которой производится вычисление.
     * @param conditions
     *       Предопределенные, начальные члены рекурентного соотношения. Должны быть совместимы с сеткой.
     */
    private fun iterator(grid: Grid, conditions: Conditions) =
        object : Iterator<Pair<Int, Double>> {
            private val steps = HashMap(conditions)
            private val gridIterator = grid.iterator()

            init {
                var i = steps.size
                while (i > 0 && gridIterator.hasNext()) {
                    gridIterator.next()
                    i--
                }
            }

            override fun hasNext(): Boolean = gridIterator.hasNext()

            override fun next(): Pair<Int, Double> {
                if (!hasNext()) {
                    throw NoSuchElementException("просчитаны все точки на сетке")
                }
                val (j, _) = gridIterator.next()
                val value = compute(j, grid, steps)
                steps[j] = value
                return j to value
            }

        }

    fun stream(grid: Grid, conditions: Conditions): Stream<Pair<Int, Double>> =
        StreamSupport.stream(
            { Spliterators.spliteratorUnknownSize(iterator(grid, conditions), Spliterator.ORDERED) },
            Spliterator.ORDERED,
            false
        )
}

// y_{j} = g_{j}*h + y_{j-1}
class FirstSchema : AbstractSchema() {

    private fun g(x: Double): Double = exp(x) * cos(x)

    /**
     * Необходимо условие (x_0, y_0). Ожидается, что j >= 1.
     */
    override fun computeImpl(j: Int, grid: Grid, conditions: Conditions): Double {
        if (conditions.isEmpty() || !conditions.contains(j - 1)) {
            throw IllegalArgumentException("новая итерация y_{j} зависит от прошлой и должна владеть y_{j-1}")
        }

        val `x_{j-1}`: Double = grid[j - 1]
        val `y_{j-1}`: Double = conditions[j - 1]!!
        return g(`x_{j-1}`) * grid.h + `y_{j-1}`
    }

}

// y_{j} = g_{j - 1} * 2 * h + y_{j-2}
class SecondSchema : AbstractSchema() {

    private fun g(x: Double): Double = exp(x) * cos(x)

    /**
     * Необходимо условие (0, y_0), (1, y_1). Ожидается, что j >= 2.
     */
    override fun computeImpl(j: Int, grid: Grid, conditions: Conditions): Double {
        if (conditions.size < 2 || !conditions.contains(j - 2)) {
            throw IllegalArgumentException("новая итерация y_{j} зависит от прошлой и должна владеть y_{j-2}")
        }

        val `x_{j-1}`: Double = grid[j - 1]
        val `y_{j-2}`: Double = conditions[j - 2]!!
        return g(`x_{j-1}`) * grid.h * 2 + `y_{j-2}`
    }

}

// y(x) = 1/2 (-e^a sin(a) - e^a cos(a) + 2 b + e^x sin(x) + e^x cos(x))
class ExactSolutionSchema : AbstractSchema() {

    override fun computeImpl(j: Int, grid: Grid, conditions: Conditions): Double {
        if (conditions.isEmpty() || !conditions.containsKey(0)) {
            throw IllegalArgumentException("новая итерация y_{j} зависит от начальных данных и должна владеть y_{0}")
        }

        val x0 = grid.a
        val y0 = conditions[0]!!
        val x = grid[j]
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

/**
 * Вычислитель схем.
 */
class GridSchemeEvaluator(
    a: Double,
    b: Double,
    h: Double,
    private val approx: Problem,
    private val exact: Problem,
    private val denseConditions: Conditions
) : Iterable<GridSchemeEvaluator.EvaluatedRow> {

    private val grid1: Grid
    private val grid2: Grid
    private var evaluated: Array<EvaluatedRow>? = null

    init {
        val n1 = Suggester.suggest(a, b, h)
        val n2 = n1 * 3 // Берем в 3 раза больше точек.
        grid1 = Grid(a, b, n1)
        grid2 = Grid(a, b, n2)
    }

    data class Problem(val schema: AbstractSchema, val conditions: Conditions)

    data class EvaluatedRow(
        val j: Int?,
        val x_j: Double?,
        val `y_{ex}(x_j)`: Double?,
        val `(delta)y_{h_1}(x_j)`: Double?,
        val `(delta)y_{h_2}(x_j)`: Double?,
        val p_j: Double?
    ) {
        override fun toString(): String {
            return "EvaluatedRow(" +
                    "\n  j = $j, " +
                    "\n  x_j = $x_j, " +
                    "\n  y_{ex}(x_j) = $`y_{ex}(x_j)`," +
                    "\n  Δy_{h_1}(x_j) = $`(delta)y_{h_1}(x_j)`," +
                    "\n  Δy_{h_2}(x_j) = $`(delta)y_{h_2}(x_j)`," +
                    "\n  p_j = $p_j" +
                    "\n)"
        }
    }

    private fun compute(): Array<EvaluatedRow> {
        val run = approx.schema.stream(grid1, approx.conditions).collect(
            Collectors.toMap(
                { pair: Pair<Int, Double> -> pair.first },
                { pair: Pair<Int, Double> -> pair.second }
            )
        )
        val runExact = approx.schema.stream(grid2, denseConditions).collect(  // Тут в 3 раза больше точек.
            Collectors.toMap(
                { pair: Pair<Int, Double> -> pair.first },
                { pair: Pair<Int, Double> -> pair.second }
            )
        )
        val exact = exact.schema.stream(grid1, exact.conditions).collect(
            Collectors.toMap(
                { pair: Pair<Int, Double> -> pair.first },
                { pair: Pair<Int, Double> -> pair.second }
            )
        )
        assert((run.size == exact.size) && (run.size * 3 == exact.size))

        val array: MutableList<EvaluatedRow> = IntStream.range(0, grid1.size)
            .mapToObj { i ->
                val d1: Double? = run?.get(i)?.let { exact[i]?.minus(it)?.absoluteValue }
                val d2: Double? = runExact?.get(i)?.let { exact[i]?.minus(it)?.absoluteValue }
                return@mapToObj EvaluatedRow(
                    j = i,
                    x_j = grid1[i],
                    `y_{ex}(x_j)` = exact[i],
                    `(delta)y_{h_1}(x_j)` = d1,
                    `(delta)y_{h_2}(x_j)` = d2,
                    p_j = d2?.let { it -> d1?.div(it)?.let { log(it, 3.0) } }
                )
            }
            .filter(Objects::nonNull)
            .map { it as EvaluatedRow }
            .limit(grid1.size.toLong())
            .collect(Collectors.toList())

        return array.toTypedArray()
    }

    override fun iterator(): Iterator<EvaluatedRow> {
        if (evaluated == null) {
            evaluated = compute()
        }

        return evaluated!!.iterator()
    }

    fun get(): Stream<EvaluatedRow> =
        StreamSupport.stream(
            { Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED) },
            Spliterator.ORDERED,
            false
        )
}

typealias Conditions = Map<Int, Double>
typealias Problem = GridSchemeEvaluator.Problem

fun main() {
    val (a, b, h) = listOf(0.0, 4.0, 0.1)

    val solutionSchema = ExactSolutionSchema()
    val firstSchema = FirstSchema()
    val secondSchema = SecondSchema()

    val y_0 = 0.0

    // Для схемы первого порядка
    val conditionsI = mapOf(0 to y_0)

    // Для схемы второго порядка
    val conditionsII = conditionsI.let {
        // Вычисляем y1 для неплотной сетки h.
        val j = 1
        val n = Suggester.suggest(a, b, h)
        val grid = Grid(a, b, n)
        val y1: Double = firstSchema.compute(j, grid, conditionsI) // просчитаем условие (1, y1)
        val mutableMap = it.toMutableMap()
        mutableMap[1] = y1
        // Создаем conditionsII на основе conditionsI.
        mutableMap.toMap()
    }

    // Для плотной сетки
    val conditionsExactII = conditionsI.let {
        // Вычисляем y1 для плотной сетки h.
        val j = 1
        val n = Suggester.suggest(a, b, h) * 3
        val grid = Grid(a, b, n)
        val y1: Double = firstSchema.compute(j, grid, conditionsI) // просчитаем условие (1, y1)
        val mutableMap = it.toMutableMap()
        mutableMap[1] = y1
        // Создаем conditionsExactII на основе conditionsI.
        mutableMap.toMap()
    }

    val firstSchemaEvaluation = GridSchemeEvaluator(
        a,
        b,
        h,
        Problem(firstSchema, conditionsI),
        Problem(solutionSchema, conditionsI),
        conditionsI
    )

    firstSchemaEvaluation.get().forEach(System.out::println)

    val secondSchemaEvaluation = GridSchemeEvaluator(
        a,
        b,
        h,
        Problem(secondSchema, conditionsII),
        Problem(solutionSchema, conditionsI),
        conditionsExactII
    )

    secondSchemaEvaluation.get().forEach(System.out::println)
}