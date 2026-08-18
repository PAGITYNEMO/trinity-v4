/*
 * rdr2-nemo — TRINITY v4.0 数论引擎 × RDR2
 *
 * 从数学和物理底层写：本 mod 的全部内容来自同一个数论场——
 * 拉马努金和 c_q(n)（μ/φ 恒等式查表）、干涉图纹模板、φ 区域权重，
 * 与 TRINITY v4.0 引擎（engine/src/trinity/poles/Ramanujan.java）同源移植。
 *
 * 输出：
 *  - 光幕（物理粒子）：玩家周围的干涉图纹可视化，粒子位置来自数论场，
 *    运动由呼吸/心跳时钟调制（弹簧回归 + 漂移）
 *  - 对偶读数 HUD：R(t) 图纹清晰度 / H 熵 / S 结构 / κ 耦合（实时场度量）
 *  - 场驱动涌现：天气、动物群由场值选择（场高的区域世界更"活"）
 *  - 环境：色温随呼吸/心跳脉动；NEMO 低语
 *
 * 纯本地、确定性、零外部依赖。
 */

#include "nemo.h"
#include "..\inc\natives.h"
#include "..\inc\types.h"
#include "..\inc\enums.h"
#include "..\inc\main.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <cstdarg>
#include <vector>

/* ---------------- 本地日志（游戏目录 nemo.log，实证诊断） ---------------- */

static FILE* gLog = nullptr;

static void nlog(const char* fmt, ...) {
    if (!gLog) {
        gLog = fopen("nemo.log", "w");
        if (!gLog) return;
    }
    va_list ap;
    va_start(ap, fmt);
    vfprintf(gLog, fmt, ap);
    va_end(ap);
    fflush(gLog);
}

/* 字符串 hash（RDR2 joaat） */
static unsigned int joaat(const char* s) {
    unsigned int h = 0;
    while (*s) { h += (unsigned char)*s++; h += (h << 10); h ^= (h >> 6); }
    h += (h << 3); h ^= (h >> 11); h += (h << 15);
    return h;
}

/* ---------------- 编码工具 ---------------- */

std::wstring utf8_to_wide(const char* s) {
    if (!s || !*s) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, nullptr, 0);
    std::wstring w(n > 0 ? n - 1 : 0, L'\0');
    if (n > 1) MultiByteToWideChar(CP_UTF8, 0, s, -1, &w[0], n);
    return w;
}

std::string wide_to_utf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s(n, '\0');
    if (n > 0) WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

/* ================= 数论场（TRINITY 数学核心，同源移植） ================= */

static const int QMAX = 63;

class TrinityField {
public:
    TrinityField() {
        /* 线性筛：φ（欧拉函数）与 μ（莫比乌斯函数） */
        int lp[QMAX + 1] = {0};
        for (int i = 0; i <= QMAX; i++) { phi[i] = 0; mu[i] = 0; }
        mu[1] = 1;
        phi[1] = 1;
        int np = 0;
        int pr[QMAX + 1];
        for (int i = 2; i <= QMAX; i++) {
            if (lp[i] == 0) {
                lp[i] = i;
                pr[np++] = i;
                phi[i] = i - 1;
                mu[i] = -1;
            }
            for (int j = 0; j < np; j++) {
                long v = (long)pr[j] * i;
                if (v > QMAX) break;
                lp[(int)v] = pr[j];
                if (pr[j] == lp[i]) {
                    phi[(int)v] = phi[i] * pr[j];
                    mu[(int)v] = 0;
                    break;
                } else {
                    phi[(int)v] = phi[i] * (pr[j] - 1);
                    mu[(int)v] = -mu[i];
                }
            }
        }
        /* c_q(n) 查表：c_q(n) = μ(q/g)·φ(q)/φ(q/g)，g = gcd(q,n) */
        for (int q = 1; q <= QMAX; q++) {
            for (int n = 0; n < q; n++) {
                int g = gcd(q, n);
                long v = (long)mu[q / g] * phi[q] / phi[q / g];
                cq[q][n] = (double)v;
            }
        }
    }

    static int gcd(int a, int b) {
        if (a < 0) a = -a;
        if (b < 0) b = -b;
        while (b != 0) { int t = a % b; a = b; b = t; }
        return a;
    }

    static double hash01(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >> 33;
        return (double)((h >> 11) & 0xFFFFFFFFFFFFFL) / 9007199254740992.0;
    }

    /** c_q(n)，整数，查表。 */
    double c(int q, int n) const {
        int m = n % q;
        if (m < 0) m += q;
        return cq[q][m];
    }

    double phiOf(int q) const { return (double)phi[q]; }

    /** φ/q 区域权重（0,1]——数论函数权重的"富饶度"。 */
    double regionWeight(long x, long z, long salt) const {
        int q = 2 + (int)(hash01(x, z, salt) * (QMAX - 1));
        if (q > QMAX) q = QMAX;
        return phi[q] / (double)q;
    }

    /** 干涉图纹 |c_q1(x) + c_q2(z)|² 归一化到 [0,1]。 */
    double ramTemplate(int q1, int q2, int x, int z) const {
        double v = c(q1, x) + c(q2, z);
        double norm = phi[q1] + phi[q2];
        double t = (v * v) / (norm * norm);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /** 总场值：干涉图纹 × 区域富饶度（图纹在富饶区更亮）。 */
    double fieldAt(long x, long z, long salt, int q1, int q2) const {
        double t = ramTemplate(q1, q2, (int)x, (int)z);
        double w = regionWeight(x, z, salt);
        return 0.35 * t + 0.65 * w;
    }

    /** 图纹清晰度 R(t)：残差分布集中度 × 场活跃度（同引擎 clarity）。 */
    double clarity(const double* f, int w, int h) const {
        double mean = 0;
        int cells = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                mean += f[y * w + x];
                cells++;
            }
        }
        mean /= (cells > 0 ? cells : 1);
        double var = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                double d = f[y * w + x] - mean;
                var += d * d;
            }
        }
        double std = sqrt(var / (cells > 0 ? cells : 1));
        double stdNorm = std / 0.25;
        if (stdNorm > 1) stdNorm = 1;
        double best = 0;
        for (int q = 3; q <= 16; q++) {
            int hist[16] = {0};
            int cnt = 0;
            for (int y = 0; y < h; y += 2) {
                for (int x = 0; x < w; x += 2) {
                    double v = f[y * w + x];
                    int n = (int)round((v * 0.5 + 0.5) * (q * 8)) % q;
                    if (n < 0) n += q;
                    hist[n]++;
                    cnt++;
                }
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double)(cnt > 0 ? cnt : 1);
                if (p > 0) hE -= p * log(p);
            }
            double rq = (1 - hE / log((double)q)) * stdNorm;
            if (rq > best) best = rq;
        }
        return best;
    }

private:
    int phi[QMAX + 1];
    int mu[QMAX + 1];
    double cq[QMAX + 1][QMAX + 1];
};

static TrinityField gField;

/* ---------------- 全局状态 ---------------- */

static bool nemoEnvOn = true;       /* F8 开关 */
static bool nemoHudOn = true;       /* F9：对偶读数 HUD */
static int  nemoTick = 0;           /* 帧计数（≈ 游戏帧率） */
static int  q1 = 23, q2 = 43;       /* 模板 q 对（引擎同源种子） */
static long worldSalt = 1976124607L; /* 世界种子（TRINITY 世界的种子） */
static Vector3 gLastPos = {0, 0, 0}; /* 最近玩家位置（HUD 节点读数用） */

/* RDR2 文本渲染：官方 NativeTrainer 方法（UI::DRAW_TEXT + CREATE_STRING），见文本渲染节 */
static void drawText(float x, float y, const char* str, float scale, int r, int g, int b, int a);

/* 季节基色（定义在光子部分；此处前向声明供节点系统使用） */
static void seasonColor(float& r, float& g, float& b);

/* ================= 图纹节点（矿脉链的 RDR2 版） =================
 * 数论场的局部极值 = 干涉图纹的"矿脉节点"。
 * 确定性扫描玩家周围场，极值处立起持续光柱；靠近时 HUD 给出读数。
 */

struct FieldNode {
    float x, y, z;
    double val;
    int life;
};

static FieldNode gNodes[12];
static int gNodeCount = 0;

static void nodesScan(Vector3 pos) {
    int bx = (int)pos.x, bz = (int)pos.z;
    gNodeCount = 0;
    for (int gx = -32; gx <= 32 && gNodeCount < 12; gx += 4) {
        for (int gz = -32; gz <= 32 && gNodeCount < 12; gz += 4) {
            int x = bx + gx, z = bz + gz;
            double c = gField.fieldAt(x, z, worldSalt, q1, q2);
            if (c < 0.52) continue;
            double up = gField.fieldAt(x, z + 4, worldSalt, q1, q2);
            double dn = gField.fieldAt(x, z - 4, worldSalt, q1, q2);
            double lt = gField.fieldAt(x - 4, z, worldSalt, q1, q2);
            double rt = gField.fieldAt(x + 4, z, worldSalt, q1, q2);
            if (c >= up && c >= dn && c >= lt && c >= rt) {
                float gy = pos.y + 1.5f;
                float groundZ = 0;
                if (MISC::GET_GROUND_Z_FOR_3D_COORD((float)x, pos.y + 60, (float)z, &groundZ, true)) {
                    gy = groundZ + 1.5f;
                }
                FieldNode nd;
                nd.x = (float)x;
                nd.y = gy;
                nd.z = (float)z;
                nd.val = c;
                nd.life = nemoTick + 3600;
                gNodes[gNodeCount++] = nd;
            }
        }
    }
    if (gNodeCount > 0) {
        nlog("[nodes] scanned %d pattern nodes\n", gNodeCount);
    }
}

static void nodesRender() {
    float sr, sg, sb;
    seasonColor(sr, sg, sb);
    for (int i = 0; i < gNodeCount; i++) {
        const FieldNode& nd = gNodes[i];
        if (nemoTick > nd.life) continue;
        /* 节点光柱：季节色 + 场值亮度 */
        float intensity = 0.35f + 0.45f * (float)nd.val;
        GRAPHICS::DRAW_LIGHT_WITH_RANGE(nd.x, nd.y, nd.z,
                                        (int)(sr * 255), (int)(sg * 255), (int)(sb * 255),
                                        6.0f, intensity);
        GRAPHICS::DRAW_LIGHT_WITH_RANGE(nd.x, nd.y + 6.0f, nd.z,
                                        (int)(sr * 255), (int)(sg * 255), (int)(sb * 255),
                                        6.0f, intensity * 0.6f);
    }
}

/* 最近节点（HUD 读数用），返回距离或 -1 */
static float nearestNode(Vector3 pos, double& val) {
    float best = 1e9f;
    for (int i = 0; i < gNodeCount; i++) {
        const FieldNode& nd = gNodes[i];
        if (nemoTick > nd.life) continue;
        float dx = nd.x - pos.x, dy = nd.y - pos.y, dz = nd.z - pos.z;
        float d = (float)sqrt(dx * dx + dy * dy + dz * dz);
        if (d < best) {
            best = d;
            val = nd.val;
        }
    }
    return best;
}

/* ---------------- 键盘钩子 ---------------- */

/* SDK 签名：key, repeats, scanCode, isExtended, isWithAlt, wasDownBefore, isUpNow */
void onKey(DWORD key, WORD, BYTE, BOOL, BOOL, BOOL, BOOL isUpNow) {
    if (isUpNow) return; /* 只在按下瞬间触发一次（弹起事件忽略） */
    if (key == NEMO_KEY_TOGGLE) {
        nemoEnvOn = !nemoEnvOn;
        gQ->say(nemoEnvOn ? L"⍟ NEMO 环境已升起" : L"— PAGITY 已过滤环境噪声");
    } else if (key == NEMO_KEY_STATUS) {
        nemoHudOn = !nemoHudOn;
        gQ->say(nemoHudOn ? L"⍟ 对偶读数已开启" : L"— 对偶读数已关闭");
    }
}

/* ================= 光幕：干涉图纹的物理粒子（分层） ================= */

struct Photon {
    float x, y, z;        /* 当前位置 */
    float ax, ay, az;     /* 数学锚点（数论场决定） */
    float vx, vy, vz;     /* 速度（物理） */
    float hue;            /* 色相（季节流动） */
    float bright;         /* 场值亮度 0..1 */
};

/* 三层光幕：近层（环绕玩家）· 远层（原野）· 天顶（穹顶） */
#define PHOTON_NEAR 48
#define PHOTON_FAR  64
#define PHOTON_SKY  24
#define PHOTON_TOTAL (PHOTON_NEAR + PHOTON_FAR + PHOTON_SKY)

static Photon gPhotons[PHOTON_TOTAL];
static int gPhotonCount = PHOTON_TOTAL;

/* 物理涟漪：玩家的扰动在光幕中的波传播 */
static struct {
    float x, y, z;
    float speed, radius, amp;
    int born;
    bool active;
} gRipple = {0, 0, 0, 8.0f, 30.0f, 1.0f, 0, false};
static int gLastRippleTick = -9999;

/* 呼吸/心跳时钟（纯时间函数，同引擎 TideClock） */
static double nemoBreath()  { return sin(2 * 3.14159265 * 0.15 * nemoTick / 60.0); }
static double nemoHeart()   { double s = sin(2 * 3.14159265 * 1.2 * nemoTick / 60.0); return s > 0 ? pow(s, 8) : 0; }
static double seasonPhase() { return 2 * 3.14159265 * (nemoTick / 60.0 / 480000.0 / 8.0); }
static int    seasonIndex() { int s = (int)floor(seasonPhase() / (3.14159265 / 2)) % 4; return s < 0 ? s + 4 : s; }

/* HSV -> RGB */
static void hsv2rgb(float h, float s, float v, float& r, float& g, float& b) {
    int i = (int)(h * 6);
    float f = h * 6 - i;
    float p = v * (1 - s);
    float q = v * (1 - f * s);
    float t = v * (1 - (1 - f) * s);
    switch (i % 6) {
        case 0: r = v; g = t; b = p; break;
        case 1: r = q; g = v; b = p; break;
        case 2: r = p; g = v; b = t; break;
        case 3: r = p; g = q; b = v; break;
        case 4: r = t; g = p; b = v; break;
        default: r = v; g = p; b = q; break;
    }
}

/* 季节基色（光幕色随季节流动，与 MC 版季节色同步） */
static void seasonColor(float& r, float& g, float& b) {
    switch (seasonIndex()) {
        case 0: r = 0.35f; g = 0.95f; b = 0.55f; break;  /* 新芽 */
        case 1: r = 0.25f; g = 0.65f; b = 1.0f; break;   /* 涨潮 */
        case 2: r = 1.0f;  g = 0.55f; b = 0.2f; break;   /* 落叶 */
        default: r = 0.75f; g = 0.85f; b = 1.0f; break;  /* 冰封 */
    }
}

/* 光子围绕玩家重采样（数论场决定锚点；近/远/天顶三层） */
static void photonsReseed(Vector3 pos) {
    int bx = (int)pos.x, bz = (int)pos.z;
    for (int i = 0; i < gPhotonCount; i++) {
        float rBase, hBase, ring;
        if (i < PHOTON_NEAR) {
            ring = i / (double)PHOTON_NEAR * 6.2831853;
            rBase = 4.0f;
            hBase = 1.5f;
        } else if (i < PHOTON_NEAR + PHOTON_FAR) {
            ring = (i - PHOTON_NEAR) / (double)PHOTON_FAR * 6.2831853 + 1.3;
            rBase = 12.0f;
            hBase = 2.5f;
        } else {
            ring = (i - PHOTON_NEAR - PHOTON_FAR) / (double)PHOTON_SKY * 6.2831853 + 0.6;
            rBase = 30.0f;
            hBase = 22.0f;
        }
        double a = ring;
        /* 锚点 x/z：半径与高度由数论场调制 */
        double f = gField.fieldAt(bx + (long)(cos(a) * (rBase + 14)), bz + (long)(sin(a) * (rBase + 14)),
                                  worldSalt, q1, q2);
        float r = rBase + (float)f * (rBase * 0.9f);
        float ax = pos.x + (float)cos(a) * r;
        float az = pos.z + (float)sin(a) * r;
        float ay = pos.y + hBase + (float)f * 5.0f;
        gPhotons[i].ax = ax;
        gPhotons[i].ay = ay;
        gPhotons[i].az = az;
        /* 缓动：粒子飘向新锚点 */
        gPhotons[i].vx += (ax - gPhotons[i].x) * 0.02f;
        gPhotons[i].vy += (ay - gPhotons[i].y) * 0.02f;
        gPhotons[i].vz += (az - gPhotons[i].z) * 0.02f;
        gPhotons[i].hue = (float)(i / (double)gPhotonCount + 0.5 + 0.03 * nemoTick / 60.0);
        gPhotons[i].bright = (float)f;
    }
}

/* 光子物理更新（呼吸/心跳调制 + 弹簧回归 + 微风 + 玩家涟漪） */
static void photonsUpdate(Vector3 pos, float dt) {
    double breath = nemoBreath();
    double heart = nemoHeart();
    float t = (float)nemoTick / 60.0f;
    bool rippleActive = gRipple.active;
    float rpx = gRipple.x, rpz = gRipple.z;
    float wave = rippleActive ? gRipple.speed * (nemoTick - gRipple.born) / 60.0f : 0;
    for (int i = 0; i < gPhotonCount; i++) {
        Photon& p = gPhotons[i];
        /* 呼吸：整体垂直脉动；心跳：脉冲推离锚点 */
        float bm = (float)(0.25 * breath + 0.6 * heart);
        float targetY = p.ay + bm * 1.2f;
        /* 弹簧回归锚点 */
        p.vx += (p.ax - p.x) * 0.004f;
        p.vy += (targetY - p.y) * 0.004f;
        p.vz += (p.az - p.z) * 0.004f;
        /* 微风：缓慢环绕漂移 */
        float wa = t * 0.05f + i * 0.7f;
        p.vx += (float)sin(wa) * 0.0008f;
        p.vz += (float)cos(wa * 1.3f) * 0.0008f;
        /* 玩家涟漪：波前扫过时光子被推离（物理波传播） */
        if (rippleActive && wave < gRipple.radius) {
            float dx = p.x - rpx;
            float dz = p.z - rpz;
            float d = (float)sqrt(dx * dx + dz * dz);
            float phase = d - wave;
            if (phase > -2.5f && phase < 2.5f) {
                float falloff = 1.0f - d / gRipple.radius;
                if (falloff < 0) falloff = 0;
                float kick = gRipple.amp * (float)cos(phase * 3.14159265f / 2.5f) * falloff;
                float inv = 1.0f / (d + 0.01f);
                p.vx += dx * inv * kick * 0.09f;
                p.vz += dz * inv * kick * 0.09f;
            }
        }
        /* 阻尼 */
        p.vx *= 0.94f;
        p.vy *= 0.94f;
        p.vz *= 0.94f;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.z += p.vz * dt;
        /* 跟随玩家平移（光幕随行） */
        p.x += (pos.x - p.x) * 0.002f;
        p.z += (pos.z - p.z) * 0.002f;
    }
}

/* 事件光幕脉冲：字显示不了时，光幕用颜色说话（TRINITY：语言是光的图纹） */
static struct {
    float r, g, b;
    float amp;
    int born;
    int dur;
    bool active;
} gPulse = {1, 1, 1, 0, 0, 0, false};

static void pulseTrigger(float r, float g, float b, float amp, int durFrames) {
    gPulse.r = r;
    gPulse.g = g;
    gPulse.b = b;
    gPulse.amp = amp;
    gPulse.born = nemoTick;
    gPulse.dur = durFrames;
    gPulse.active = true;
}

static void pulseTick() {
    if (gPulse.active && nemoTick - gPulse.born > gPulse.dur) {
        gPulse.active = false;
    }
}

/* 脉冲强度 0..1 */
static float pulseStrength() {
    if (!gPulse.active) return 0;
    int age = nemoTick - gPulse.born;
    float t = age / (float)gPulse.dur;
    return (1.0f - t) * gPulse.amp;
}

/* 光幕渲染：三层节拍（近每帧 / 远每 2 帧 / 天顶每 3 帧），共 ~88 光点/帧 */
static void photonsRender() {
    float sr, sg, sb;
    seasonColor(sr, sg, sb);
    bool drawFar = (nemoTick % 2 == 0);
    bool drawSky = (nemoTick % 3 == 0);
    float ps = pulseStrength();
    for (int i = 0; i < gPhotonCount; i++) {
        if (i >= PHOTON_NEAR) {
            if (i < PHOTON_NEAR + PHOTON_FAR) {
                if (!drawFar) continue;
            } else if (!drawSky) {
                continue;
            }
        }
        const Photon& p = gPhotons[i];
        float r, g, b;
        hsv2rgb(p.hue, 0.75f, 0.55f + 0.45f * p.bright, r, g, b);
        /* 与季节基色混合：光幕季节化 */
        r = r * 0.4f + sr * 0.6f;
        g = g * 0.4f + sg * 0.6f;
        b = b * 0.4f + sb * 0.6f;
        /* 事件脉冲：光幕被事件染色 */
        if (ps > 0) {
            r = r * (1 - ps) + gPulse.r * ps;
            g = g * (1 - ps) + gPulse.g * ps;
            b = b * (1 - ps) + gPulse.b * ps;
        }
        float intensity = (0.30f + 0.55f * p.bright) * (1.0f + 1.2f * ps);
        GRAPHICS::DRAW_LIGHT_WITH_RANGE(p.x, p.y, p.z,
                                        (int)(r * 255), (int)(g * 255), (int)(b * 255),
                                        4.0f + 4.0f * p.bright, intensity);
    }
}

/* 玩家=噪声源：高速行动（奔马/疾跑）扰动光幕，生成物理涟漪 */
static void rippleCheck(Vector3 pos) {
    Ped pl = PLAYER::GET_PLAYER_PED(0);
    if (!ENTITY::DOES_ENTITY_EXIST(pl)) return;
    float spd = ENTITY::GET_ENTITY_SPEED(pl);
    if (spd > 7.0f && !gRipple.active && nemoTick - gLastRippleTick > 240) {
        gRipple.x = pos.x;
        gRipple.y = pos.y + 1.5f;
        gRipple.z = pos.z;
        gRipple.speed = 8.0f;
        gRipple.radius = 30.0f;
        gRipple.amp = 1.0f;
        gRipple.born = nemoTick;
        gRipple.active = true;
        gLastRippleTick = nemoTick;
        nlog("[ripple] player speed %.1f -> field ripple\n", spd);
    }
    if (gRipple.active && (float)(nemoTick - gRipple.born) / 60.0f * gRipple.speed > gRipple.radius) {
        gRipple.active = false;
    }
}

/* ================= 对偶读数 HUD（实时场度量） ================= */

static double gR = 0, gH = 0, gS = 0, gKappa = 0;

static void measureField(Vector3 pos) {
    int bx = (int)pos.x, bz = (int)pos.z;
    const int W = 13, H = 13;
    double f[W * H];
    for (int dy = 0; dy < H; dy++) {
        for (int dx = 0; dx < W; dx++) {
            f[dy * W + dx] = gField.fieldAt(bx + dx - 6, bz + dy - 6, worldSalt, q1, q2);
        }
    }
    gR = gField.clarity(f, W, H);
    /* H：场值分布的熵（8 bin） */
    int hist[8] = {0};
    for (int i = 0; i < W * H; i++) {
        int n = (int)(f[i] * 7.999);
        if (n > 7) n = 7;
        hist[n]++;
    }
    double hE = 0;
    for (int i = 0; i < 8; i++) {
        double p = hist[i] / (double)(W * H);
        if (p > 0) hE -= p * log(p);
    }
    gH = hE / log(8.0);
    /* S：拉普拉斯能量（结构） */
    double lap = 0, e = 1e-9;
    for (int y = 1; y < H - 1; y++) {
        for (int x = 1; x < W - 1; x++) {
            double l = 4 * f[y * W + x] - f[(y - 1) * W + x] - f[(y + 1) * W + x]
                       - f[y * W + x - 1] - f[y * W + x + 1];
            lap += l * l;
            e += f[y * W + x] * f[y * W + x];
        }
    }
    gS = lap / e * 0.1;
    if (gS > 1) gS = 1;
    /* κ：与季节振幅耦合 */
    gKappa = 0.5 + 0.5 * (0.7 + 0.3 * sin(seasonPhase()));
}

static void renderHud() {
    if (!nemoHudOn) return;
    char buf[260];
    /* 涌现状态：R 高且 H 低 = 结构从噪声中涌现（引擎判定） */
    const char* emerge = "噪声主导";
    if (gR > 0.45 && gH < 0.65) emerge = "涌现中";
    else if (gS > 0.5) emerge = "结构高";
    double nv = 0;
    float nd = nearestNode(gLastPos, nv);
    char nodebuf[64] = "";
    if (nd < 9.0f && nd >= 0) {
        snprintf(nodebuf, sizeof(nodebuf), "  图纹节点 %.1f格 R=%.2f", nd, nv);
    }
    snprintf(buf, sizeof(buf),
             "NEMO 对偶场  R=%.2f H=%.2f S=%.2f k=%.2f  光子=%d  %s%s",
             gR, gH, gS, gKappa, gPhotonCount, emerge, nodebuf);
    drawText(0.02f, 0.88f, buf, 0.40f, 140, 230, 255, 210);
}

/* ================= 场驱动涌现（天气 / 动物） ================= */

static const char* MODELS_DEER[]  = {"a_c_deer_01", "a_c_elk", "a_c_pronghorn", "a_c_moose"};
static const char* MODELS_WOLF[]  = {"a_c_wolf", "a_c_coyote"};
static const char* MODELS_BIRD[]  = {"a_c_eagle", "a_c_raven_01", "a_c_vulture"};
static const char* MODELS_HORSE[] = {"a_c_horse_americanpaint", "a_c_horse_appaloosa", "a_c_horse_arabian"};

static int nextWorldGenTick = 1800;   /* 首次事件：进故事模式 30 秒后（必定天气） */
static bool firstWorldGenDone = false;

static void addBeam(float x, float y, float z, int r, int g, int b, float range, float intensity, int frames) {
    (void)x; (void)y; (void)z; (void)r; (void)g; (void)b; (void)range; (void)intensity; (void)frames;
}

static int genHerdOne(const char* model, int count, Vector3 pos, float radius, float yBoost) {
    Hash h = joaat(model);
    STREAMING::REQUEST_MODEL(h, true);
    int tries = 0;
    while (!STREAMING::HAS_MODEL_LOADED(h) && tries < 200) {
        WAIT(0);
        tries++;
    }
    if (!STREAMING::HAS_MODEL_LOADED(h)) {
        STREAMING::SET_MODEL_AS_NO_LONGER_NEEDED(h);
        nlog("[worldgen] model FAILED to load: %s\n", model);
        return 0;
    }
    nlog("[worldgen] model loaded: %s\n", model);
    int spawned = 0;
    for (int i = 0; i < count; i++) {
        double a = i / (double)count * 6.2831853 + 0.7;
        double f = gField.fieldAt((long)pos.x + (long)(cos(a) * radius), (long)pos.z + (long)(sin(a) * radius),
                                  worldSalt, q1, q2);
        float d = radius * (0.55f + 0.7f * (float)f);
        float x = pos.x + (float)cos(a) * d;
        float z = pos.z + (float)sin(a) * d;
        Ped p = PED::CREATE_PED(h, x, pos.y + yBoost, z, (float)(a * 57.29578), false, false, false, false);
        if (ENTITY::DOES_ENTITY_EXIST(p)) {
            ENTITY::SET_ENTITY_AS_MISSION_ENTITY(p, true, true);
            /* 地形耦合迁徙：沿数论场梯度方向走（同引擎 bot 的 driftTowardPattern） */
            double fx = gField.fieldAt((long)x + 6, (long)z, worldSalt, q1, q2)
                      - gField.fieldAt((long)x - 6, (long)z, worldSalt, q1, q2);
            double fz = gField.fieldAt((long)x, (long)z + 6, worldSalt, q1, q2)
                      - gField.fieldAt((long)x, (long)z - 6, worldSalt, q1, q2);
            if (fx * fx + fz * fz > 1e-6) {
                float len = (float)sqrt(fx * fx + fz * fz);
                TASK::TASK_GO_TO_COORD_ANY_MEANS(p,
                        x + (float)(fx / len) * 18.0f, pos.y + yBoost, z + (float)(fz / len) * 18.0f,
                        2.2f, 0, false, 0, 0.0f);
            } else {
                TASK::TASK_WANDER_STANDARD(p, 30.0f, 10);
            }
            spawned++;
        }
    }
    STREAMING::SET_MODEL_AS_NO_LONGER_NEEDED(h);
    nlog("[worldgen] spawned %d x %s\n", spawned, model);
    return spawned;
}

static void worldGen() {
    if (nemoTick < nextWorldGenTick) return;
    Ped pl = PLAYER::GET_PLAYER_PED(0);
    if (!ENTITY::DOES_ENTITY_EXIST(pl) || ENTITY::IS_ENTITY_DEAD(pl)) return;
    Vector3 pos = ENTITY::GET_ENTITY_COORDS(pl, true, true);
    int cx = (int)pos.x >> 4;
    int cz = (int)pos.z >> 4;
    int minute = CLOCK::GET_CLOCK_HOURS() * 60 + CLOCK::GET_CLOCK_MINUTES();
    /* 确定性间隔：3~6 分钟（chunk-seed 风格） */
    unsigned s = (unsigned)(cx * 73856093) ^ (unsigned)(cz * 19349663) ^ (unsigned)(minute * 83492791);
    if (!s) s = 1;
    nextWorldGenTick = nemoTick + 60 * 60 * (3 + (int)((s >> 8) % 3));

    /* 场值决定事件性质：场高 → 天气狂暴；场低 → 动物/低语 */
    double field = gField.fieldAt((long)pos.x, (long)pos.z, worldSalt, q1, q2);

    if (!firstWorldGenDone) {
        firstWorldGenDone = true;
        static const char* WEATHERS[] = {"STORM", "RAIN", "FOGGY", "OVERCAST", "SNOW"};
        const char* w = WEATHERS[seasonIndex() % 5]; /* 首事件：季节决定天气 */
        MISC::_SET_WEATHER_TYPE(joaat(w), true, true, true, 12.0f, true);
        nlog("[worldgen] first event: weather %s (season %d, field %.2f)\n", w, seasonIndex(), field);
        gQ->say(L"天空开始变脸", 6000);
        pulseTrigger(0.9f, 0.9f, 1.0f, 1.0f, 90); /* 白光脉冲：天空变脸 */
        return;
    }

    unsigned rnd = (unsigned)(s * 1664525u + 1013904223u);
    double r = (rnd >> 8) / 16777216.0;

    if (field > 0.62 && r < 0.5) {
        /* 场高：天气狂暴 */
        static const char* WEATHERS[] = {"STORM", "RAIN", "THUNDER", "SNOW"};
        const char* w = WEATHERS[(int)(r * 40) % 4];
        MISC::_SET_WEATHER_TYPE(joaat(w), true, true, true, 12.0f, true);
        nlog("[worldgen] weather event (field %.2f): %s\n", field, w);
        gQ->say(L"对偶场的张力在这里爆发", 6000);
        pulseTrigger(0.9f, 0.9f, 1.0f, 1.0f, 90);
    } else if (r < 0.22) {
        const char* m = MODELS_DEER[(int)(r * 100) % 4];
        int n = genHerdOne(m, 3 + (int)(r * 30) % 3, pos, 50, 0);
        if (n > 0) {
            gQ->say(L"一群鹿从山脊那边过来了", 5000);
            pulseTrigger(0.3f, 0.9f, 0.4f, 0.8f, 80); /* 绿脉冲：鹿群 */
        }
    } else if (r < 0.38) {
        const char* m = MODELS_WOLF[(int)(r * 100) % 2];
        int n = genHerdOne(m, 3, pos, 45, 0);
        if (n > 0) {
            gQ->say(L"……有狼群在附近低嚎", 5000);
            pulseTrigger(1.0f, 0.3f, 0.3f, 0.9f, 80); /* 红脉冲：狼群 */
        }
    } else if (r < 0.52) {
        const char* m = MODELS_BIRD[(int)(r * 100) % 3];
        int n = genHerdOne(m, 2 + (int)(r * 30) % 3, pos, 30, 38);
        if (n > 0) {
            gQ->say(L"秃鹫在头顶盘旋——它们闻到了什么", 5000);
            pulseTrigger(0.4f, 0.6f, 1.0f, 0.8f, 80); /* 蓝脉冲：秃鹫 */
        }
    } else if (r < 0.86) {
        const char* m = MODELS_HORSE[(int)(r * 100) % 3];
        int n = genHerdOne(m, 2 + (int)(r * 20) % 2, pos, 55, 0);
        if (n > 0) {
            gQ->say(L"一群野马从远处跑过", 5000);
            pulseTrigger(1.0f, 0.7f, 0.3f, 0.8f, 80); /* 橙脉冲：野马 */
        }
    } else {
        gQ->say(L"风把旧世界的气味吹过来了", 5000);
        nlog("[worldgen] whisper event (field %.2f)\n", field);
        pulseTrigger(0.7f, 0.5f, 1.0f, 0.8f, 80); /* 紫脉冲：低语 */
    }
}

/* ================= 环境：色温随呼吸/心跳脉动 ================= */

static void envPulse() {
    if (!nemoEnvOn) return;
    int hour = CLOCK::GET_CLOCK_HOURS();
    /* 基础色温（时间） × 场调制（R(t) 高的区域色温更明显——光幕随数学呼吸） */
    float fieldMod = 0.5f + 0.5f * (float)gR;
    if (hour >= 17 && hour <= 20) {
        GRAPHICS::SET_TIMECYCLE_MODIFIER_STRENGTH((float)(0.18 + 0.10 * nemoBreath()) * fieldMod);
    } else if (hour >= 22 || hour <= 4) {
        GRAPHICS::SET_TIMECYCLE_MODIFIER_STRENGTH((float)(0.12 + 0.08 * nemoHeart()) * fieldMod);
    } else {
        GRAPHICS::SET_TIMECYCLE_MODIFIER_STRENGTH(0.0f);
    }
}

/* ---------------- 文本渲染（官方 NativeTrainer 方法：DRAW_TEXT + CREATE_STRING） ----------------
 * 关键：DRAW_TEXT(0xD79334A4BB99BAD1) 第一个参数必须是
 * GAMEPLAY::CREATE_STRING(10, "LITERAL_STRING", str) 包装的文本组件——
 * 直接传裸字符串不会显示（之前的坑）。镜像头缺失 CREATE_STRING，用 invoke 直调。
 */

static inline char* createString(const char* s) {
    return invoke<char*>(0xFA925AC00EB830B9, 10, (char*)"LITERAL_STRING", (char*)s);
}

static void drawText(float x, float y, const char* str, float scale, int r, int g, int b, int a) {
    HUD::SET_TEXT_SCALE(0.0f, scale);
    HUD::_SET_TEXT_COLOR(r, g, b, a);
    HUD::SET_TEXT_CENTRE(0);
    HUD::SET_TEXT_DROPSHADOW(0, 0, 0, 0, 0);
    HUD::_DISPLAY_TEXT(createString(str), x, y);
}

static void renderLines() {
    std::vector<NemoLine> lines;
    gQ->copyLines(lines);
    DWORD now = GetTickCount();
    /* 取最旧的活动字幕（一次只显示一条，避免刷屏） */
    std::string show;
    for (size_t i = 0; i < lines.size(); i++) {
        DWORD age = now - lines[i].startTick;
        if (age > (DWORD)lines[i].lifeMs) continue;
        show = wide_to_utf8(lines[i].text);
        break;
    }
    if (!show.empty()) {
        drawText(0.5f, 0.42f, show.c_str(), 0.55f, 255, 236, 180, 255);
    }
}

/* ---------------- 主循环 ---------------- */

void ScriptMain() {
    srand(GetTickCount());
    gPhotonCount = PHOTON_TOTAL;
    nlog("nemo init: script main started (field q1=%d q2=%d salt=%lld photons=%d)\n",
         q1, q2, worldSalt, gPhotonCount);
    while (true) {
        nemoTick++;
        Ped pl = PLAYER::GET_PLAYER_PED(0);
        if (ENTITY::DOES_ENTITY_EXIST(pl) && !ENTITY::IS_ENTITY_DEAD(pl)) {
            Vector3 pos = ENTITY::GET_ENTITY_COORDS(pl, true, true);
            gLastPos = pos;
            if (nemoTick % 60 == 0) photonsReseed(pos);   /* 每秒重采样锚点 */
            photonsUpdate(pos, 1.0f / 60.0f);
            photonsRender();                              /* 每帧：三层光幕光子 */
            rippleCheck(pos);                             /* 每帧：玩家=噪声源 */
            if (nemoTick % 300 == 0) nodesScan(pos);      /* 每 5 秒：图纹节点扫描 */
            nodesRender();                                /* 每帧：节点光柱 */
            pulseTick();                                  /* 每帧：事件脉冲计时 */
            if (nemoTick % 30 == 0) measureField(pos);
            if (nemoTick % 20 == 0) worldGen();
        }
        if (nemoTick % 4 == 0) envPulse();
        if (nemoTick % 20 == 0) {
            if (nemoEnvOn && rand() % 400 == 0) {
                static const wchar_t* whispers[] = {
                    L"风在旧地图上吹出新的形状",
                    L"所有亡者的马都记得回家的路",
                    L"混沌不是无序，是未被读懂的秩序",
                    L"你踩过的每一寸土，都在另一端被写下",
                };
                gQ->say(whispers[rand() % 4], 5000);
                pulseTrigger(0.7f, 0.5f, 1.0f, 0.7f, 70); /* 紫脉冲：低语 */
            }
        }
        renderLines();
        renderHud();
        if (nemoTick % 600 == 0) {
            /* 每 10 秒记一次运行状态（实证：粒子渲染 + 场度量在跑） */
            nlog("[run] tick=%d photons=%d R=%.2f H=%.2f S=%.2f k=%.2f\n",
                 nemoTick, gPhotonCount, gR, gH, gS, gKappa);
        }
        WAIT(0);
    }
}
