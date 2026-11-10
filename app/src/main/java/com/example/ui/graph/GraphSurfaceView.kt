package com.example.ui.graph

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.calculator.graph.GraphExpression
import com.example.calculator.graph.GraphMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** 3D graphing is rendered by OpenGL ES 2, off Compose's main/UI rendering thread. */
internal data class GraphScene(
    val mode: GraphMode,
    val expressions: List<GraphExpression?>,
    val time: Double,
    val parameter: Double
)

internal class GraphSurfaceView(context: Context) : GLSurfaceView(context) {
    private val graphRenderer = GraphGlRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                graphRenderer.zoom = (graphRenderer.zoom / detector.scaleFactor).coerceIn(2.1f, 11f)
                requestRender()
                return true
            }
        }
    )
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(graphRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScene(value: GraphScene) {
        graphRenderer.scene = value
        requestRender()
    }

    fun resetCamera() {
        graphRenderer.yaw = 0.65f
        graphRenderer.pitch = 0.55f
        graphRenderer.zoom = 5.4f
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; return true }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    graphRenderer.yaw += (event.x - lastX) * 0.007f
                    graphRenderer.pitch = (graphRenderer.pitch +
                        (event.y - lastY) * 0.005f).coerceIn(-1.30f, 1.30f)
                    requestRender()
                }
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }
}

private class GraphGlRenderer : GLSurfaceView.Renderer {
    @Volatile var scene: GraphScene? = null
    @Volatile var yaw: Float = 0.65f
    @Volatile var pitch: Float = 0.55f
    @Volatile var zoom: Float = 5.4f

    private var program = 0
    private var posLocation = 0
    private var normalLocation = 0
    private var mvpLocation = 0
    private var colorLocation = 0
    private var lineLocation = 0
    private var width = 1
    private var height = 1
    private val projection = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvp = FloatArray(16)
    private var mesh: FloatBuffer? = null
    private var meshVertexCount = 0
    private var curve: FloatBuffer? = null
    // Reuse native direct buffers while animated: do not allocate ~300 KB of direct
    // memory and wait for GC every frame.
    private var reusableMesh: FloatBuffer? = null
    private var reusableCurve: FloatBuffer? = null
    private var curveVertexCount = 0
    private var cachedScene: GraphScene? = null
    private val variables = GraphExpression.Variables()
    private val grid = makeGrid()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        posLocation = GLES20.glGetAttribLocation(program, "aPosition")
        normalLocation = GLES20.glGetAttribLocation(program, "aNormal")
        mvpLocation = GLES20.glGetUniformLocation(program, "uMvp")
        colorLocation = GLES20.glGetUniformLocation(program, "uColor")
        lineLocation = GLES20.glGetUniformLocation(program, "uLine")
        GLES20.glClearColor(0.055f, 0.073f, 0.086f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        cachedScene = null
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        val requested = scene ?: return

        if (requested !== cachedScene) {
            buildGeometry(requested)
            cachedScene = requested
        }
        val y = pitch
        val az = yaw
        val r = zoom
        val cx = (r * cos(y) * sin(az))
        val cy = (r * sin(y))
        val cz = (r * cos(y) * cos(az))
        Matrix.setLookAtM(viewMatrix, 0, cx, cy, cz, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(projection, 0, 46f,
            width.toFloat() / height.toFloat(), 0.1f, 40f)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewMatrix, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)

        // Draw floor, then surface with Z buffer. Lines are distinct, low-key visual guides.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        draw(grid, grid.limit() / 6, GLES20.GL_LINES, 0.23f, 0.30f, 0.33f, true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        mesh?.let {
            draw(it, meshVertexCount, GLES20.GL_TRIANGLES,
                0.91f, 0.61f, 0.37f, false)
        }
        curve?.let {
            GLES20.glLineWidth(3f)
            draw(it, curveVertexCount, GLES20.GL_LINES,
                0.82f, 0.67f, 0.98f, true)
        }
        GLES20.glDisableVertexAttribArray(posLocation)
        GLES20.glDisableVertexAttribArray(normalLocation)
    }

    private fun draw(
        data: FloatBuffer,
        count: Int,
        primitive: Int,
        red: Float,
        green: Float,
        blue: Float,
        isLine: Boolean
    ) {
        if (count <= 0) return
        data.position(0)
        GLES20.glVertexAttribPointer(posLocation, 3, GLES20.GL_FLOAT,
            false, 24, data)
        data.position(3)
        GLES20.glVertexAttribPointer(normalLocation, 3, GLES20.GL_FLOAT,
            false, 24, data)
        GLES20.glEnableVertexAttribArray(posLocation)
        GLES20.glEnableVertexAttribArray(normalLocation)
        GLES20.glUniform3f(colorLocation, red, green, blue)
        GLES20.glUniform1f(lineLocation, if (isLine) 1f else 0f)
        GLES20.glDrawArrays(primitive, 0, count)
    }

    private fun buildGeometry(config: GraphScene) {
        meshVertexCount = 0
        curveVertexCount = 0
        mesh = null
        curve = null
        variables.t = config.time
        variables.a = config.parameter
        if (config.mode == GraphMode.SURFACE) buildSurface(config.expressions.getOrNull(0))
        if (config.mode == GraphMode.SPACE_CURVE) {
            buildCurve(config.expressions.getOrNull(0),
                config.expressions.getOrNull(1), config.expressions.getOrNull(2))
        }
    }

    private fun buildSurface(expression: GraphExpression?) {
        if (expression == null) return
        val size = 46
        val points = FloatArray(size * size * 3)
        val valid = BooleanArray(size * size)
        var index = 0
        for (iy in 0 until size) {
            variables.y = -4.0 + iy * 8.0 / (size - 1)
            for (ix in 0 until size) {
                variables.x = -4.0 + ix * 8.0 / (size - 1)
                val z = expression.evaluate(variables)
                valid[index / 3] = z.isFinite() && abs(z) <= 30
                points[index++] = (variables.x / 4).toFloat()
                points[index++] = (if (z.isFinite()) (z / 3).coerceIn(-3.0, 3.0) else 0.0).toFloat()
                points[index++] = (variables.y / 4).toFloat()
            }
        }
        // Per-vertex finite-difference normals: smooth material shading, no triangle facets.
        val normals = FloatArray(size * size * 3)
        for (row in 0 until size) for (col in 0 until size) {
            val p = row * size + col
            val left = row * size + (col - 1).coerceAtLeast(0)
            val right = row * size + (col + 1).coerceAtMost(size - 1)
            val back = (row - 1).coerceAtLeast(0) * size + col
            val front = (row + 1).coerceAtMost(size - 1) * size + col
            val sx = (points[right*3+1] - points[left*3+1]) /
                (points[right*3] - points[left*3]).coerceAtLeast(0.0001f)
            val sz = (points[front*3+1] - points[back*3+1]) /
                (points[front*3+2] - points[back*3+2]).coerceAtLeast(0.0001f)
            val nx = -sx; val ny = 1f; val nz = -sz
            val len = sqrt(nx*nx + ny*ny + nz*nz).coerceAtLeast(1e-7f)
            normals[p*3] = nx/len
            normals[p*3+1] = ny/len
            normals[p*3+2] = nz/len
        }
        val maxVertices = (size - 1) * (size - 1) * 6
        val required = maxVertices * 6
        val out = reusableMesh?.takeIf { it.capacity() >= required }
            ?: ByteBuffer.allocateDirect(required * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().also { reusableMesh = it }
        out.clear()
        fun vertex(p: Int) {
            val base = p * 3
            out.put(points[base]); out.put(points[base + 1]); out.put(points[base + 2])
            out.put(normals[base]); out.put(normals[base + 1]); out.put(normals[base + 2])
        }
        fun tri(a: Int, b: Int, c: Int) {
            if (!valid[a] || !valid[b] || !valid[c]) return
            vertex(a); vertex(b); vertex(c)
            meshVertexCount += 3
        }
        for (j in 0 until size - 1) {
            for (i in 0 until size - 1) {
                val a = j * size + i
                tri(a, a + size, a + 1)
                tri(a + 1, a + size, a + size + 1)
            }
        }
        out.flip()
        mesh = out
    }

    private fun buildCurve(ex: GraphExpression?, ey: GraphExpression?, ez: GraphExpression?) {
        if (ex == null || ey == null || ez == null) return
        // Each adjacent finite sample becomes a line segment. In particular,
        // poles/undefined coordinates cannot be joined by an artificial bridge.
        val capacity = 1400 * 6
        val out = reusableCurve?.takeIf { it.capacity() >= capacity }
            ?: ByteBuffer.allocateDirect(capacity * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().also { reusableCurve = it }
        out.clear()
        var previousValid = false
        var previousX = 0f
        var previousY = 0f
        var previousZ = 0f
        fun put(x: Float, y: Float, z: Float) {
            out.put(x); out.put(y); out.put(z)
            out.put(0f); out.put(1f); out.put(0f)
        }
        for (index in 0 until 700) {
            variables.u = index * (4 * Math.PI) / 699
            val wx = ex.evaluate(variables)
            val wy = ey.evaluate(variables)
            val wz = ez.evaluate(variables)
            if (!wx.isFinite() || !wy.isFinite() || !wz.isFinite() ||
                maxOf(abs(wx), abs(wy), abs(wz)) > 35.0
            ) {
                previousValid = false
                continue
            }
            val x = (wx / 4.0).toFloat()
            val y = (wz / 4.0).toFloat()
            val z = (wy / 4.0).toFloat()
            if (previousValid) {
                put(previousX, previousY, previousZ)
                put(x, y, z)
                curveVertexCount += 2
            }
            previousX = x
            previousY = y
            previousZ = z
            previousValid = true
        }
        out.flip()
        curve = out
    }

    private fun makeGrid(): FloatBuffer {
        val positions = ArrayList<Float>(42 * 12)
        fun point(x: Float, y: Float, z: Float) {
            positions.add(x); positions.add(y); positions.add(z)
            positions.add(0f); positions.add(1f); positions.add(0f)
        }
        for (step in -4..4) {
            val v = step / 4f
            point(v,-1.03f,-1f); point(v,-1.03f,1f)
            point(-1f,-1.03f,v); point(1f,-1.03f,v)
        }
        // Vertical reference axis and horizontal origin lines.
        point(0f,-1.03f,0f); point(0f,1.6f,0f)
        point(-1.2f,0f,0f); point(1.2f,0f,0f)
        point(0f,0f,-1.2f); point(0f,0f,1.2f)
        val buffer = ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (value in positions) buffer.put(value)
        buffer.flip()
        return buffer
    }

    private fun compileShader(kind: Int, code: String): Int {
        val id = GLES20.glCreateShader(kind)
        GLES20.glShaderSource(id, code)
        GLES20.glCompileShader(id)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw IllegalStateException("Graph shader failed: " + log)
        }
        return id
    }

    private fun createProgram(vs: String, fs: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vertex)
        GLES20.glAttachShader(id, fragment)
        GLES20.glLinkProgram(id)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("Graph program failed: " + GLES20.glGetProgramInfoLog(id))
        return id
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform vec3 uColor;
            uniform float uLine;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying mediump vec3 vColor;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vec3 normal = normalize(aNormal);
                float light = 0.50 + 0.50 * abs(dot(normal, normalize(vec3(0.5, 1.0, 0.7))));
                float elevation = clamp((aPosition.y + 1.0) * 0.35, 0.0, 1.0);
                vColor = uColor * mix(0.65, 1.08, elevation) * light;
                if (uLine > 0.5) vColor = uColor;
                gl_PointSize = 4.0;
            }
        """
        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying mediump vec3 vColor;
            void main() {
                gl_FragColor = vec4(vColor, 1.0);
            }
        """
    }
}
