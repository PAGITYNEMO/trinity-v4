/*
 * rdr2-nemo — TRINITY v4.0 世界观 × RDR2 单机 mod
 * 共享头：端口、键位、线程安全队列、NEMO 常量
 *
 * 全部游戏 natives 只能在 script thread 调用；AI/winsock 线程只读写
 * 这里的共享队列，主循环每帧取命令执行。
 */
#pragma once

#include <windows.h>
#include <string>
#include <deque>
#include <vector>

#define NEMO_PORT        43072
#define NEMO_KEY_TOGGLE  VK_F8      /* NEMO 环境开关 */
#define NEMO_KEY_STATUS  VK_F9      /* 状态显示 */

/* 一条来自 AI 或控制台的命令（natives 执行于主循环） */
struct NemoCmd {
    std::string type;   /* teleport / set_time / set_weather / say / spawn /
                           give_weapon / heading / visible / health / nemo / stop */
    std::string a1, a2, a3;  /* 泛化参数 */
    float f1 = 0, f2 = 0, f3 = 0;
    int   i1 = -99999;
    int   i2 = -99999;
};

/* 一条屏幕字幕 */
struct NemoLine {
    std::wstring text;   /* UTF-8 转 wide 后存储，RDR2 原生支持 Unicode 字幕 */
    DWORD startTick = 0;
    int   lifeMs = 6000;
};

/* 线程安全容器 */
class NemoQueue {
public:
    void push(const NemoCmd& c) { EnterCriticalSection(&cs); q.push_back(c); LeaveCriticalSection(&cs); }
    bool pop(NemoCmd& out) {
        EnterCriticalSection(&cs);
        bool ok = !q.empty();
        if (ok) { out = q.front(); q.pop_front(); }
        LeaveCriticalSection(&cs);
        return ok;
    }
    void say(const std::wstring& text, int lifeMs = 6000) {
        NemoLine l; l.text = text; l.startTick = GetTickCount(); l.lifeMs = lifeMs;
        EnterCriticalSection(&cs); lines.push_back(l); LeaveCriticalSection(&cs);
    }
    void copyLines(std::vector<NemoLine>& out) {
        EnterCriticalSection(&cs);
        out.assign(lines.begin(), lines.end());
        LeaveCriticalSection(&cs);
    }
    /* 玩家状态（AI 线程只读） */
    void writeState(float px, float py, float pz, float heading, int health, int hour, int minute, const char* weather) {
        EnterCriticalSection(&cs);
        st.px = px; st.py = py; st.pz = pz; st.heading = heading; st.health = health;
        st.hour = hour; st.minute = minute;
        st.weather = weather;
        LeaveCriticalSection(&cs);
    }
    void readState(float& px, float& py, float& pz, float& heading, int& health, int& hour, int& minute, std::string& weather) {
        EnterCriticalSection(&cs);
        px = st.px; py = st.py; pz = st.pz; heading = st.heading; health = st.health;
        hour = st.hour; minute = st.minute; weather = st.weather;
        LeaveCriticalSection(&cs);
    }
    NemoQueue() { InitializeCriticalSection(&cs); }
    ~NemoQueue() { DeleteCriticalSection(&cs); }
private:
    CRITICAL_SECTION cs;
    std::deque<NemoCmd> q;
    std::deque<NemoLine> lines;
    struct State { float px, py, pz, heading; int health, hour, minute; std::string weather; } st;
};

/* 全局单例（DllMain 创建，DLL_PROCESS_DETACH 销毁） */
extern NemoQueue* gQ;

/* UTF-8 -> wide */
std::wstring utf8_to_wide(const char* s);
/* wide -> UTF-8 */
std::string wide_to_utf8(const std::wstring& w);
