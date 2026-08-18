/*
 * field.h — NoiseBrowser 数论场（与 rdr2-nemo / TRINITY 引擎同源移植）
 *
 * 浏览器被看作噪声-结构转换器：页面字节流、URL、标题、DOM 统计
 * 都映射到同一个数论场。度量：图纹清晰度 R(t)、结构度 S、熵 H、
 * 耦合 κ（四极：RAMANUJAN 观察 / AXIS 度量）。
 */
#pragma once

#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

class TrinityField {
public:
    static const int QMAX = 63;

    TrinityField() {
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

    static long hash64(long x, long salt) {
        long h = x * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >> 33;
        return h;
    }

    double c(int q, int n) const {
        int m = n % q;
        if (m < 0) m += q;
        return cq[q][m];
    }

    double phiOf(int q) const { return (double)phi[q]; }

    /** 字符串的拉马努金图纹：按 q 扫描字符的同余分布，返回清晰度。 */
    double ramClarity(const std::string& s, int q1, int q2) const {
        if (s.empty()) return 0;
        int n = (int)s.size();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += c(q1, i) * (unsigned char)s[i];
        }
        sum += 0.0;
        /* 归一化到 [0,1]：|c_q1 自相关 + c_q2 交叉| 的干涉 */
        double t = 0;
        for (int q = 3; q <= 16; q++) {
            int hist[16] = {0};
            for (int i = 0; i < n; i++) {
                int r = ((unsigned char)s[i] + i) % q;
                if (r < 0) r += q;
                hist[r]++;
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double)n;
                if (p > 0) hE -= p * log(p);
            }
            double rq = 1 - hE / log((double)q);
            if (rq > t) t = rq;
        }
        /* 活跃度：字节方差 */
        long acc = 0;
        for (int i = 0; i < n; i++) acc += (unsigned char)s[i];
        double mean = acc / (double)n;
        double var = 0;
        for (int i = 0; i < n; i++) {
            double d = (unsigned char)s[i] - mean;
            var += d * d;
        }
        double std = sqrt(var / n);
        double activity = std / 48.0;
        if (activity > 1) activity = 1;
        return t * activity;
    }

    /** 结构度：相邻字节差分的拉普拉斯能量归一化。 */
    static double structureOf(const std::string& s) {
        if (s.size() < 3) return 0;
        double lap = 0, e = 1e-9;
        for (size_t i = 1; i + 1 < s.size(); i++) {
            double l = 2 * (int)(unsigned char)s[i] - (int)(unsigned char)s[i - 1] - (int)(unsigned char)s[i + 1];
            lap += l * l;
            e += (double)(unsigned char)s[i] * (unsigned char)s[i];
        }
        double v = lap / e * 0.02;
        return v > 1 ? 1 : v;
    }

    /** 熵：字节分布 Shannon 熵归一化。 */
    static double entropyOf(const std::string& s) {
        if (s.empty()) return 0;
        int hist[256] = {0};
        for (size_t i = 0; i < s.size(); i++) hist[(unsigned char)s[i]]++;
        double hE = 0;
        for (int i = 0; i < 256; i++) {
            if (hist[i] > 0) {
                double p = hist[i] / (double)s.size();
                hE -= p * log(p);
            }
        }
        return hE / log(256.0);
    }

private:
    int phi[QMAX + 1];
    int mu[QMAX + 1];
    double cq[QMAX + 1][QMAX + 1];
};
