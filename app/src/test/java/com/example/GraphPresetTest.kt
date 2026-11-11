package com.example

import com.example.calculator.graph.GraphExpression
import com.example.calculator.graph.GraphMode
import com.example.calculator.graph.GraphPresets
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class GraphPresetTest {
    @Test fun allPresetsCompileAndProduceRealGeometry() {
        val vars = GraphExpression.Variables(a=1.0)
        for (mode in GraphMode.entries) {
            for (preset in GraphPresets.forMode(mode)) {
                val expressions = preset.expressions.expressions().take(mode.fieldCount)
                for (expression in expressions) {
                    if (expression.isBlank()) continue
                    val compiled = GraphExpression.compile(expression).getOrElse {
                        throw AssertionError(mode.name + " / " + preset.nameEn + ": " + expression, it)
                    }
                    var finiteCount=0
                    repeat(80) { i ->
                        vars.x = -4.0 + i/10.0
                        vars.y = -3.0 + i/15.0
                        vars.u = 4*PI*i/80.0
                        vars.t = .75
                        if (compiled.evaluate(vars).isFinite()) finiteCount++
                    }
                    assertTrue(mode.name + ":" + preset.nameEn + " " + expression, finiteCount >= 65)
                }
            }
        }
    }
    @Test fun modesAndParametersHaveRealDefaults() {
        assertEquals(4, GraphPresets.modes2D.size)
        assertEquals(2, GraphPresets.modes3D.size)
        assertTrue(GraphPresets.forMode(GraphMode.CARTESIAN).any { it.animate })
        assertTrue(GraphPresets.forMode(GraphMode.SURFACE).any { it.animate })
    }
}
