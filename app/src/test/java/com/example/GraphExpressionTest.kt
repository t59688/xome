package com.example

import com.example.calculator.graph.GraphExpression
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class GraphExpressionTest {
    private fun eval(input: String, x: Double = 0.0, y: Double = 0.0,
                     u: Double = 0.0, t: Double = 0.0, a: Double = 1.0): Double =
        GraphExpression.compile(input).getOrThrow()
            .evaluate(GraphExpression.Variables(x,y,u,t,a))

    @Test fun standardExpressions() {
        assertEquals(7.0, eval("1+2*3"), 1e-9)
        assertEquals(512.0, eval("2^3^2"), 1e-9)
        assertEquals(-9.0, eval("-x^2",x=3.0),1e-9)
        assertEquals(0.25, eval("x^-2",x=2.0),1e-9)
        assertEquals(2*Math.PI, eval("2π"),1e-9)
        assertEquals(12.0, eval("2(x+3)",x=3.0),1e-9)
        assertEquals(9.0, eval("1.2e1-3"),1e-9)
        assertEquals(0.0, eval("sin(pi/2+t)",t=PI/2),1e-9)
        assertEquals(10.0, eval("a*x + y",x=4.0,y=2.0,a=2.0),1e-9)
        assertEquals(-2.0, eval("min(x,y)",x=-2.0,y=3.0),1e-9)
        assertEquals(3.0, eval("max(x,y)",x=-2.0,y=3.0),1e-9)
        assertEquals(1.0, eval("sqrt(4)-abs(-1)"),1e-9)
        assertEquals(1.0, eval("cos(theta)",u=0.0),1e-9)
    }

    @Test fun invalidOrUnboundedExpressionsFailWithoutHanging() {
        for (input in listOf("", "x+%", "foo(x)", "sin(", "sin()", "a^", "a)", "min(x)",
                             "x".repeat(193), "(".repeat(42)+"1"+")".repeat(42))) {
            assertTrue("Should reject " + input.take(25), GraphExpression.compile(input).isFailure)
        }
    }

    @Test fun singularitiesAreNotConvertedToDrawingCoordinates() {
        assertTrue(eval("1/(x-1)",x=1.0).isInfinite())
        assertTrue(eval("sqrt(x)",x=-1.0).isNaN())
    }

    @Test fun repeatedEvaluationDoesNotChangeCompiledExpression() {
        val compiled = GraphExpression.compile("sin(a*x+t)+y").getOrThrow()
        val vars = GraphExpression.Variables()
        for (i in 0 until 5000) {
            vars.x=i/50.0
            vars.t=i/100.0
            vars.a=1.7
            assertTrue(compiled.evaluate(vars).isFinite())
        }
    }
}
