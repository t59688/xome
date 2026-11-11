package com.example.calculator.graph

enum class GraphMode(
    val zh: String,
    val en: String,
    val category: String, // "2D", "WAVE", "3D"
    val fieldCount: Int
) {
    CARTESIAN("显函数", "Functions", "2D", 3),
    WAVEFORM("物理波形", "Waveforms", "WAVE", 3),
    PARAMETRIC("参数曲线", "Parametric", "2D", 2),
    POLAR("极坐标", "Polar", "2D", 1),
    VECTOR_FIELD("向量场", "Vectors", "2D", 2),
    SURFACE("曲面", "Surface", "3D", 1),
    SPACE_CURVE("空间曲线", "3D curve", "3D", 3);

    val threeD: Boolean get() = category == "3D"
}

data class GraphDraft(
    val first: String,
    val second: String = "",
    val third: String = ""
) {
    fun expressions() = listOf(first, second, third)
    fun update(index: Int, value: String) = when (index) {
        0 -> copy(first = value)
        1 -> copy(second = value)
        else -> copy(third = value)
    }
}

data class GraphPreset(
    val nameZh: String,
    val nameEn: String,
    val expressions: GraphDraft,
    val animate: Boolean = false,
    val parameter: Float = 1f,
    val mathTag: String = "",
    val formulaDisplay: String = "",
    val insightZh: String = "",
    val insightEn: String = ""
)

object GraphPresets {
    val modes2D = listOf(GraphMode.CARTESIAN, GraphMode.PARAMETRIC, GraphMode.POLAR, GraphMode.VECTOR_FIELD)
    val modesWave = listOf(GraphMode.WAVEFORM)
    val modes3D = listOf(GraphMode.SURFACE, GraphMode.SPACE_CURVE)

    fun forMode(mode: GraphMode): List<GraphPreset> = when (mode) {
        GraphMode.CARTESIAN -> listOf(
            GraphPreset(
                nameZh = "三角基底",
                nameEn = "Trigonometry",
                expressions = GraphDraft("sin(x)", "cos(x)", ""),
                mathTag = "正交基底",
                formulaDisplay = "y₁=sin(x), y₂=cos(x)",
                insightZh = "正弦与余弦互为正交基底，相位差为 π/2，是所有周期振动与波动分析的数学基石。",
                insightEn = "Sine and cosine form orthogonal basis functions with a π/2 phase difference."
            ),
            GraphPreset(
                nameZh = "Sinc 抽样滤波",
                nameEn = "Sinc Filter",
                expressions = GraphDraft("sinc(pi*x)", "exp(-x^2/4)", ""),
                parameter = 1f,
                mathTag = "信号抽样",
                formulaDisplay = "y=sinc(πx)=sin(πx)/(πx)",
                insightZh = "理想低通滤波器的冲激响应，也是香农-奈奎斯特抽样定理完美重构模拟信号的核心插值基核。",
                insightEn = "Impulse response of an ideal brick-wall low-pass filter in Shannon sampling."
            ),
            GraphPreset(
                nameZh = "悬链线",
                nameEn = "Catenary",
                expressions = GraphDraft("a*cosh(x/a)-a", "x^2/(2*a)", ""),
                parameter = 2f,
                mathTag = "重力力学",
                formulaDisplay = "y=a·cosh(x/a)-a ≈ x²/(2a)",
                insightZh = "柔性均质绳索两端固定、在重力场中自然下垂时形成的曲线。在原点附近低阶泰勒展开近似为抛物线。",
                insightEn = "The curve assumed by an idealized hanging cable under uniform gravity."
            ),
            GraphPreset(
                nameZh = "高斯脉冲调制",
                nameEn = "Gabor Wavelet",
                expressions = GraphDraft("a*exp(-x^2/4)*cos(3*x-t)", "-a*exp(-x^2/4)", "a*exp(-x^2/4)"),
                animate = true,
                parameter = 1.2f,
                mathTag = "时频小波",
                formulaDisplay = "y=A·e^(-x²/4)·cos(3x-t)",
                insightZh = "高斯衰减包络与载波调制的乘积，在时域与频域同时达到海森堡测不准原理的最优极限分辨率。",
                insightEn = "Gabor wavelet achieving the theoretical bound of the Heisenberg uncertainty principle."
            ),
            GraphPreset(
                nameZh = "三次曲线极值",
                nameEn = "Cubic Extrema",
                expressions = GraphDraft("0.15*x^3 - x + 0.5*sin(t)", "0.45*x^2 - 1", ""),
                animate = true,
                parameter = 1f,
                mathTag = "微积分",
                formulaDisplay = "y=0.15x³-x+c, y'=0.45x²-1",
                insightZh = "经典三次多项式及其导函数（切线斜率曲线），直观展示导数零点对应原函数的极值点与拐点变化。",
                insightEn = "Cubic polynomial with its derivative curve demonstrating critical points and inflection."
            ),
            GraphPreset(
                nameZh = "双曲正切阶跃",
                nameEn = "Tanh Sigmoid",
                expressions = GraphDraft("a*tanh(x)", "1/(1+exp(-2*x))", ""),
                parameter = 1f,
                mathTag = "神经网络",
                formulaDisplay = "y=A·tanh(x), σ(x)=1/(1+e^(-2x))",
                insightZh = "以零为中心的平滑非线性饱和函数，广泛应用于早期深度学习激活函数与热力学费米-狄拉克统计。",
                insightEn = "Smooth zero-centered saturation function used in neural networks and statistical mechanics."
            )
        )

        GraphMode.WAVEFORM -> listOf(
            GraphPreset(
                nameZh = "傅里叶方波合成",
                nameEn = "Fourier Square",
                expressions = GraphDraft(
                    "(4/pi)*(sin(x-t) + sin(3*(x-t))/3 + sin(5*(x-t))/5 + sin(7*(x-t))/7)",
                    "(4/pi)*sin(x-t)",
                    "(4/pi)*(sin(x-t) + sin(3*(x-t))/3)"
                ),
                animate = true,
                parameter = 1f,
                mathTag = "谐波叠加",
                formulaDisplay = "y=(4/π)∑[sin((2k-1)(x-t))/(2k-1)]",
                insightZh = "周期方波由奇次谐波正弦波线性叠加构成。跳跃不连续处的过冲振荡即著名的吉布斯现象（过冲量约 8.95%）。",
                insightEn = "Square wave reconstructed from odd harmonics; overshoot near jumps illustrates Gibbs phenomenon."
            ),
            GraphPreset(
                nameZh = "傅里叶锯齿波",
                nameEn = "Fourier Sawtooth",
                expressions = GraphDraft(
                    "(2/pi)*(sin(x-t) - sin(2*(x-t))/2 + sin(3*(x-t))/3 - sin(4*(x-t))/4 + sin(5*(x-t))/5)",
                    "(2/pi)*sin(x-t)",
                    ""
                ),
                animate = true,
                parameter = 1f,
                mathTag = "谐波级数",
                formulaDisplay = "y=(2/π)∑[(-1)^(k+1)·sin(k(x-t))/k]",
                insightZh = "包含基波的所有奇偶整数次谐波，幅值衰减速率与阶数成反比 1/k，常用于模拟阴极示波器的时基扫描波形。",
                insightEn = "Sawtooth wave containing all integer harmonics decaying with inverse amplitude 1/k."
            ),
            GraphPreset(
                nameZh = "声学拍频现象",
                nameEn = "Acoustic Beats",
                expressions = GraphDraft(
                    "sin(4*x-t) + sin(4.5*x-1.15*t)",
                    "2*cos(0.25*x-0.075*t)",
                    "-2*cos(0.25*x-0.075*t)"
                ),
                animate = true,
                parameter = 1f,
                mathTag = "波的干涉",
                formulaDisplay = "y=sin(ω₁t)+sin(ω₂t)=2cos(Δωt/2)sin(ω̄t)",
                insightZh = "两列频率极为相近的波同向干涉叠加，产生周期性强弱交替的包络（拍频），是乐器调音与无线电差拍检波的基础。",
                insightEn = "Interference of two closely-spaced frequencies resulting in low-frequency beat envelopes."
            ),
            GraphPreset(
                nameZh = "调幅波 (AM)",
                nameEn = "Amplitude Mod",
                expressions = GraphDraft(
                    "(1 + 0.6*cos(x-0.5*t)) * cos(8*x-3*t)",
                    "1 + 0.6*cos(x-0.5*t)",
                    "-(1 + 0.6*cos(x-0.5*t))"
                ),
                animate = true,
                parameter = 1.2f,
                mathTag = "无线通信",
                formulaDisplay = "s(t)=[1 + m·cos(Ωt)]·cos(ωc·t)",
                insightZh = "经典调幅无线电广播（AM）原理：低频音频信号控制高频载波信号的瞬时振幅包络，外层虚线即为检波包络。",
                insightEn = "Amplitude modulation carrying baseband information encoded on carrier envelope."
            ),
            GraphPreset(
                nameZh = "调频波 (FM Chirp)",
                nameEn = "Frequency Mod",
                expressions = GraphDraft(
                    "sin(2*x + 1.6*sin(0.6*x-t))",
                    "cos(0.6*x-t)",
                    ""
                ),
                animate = true,
                parameter = 1f,
                mathTag = "雷达探测",
                formulaDisplay = "s(t)=sin(ωc·t + β·sin(Ωt))",
                insightZh = "瞬时频率随调制信号周期性疏密变化的波形，抗干扰能力强，广泛应用于 FM 广播、超声波成像与线性调频雷达。",
                insightEn = "Frequency modulation where instantaneous carrier frequency varies proportionally to modulating signal."
            ),
            GraphPreset(
                nameZh = "物理驻波",
                nameEn = "Standing Wave",
                expressions = GraphDraft(
                    "2*a*sin(2*x)*cos(2*t)",
                    "a*sin(2*x - 2*t)",
                    "a*sin(2*x + 2*t)"
                ),
                animate = true,
                parameter = 1f,
                mathTag = "共振谐振",
                formulaDisplay = "y=2A·sin(kx)·cos(ωt)",
                insightZh = "两列振幅与波速相同、传播方向相反的行波叠加，空间中各点振幅固定，形成永远静止的波节与振幅最大的波腹。",
                insightEn = "Standing wave from opposing traveling waves with static nodes and oscillating antinodes."
            ),
            GraphPreset(
                nameZh = "阻尼衰减波",
                nameEn = "Damped Vibration",
                expressions = GraphDraft(
                    "a*exp(-0.28*abs(x))*cos(3*x-2*t)",
                    "a*exp(-0.28*abs(x))",
                    "-a*exp(-0.28*abs(x))"
                ),
                animate = true,
                parameter = 1.3f,
                mathTag = "阻尼耗散",
                formulaDisplay = "y=A·e^(-γ|x|)·cos(kx-ωt)",
                insightZh = "由于介质摩擦阻力或电路电阻造成的能量耗散，振动物体的机械振幅按指数律迅速衰减，常见于琴弦与减震器。",
                insightEn = "Damped mechanical oscillation showing exponential amplitude dissipation over space/time."
            ),
            GraphPreset(
                nameZh = "KdV 孤立波",
                nameEn = "Soliton Wave",
                expressions = GraphDraft(
                    "a*sech(x - 1.6*t)^2",
                    "",
                    ""
                ),
                animate = true,
                parameter = 1.4f,
                mathTag = "非线性孤子",
                formulaDisplay = "u(x,t)=A·sech²(k(x - c·t))",
                insightZh = "Korteweg-de Vries (KdV) 方程的孤立波解。非线性陡化与色散展宽达到精妙绝伦的自稳平衡，波形千百里不变。",
                insightEn = "KdV equation soliton solution: non-linear steepening perfectly balances dispersion."
            )
        )

        GraphMode.PARAMETRIC -> listOf(
            GraphPreset(
                nameZh = "李萨如图形",
                nameEn = "Lissajous 3:4",
                expressions = GraphDraft("3*sin(3*u+t)", "3*cos(4*u)"),
                animate = true,
                parameter = 1f,
                mathTag = "示波器轨迹",
                formulaDisplay = "x=A·sin(3u+t), y=B·cos(4u)",
                insightZh = "垂直方向两个简谐振动的合成轨迹。闭合环路数与交点个数直接对应两个信号的频率比（3:4），常用于示波器测频。",
                insightEn = "Harmonic trajectory produced by perpendicular sinusoidal oscillations in 3:4 ratio."
            ),
            GraphPreset(
                nameZh = "蝴蝶曲线",
                nameEn = "Butterfly Curve",
                expressions = GraphDraft(
                    "sin(u)*(exp(cos(u)) - 2*cos(4*u) + sin(u/12)^5)",
                    "cos(u)*(exp(cos(u)) - 2*cos(4*u) + sin(u/12)^5)"
                ),
                parameter = 1f,
                mathTag = "极美超验线",
                formulaDisplay = "r=e^cos(u) - 2cos(4u) + sin⁵(u/12)",
                insightZh = "由 Temple H. Fay 于 1989 年发现的著名超越函数参数曲线，酷似一只振翅翩跹的蝴蝶，极具数学对称之美。",
                insightEn = "Transcendental parametric curve discovered by Fay (1989), evoking a flying butterfly."
            ),
            GraphPreset(
                nameZh = "心形轨迹",
                nameEn = "Heart Curve",
                expressions = GraphDraft(
                    "0.2*16*sin(u)^3",
                    "0.2*(13*cos(u) - 5*cos(2*u) - 2*cos(3*u) - cos(4*u))"
                ),
                parameter = 1f,
                mathTag = "代数几何",
                formulaDisplay = "x=16sin³(u), y=13cos(u)-5cos(2u)...",
                insightZh = "代数与三角参数化构建的标准爱心曲线，具有高阶平滑度与优雅的轮廓曲线。",
                insightEn = "Parametric cardioid variation forming an elegant, perfectly symmetric heart geometry."
            ),
            GraphPreset(
                nameZh = "摆线 (最速降线)",
                nameEn = "Cycloid",
                expressions = GraphDraft("1.2*(u - sin(u)) - 3.7", "1.2*(1 - cos(u)) - 2"),
                parameter = 1f,
                mathTag = "变分法",
                formulaDisplay = "x=r(u-sin u), y=r(1-cos u)",
                insightZh = "圆在直线上无滑动滚动时圆周上一点画出的轨迹。它既是重力场中最速降线（约翰·伯努利挑战题），也是等时降线。",
                insightEn = "The path traced by a point on a rolling circle; solves both brachistochrone and tautochrone problems."
            ),
            GraphPreset(
                nameZh = "伯努利双纽线",
                nameEn = "Lemniscate",
                expressions = GraphDraft(
                    "3*cos(u)/(1+sin(u)^2)",
                    "3*sin(u)*cos(u)/(1+sin(u)^2)"
                ),
                parameter = 1f,
                mathTag = "无穷大符号",
                formulaDisplay = "(x²+y²)² = 2a²(x²-y²)",
                insightZh = "形似阿拉伯数字 8 或数学无穷大符号 ∞ 的代数曲线，是椭圆函数论与天体物理引力双星系统的几何原型。",
                insightEn = "Figure-eight curve discovered by Bernoulli; historical foundation for elliptic integrals."
            )
        )

        GraphMode.POLAR -> listOf(
            GraphPreset(
                nameZh = "五叶玫瑰线",
                nameEn = "Rose Curve",
                expressions = GraphDraft("3.5*cos(5*u+t)"),
                animate = true,
                parameter = 1f,
                mathTag = "高阶对称",
                formulaDisplay = "r = A·cos(5θ + t)",
                insightZh = "当角系数为奇数 n 时形成正 n 叶玫瑰线，随时间 t 匀速自转，呈现生机勃勃的花瓣张弛旋转动效。",
                insightEn = "Polar rose with 5 symmetric petals rotating dynamically as phase t progresses."
            ),
            GraphPreset(
                nameZh = "阿基米德螺线",
                nameEn = "Archimedean",
                expressions = GraphDraft("0.22*a*u"),
                parameter = 1.2f,
                mathTag = "等距螺线",
                formulaDisplay = "r = a·θ",
                insightZh = "极径与极角成正比的经典螺线，各相邻螺圈之间的径向间距恒定不变，常用于螺旋盘香与机械水泵叶片设计。",
                insightEn = "Spiral with constant distance between consecutive turnings; foundational in mechanical screw design."
            ),
            GraphPreset(
                nameZh = "黄金对数螺线",
                nameEn = "Logarithmic Spiral",
                expressions = GraphDraft("0.35*exp(0.18*u)"),
                parameter = 1f,
                mathTag = "自然生长律",
                formulaDisplay = "r = a·e^(bθ)",
                insightZh = "等角螺线：切线与极半径夹角恒定。自然界中的鹦鹉螺壳、银河系旋臂与热带气旋均遵循此奇妙生长几何。",
                insightEn = "Equiangular spiral found in nautilus shells, spiral galaxies, and tropical cyclones."
            ),
            GraphPreset(
                nameZh = "极坐标心形线",
                nameEn = "Cardioid",
                expressions = GraphDraft("2*a*(1 - sin(u))"),
                parameter = 1.1f,
                mathTag = "麦克风指向",
                formulaDisplay = "r = 2a·(1 - sin θ)",
                insightZh = "定圆周上一点沿等半径圆滚动一周形成的轨迹。声学工程中心形指向性麦克风后方灵敏度为零，正是利用此方程。",
                insightEn = "Cardioid generated by epicycloid rolling; standard directional polar pattern in acoustic microphones."
            )
        )

        GraphMode.VECTOR_FIELD -> listOf(
            GraphPreset(
                nameZh = "中心旋涡场",
                nameEn = "Vortex Flow",
                expressions = GraphDraft("-y", "x"),
                mathTag = "流体旋转",
                formulaDisplay = "V(x,y) = (-y, x)",
                insightZh = "角速度恒定的不可压缩二维刚体旋转场，其散度为零（无源汇），旋度为常数，展现流体旋转的优雅动力学。",
                insightEn = "Incompressible circular vortex flow with zero divergence and constant curl."
            ),
            GraphPreset(
                nameZh = "双曲鞍点流",
                nameEn = "Saddle Flow",
                expressions = GraphDraft("x", "-y"),
                mathTag = "保守势场",
                formulaDisplay = "V(x,y) = (x, -y)",
                insightZh = "平面驻点鞍流场，流线呈现正交双曲线簇，在非线性动力系统与流体力学分岔分析中具有基础地位。",
                insightEn = "Hyperbolic stagnation flow with orthogonal streamlines around an unstable equilibrium point."
            ),
            GraphPreset(
                nameZh = "电偶极场",
                nameEn = "Dipole Field",
                expressions = GraphDraft("x/(x^2+y^2+0.6) - 0.4", "y/(x^2+y^2+0.6)"),
                mathTag = "经典电磁学",
                formulaDisplay = "E(x,y) ∝ r / (r² + ε)",
                insightZh = "模拟平滑点源与电荷产生的静电场与力线流向，展示从近场发散到远场趋近的平滑过渡。",
                insightEn = "Electrostatic field lines emerging from charge distribution with smoothed singularity."
            ),
            GraphPreset(
                nameZh = "波动驻波场",
                nameEn = "Wave Lattice",
                expressions = GraphDraft("sin(y+t)", "cos(x-t)"),
                animate = true,
                mathTag = "动态晶格",
                formulaDisplay = "V(x,y,t) = (sin(y+t), cos(x-t))",
                insightZh = "正交相干波场构成的周期性涡旋晶格流动，矢量方向随时间 t 循环翻滚演化，犹如微观流体对流胞。",
                insightEn = "Periodic vortex lattice evolving dynamically as orthogonal wave fronts intersect."
            )
        )

        GraphMode.SURFACE -> listOf(
            GraphPreset(
                nameZh = "水波涟漪",
                nameEn = "Water Ripple",
                expressions = GraphDraft("a*sin(2.6*sqrt(x^2+y^2)-t)/(1+0.2*(x^2+y^2))"),
                animate = true,
                parameter = 1.3f,
                mathTag = "二维波动",
                formulaDisplay = "z = A·sin(k·r - t) / (1 + α·r²)",
                insightZh = "模拟水滴滴落水面产生的二维同心环状扩展波，幅值随着扩散半径增大而能量衰减，波光粼粼。",
                insightEn = "Concentric surface waves radiating outward from a central disturbance with geometric dissipation."
            ),
            GraphPreset(
                nameZh = "墨西哥草帽面",
                nameEn = "Sombrero Sinc",
                expressions = GraphDraft("a*sinc(sqrt(x^2+y^2)+0.01)"),
                parameter = 1.6f,
                mathTag = "高维脉冲",
                formulaDisplay = "z = A·sinc(r) = A·sin(r)/r",
                insightZh = "二维贝塞尔圆对称函数在直角坐标系下的旋转表现，形态酷似墨西哥草帽，也是光学圆孔艾里斑衍射的光强分布。",
                insightEn = "Circularly symmetric sinc surface; represents Airy disk diffraction pattern in optics."
            ),
            GraphPreset(
                nameZh = "双曲抛物马鞍面",
                nameEn = "Hyperbolic Saddle",
                expressions = GraphDraft("(x^2 - y^2)/6"),
                parameter = 1f,
                mathTag = "微分几何",
                formulaDisplay = "z = (x² - y²) / 6",
                insightZh = "典型负高斯曲率曲面（双重直纹面）。沿 x 轴向上弯曲，沿 y 轴向下弯曲，原点为典型的多元函数鞍点。",
                insightEn = "Doubly-ruled surface with negative Gaussian curvature and an orthogonal saddle point."
            ),
            GraphPreset(
                nameZh = "猴鞍面",
                nameEn = "Monkey Saddle",
                expressions = GraphDraft("(x^3 - 3*x*y^2)/16"),
                parameter = 1f,
                mathTag = "三阶鞍点",
                formulaDisplay = "z = (x³ - 3xy²) / 16",
                insightZh = "拥有三个向下凹陷（两条腿和一条尾巴）与三个向上凸起的三阶鞍点曲面，在实分析中展示高阶退化驻点特性。",
                insightEn = "Higher-order surface with three downward slopes accommodating two legs and a tail for a monkey."
            ),
            GraphPreset(
                nameZh = "蛋托晶格面",
                nameEn = "Egg Carton",
                expressions = GraphDraft("a*sin(x)*cos(y)"),
                parameter = 1.2f,
                mathTag = "周期势阱",
                formulaDisplay = "z = A·sin(x)·cos(y)",
                insightZh = "空间周期性交替的高峰与深谷，在固体物理中常用于模拟晶格周期性电势分布与冷原子光晶格势阱。",
                insightEn = "Periodic potential landscape modelling electron movement in a crystalline lattice."
            ),
            GraphPreset(
                nameZh = "高斯双峰丘陵",
                nameEn = "Twin Peaks",
                expressions = GraphDraft("2*exp(-((x-1.5)^2+y^2)/2) + 2*exp(-((x+1.5)^2+y^2)/2)"),
                parameter = 1f,
                mathTag = "多模态分布",
                formulaDisplay = "z = 2e^(-(x-d)²/2) + 2e^(-(x+d)²/2)",
                insightZh = "双模态高斯混合分布表面，在统计学、量子双势阱隧道效应及机器学习能量模型中具有重要应用。",
                insightEn = "Bimodal Gaussian distribution modeling double-well potential and probability density."
            )
        )

        GraphMode.SPACE_CURVE -> listOf(
            GraphPreset(
                nameZh = "三维圆柱螺旋线",
                nameEn = "Helix Curve",
                expressions = GraphDraft("2.5*cos(u+t)", "2.5*sin(u+t)", "a*u/3 - 4"),
                animate = true,
                parameter = 1f,
                mathTag = "DNA 双螺旋",
                formulaDisplay = "x=R·cos(u+t), y=R·sin(u+t), z=h·u",
                insightZh = "空间中最均匀的曲率与扭率恒定曲线，带电粒子在匀强磁场中的洛伦兹力运动轨迹，以及生物 DNA 分子结构原型。",
                insightEn = "Constant curvature and torsion space curve; trajectory of charged particle in magnetic field."
            ),
            GraphPreset(
                nameZh = "环面纽结",
                nameEn = "Torus Knot",
                expressions = GraphDraft(
                    "(2.5 + cos(3*u))*cos(2*u+t)",
                    "(2.5 + cos(3*u))*sin(2*u+t)",
                    "sin(3*u)"
                ),
                animate = true,
                parameter = 1f,
                mathTag = "拓扑纽结",
                formulaDisplay = "(p,q)=(2,3) 环面三叶纽结",
                insightZh = "缠绕在环面表面自闭合的三维 (2,3) 三叶纽结，在现代拓扑学、纽结理论与弦理论超对称紧化中是核心研究对象。",
                insightEn = "A (2,3) trefoil knot wrapped continuously around the surface of a torus."
            ),
            GraphPreset(
                nameZh = "三维圆锥螺旋",
                nameEn = "Conical Spiral",
                expressions = GraphDraft(
                    "0.28*u*cos(u+t)",
                    "0.28*u*sin(u+t)",
                    "0.3*u - 3.5"
                ),
                animate = true,
                parameter = 1f,
                mathTag = "旋转螺旋",
                formulaDisplay = "r(z) ∝ z, θ ∝ z",
                insightZh = "半径随高度线性展开的三维锥面螺旋线，常用于高频对数周期天线与龙卷风旋转动力学建模。",
                insightEn = "Spiral wound around a conical envelope, widely used in wideband directional antenna design."
            )
        )
    }

    fun first(mode: GraphMode): GraphPreset = forMode(mode).first()
}
