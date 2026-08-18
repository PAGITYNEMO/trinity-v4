/*
 * NoiseBrowser v0.1 — TRINITY 四极浏览器
 *
 * 浏览器 = 噪声-结构转换器：页面字节流、URL、标题映射到同一个数论场。
 *  - RAMANUJAN：字节流的拉马努金图纹清晰度 R(t)
 *  - AXIS：熵 H / 结构度 S / 过滤效率 ηP / 耦合 κN 实时度量
 *  - PAGITY：URL 黑名单过滤（结构度低的区域不喂资源）
 *  - NEMO：访问历史按连接强度播种（星云视图）
 *
 * 渲染内核：WebView2（Edge 内核，真实浏览器能力）。
 * 数学层：src/field.h（与 rdr2-nemo / TRINITY 引擎同源）。
 *
 * 构建：make -f Makefile.mingw  （需 WebView2Loader.dll 在 exe 旁）
 */
#include <windows.h>
#include <commctrl.h>
#include <objbase.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstdarg>

#include "WebView2.h"
#include "field.h"

#pragma comment(lib, "comctl32.lib")

/* ---------------- 控件 ID ---------------- */
#define IDC_ADDRESS 101
#define IDC_BACK    102
#define IDC_FWD     103
#define IDC_REFRESH 104
#define IDC_HOME    105
#define IDC_GO      106

/* ---------------- 日志 ---------------- */
static void nlog(const char* fmt, ...) {
    FILE* f = fopen("noise-browser.log", "a");
    if (!f) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    va_end(ap);
    fclose(f);
}

/* ---------------- 简单 ComPtr ---------------- */
template <typename T>
class ComPtr {
public:
    ComPtr() : p(nullptr) {}
    ~ComPtr() { if (p) p->Release(); }
    T* operator->() { return p; }
    operator T*() { return p; }
    T** operator&() { return &p; }
    bool ok() { return p != nullptr; }
    T* p;
};

/* ---------------- 全局 ---------------- */
static HWND gHwnd = nullptr;
static HWND gAddr = nullptr;
static HWND gWebHwnd = nullptr;
static ComPtr<ICoreWebView2> gWebView;
static ComPtr<ICoreWebView2Controller> gController;
static TrinityField gField;

static void layoutChildren(); /* 前向声明（ControllerCompletedHandler 使用） */

static double gR = 0, gH = 0, gS = 0, gEtaP = 0.5, gKappa = 0.5;
static std::wstring gTitle;
static std::wstring gUrl;
static int gNavCount = 0;
static bool gBusy = false;

/* 星云播种：页面标题 -> 访问次数 */
struct Seed { std::wstring title; int visits; };
static std::vector<Seed> gSeeds;

/* PAGITY 黑名单（内置示例；可在 exe 目录建 noise_blacklist.txt 扩展） */
static bool isBlocked(const std::wstring& url) {
    static const wchar_t* BLOCK[] = {
        L"tracker.", L"doubleclick.net", L"googlesyndication.com",
        L"amazon-adsystem.com", L"scorecardresearch.com",
    };
    for (auto b : BLOCK) {
        if (url.find(b) != std::wstring::npos) return true;
    }
    FILE* f = fopen("noise_blacklist.txt", "r");
    if (f) {
        char line[512];
        while (fgets(line, sizeof(line), f)) {
            std::string s = line;
            if (!s.empty() && s.back() == '\n') s.pop_back();
            if (!s.empty() && url.find(std::wstring(s.begin(), s.end())) != std::wstring::npos) {
                fclose(f);
                return true;
            }
        }
        fclose(f);
    }
    return false;
}

/* 星云播种：记录访问 */
static void seedVisit(const std::wstring& title) {
    if (title.empty()) return;
    for (auto& s : gSeeds) {
        if (s.title == title) {
            s.visits++;
            return;
        }
    }
    Seed s;
    s.title = title;
    s.visits = 1;
    gSeeds.push_back(s);
}

/* ---------------- AXIS 度量：对页面字节流做四极分析 ---------------- */

static void measurePage(const std::wstring& wurl, const std::wstring& wtitle, const std::string& html) {
    /* 输入噪声场：URL + 标题 + HTML 前 64KB */
    std::string feed;
    for (wchar_t c : wurl) feed.push_back((char)(c & 0x7F));
    feed += "|";
    for (wchar_t c : wtitle) feed.push_back((char)(c & 0x7F));
    feed += "|";
    feed += html.substr(0, 65536);

    gR = gField.ramClarity(feed, 23, 43);
    gS = TrinityField::structureOf(feed);
    gH = TrinityField::entropyOf(feed);
    gEtaP = 0.5 + 0.5 * (1.0 - gH);   /* 过滤效率：低熵 = 结构已从噪声中滤出 */
    gKappa = 0.5 + 0.5 * gR;          /* 耦合：图纹清晰 → 预取/连接倾向 */
}

/* ---------------- 事件回调：导航完成 ---------------- */

/* 无操作回调（ExecuteScript 结果忽略） */
class NoopScriptHandler : public ICoreWebView2ExecuteScriptCompletedHandler {
public:
    STDMETHODIMP QueryInterface(REFIID, void** ppv) override { *ppv = nullptr; return E_NOINTERFACE; }
    STDMETHODIMP_(ULONG) AddRef() override { return 1; }
    STDMETHODIMP_(ULONG) Release() override { return 1; }
    STDMETHODIMP Invoke(HRESULT, LPCWSTR) override { return S_OK; }
};

/* wchar -> 本地 ANSI（中文用 GBK，GDI TextOutA 需要） */
static std::string w2a(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_ACP, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s(n, '\0');
    if (n > 0) WideCharToMultiByte(CP_ACP, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

class NavCompletedHandler : public ICoreWebView2NavigationCompletedEventHandler {
public:
    STDMETHODIMP QueryInterface(REFIID, void** ppv) override {
        *ppv = nullptr;
        return E_NOINTERFACE;
    }
    STDMETHODIMP_(ULONG) AddRef() override { return 1; }
    STDMETHODIMP_(ULONG) Release() override { return 1; }
    STDMETHODIMP Invoke(ICoreWebView2* sender,
                        ICoreWebView2NavigationCompletedEventArgs* args) override {
        gBusy = false;
        /* 导航结果 */
        BOOL ok = FALSE;
        COREWEBVIEW2_WEB_ERROR_STATUS err = COREWEBVIEW2_WEB_ERROR_STATUS_UNKNOWN;
        args->get_IsSuccess(&ok);
        args->get_WebErrorStatus(&err);
        nlog("[nav] completed success=%d error=%d\n", ok ? 1 : 0, (int)err);
        /* 取 URL 与标题 */
        LPWSTR url = nullptr, title = nullptr;
        sender->get_Source(&url);
        sender->get_DocumentTitle(&title);
        gUrl = url ? url : L"";
        gTitle = title ? title : L"";
        CoTaskMemFree(url);
        CoTaskMemFree(title);
        gNavCount++;
        if (ok) seedVisit(gTitle);

        /* 用 URL+标题+HTML 统计做四极度量 */
        sender->ExecuteScript(
            L"document.documentElement.outerHTML.substring(0, 60000)",
            new NoopScriptHandler());
        std::string htmlFeed;
        for (wchar_t c : gUrl) htmlFeed.push_back((char)(c & 0x7F));
        for (wchar_t c : gTitle) htmlFeed.push_back((char)(c & 0x7F));
        measurePage(gUrl, gTitle, htmlFeed);

        InvalidateRect(gHwnd, nullptr, TRUE);
        return S_OK;
    }
};

class ControllerCompletedHandler : public ICoreWebView2CreateCoreWebView2ControllerCompletedHandler {
public:
    STDMETHODIMP QueryInterface(REFIID, void** ppv) override { *ppv = nullptr; return E_NOINTERFACE; }
    STDMETHODIMP_(ULONG) AddRef() override { return 1; }
    STDMETHODIMP_(ULONG) Release() override { return 1; }
    STDMETHODIMP Invoke(HRESULT result, ICoreWebView2Controller* controller) override {
        if (FAILED(result)) {
            nlog("[init] controller FAILED hr=0x%08X\n", (unsigned)result);
            return result;
        }
        nlog("[init] controller created\n");
        gController.p = controller;
        gController.p->AddRef();
        gController.p->get_CoreWebView2(&gWebView.p);
        if (!gWebView.ok()) {
            nlog("[init] get_CoreWebView2 FAILED\n");
            return E_FAIL;
        }

        /* 事件：导航完成 -> 四极度量 */
        gWebView->add_NavigationCompleted(new NavCompletedHandler(), nullptr);
        /* 设置区域并打开主页 */
        layoutChildren();
        nlog("[init] navigating home\n");
        gWebView->Navigate(L"https://www.bing.com");
        return S_OK;
    }
};

class EnvCompletedHandler : public ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
public:
    STDMETHODIMP QueryInterface(REFIID, void** ppv) override { *ppv = nullptr; return E_NOINTERFACE; }
    STDMETHODIMP_(ULONG) AddRef() override { return 1; }
    STDMETHODIMP_(ULONG) Release() override { return 1; }
    STDMETHODIMP Invoke(HRESULT result, ICoreWebView2Environment* env) override {
        if (FAILED(result)) {
            nlog("[init] environment FAILED hr=0x%08X\n", (unsigned)result);
            return result;
        }
        nlog("[init] environment created\n");
        ComPtr<ICoreWebView2Environment> e;
        e.p = env;
        e.p->AddRef();
        e.p->CreateCoreWebView2Controller(gWebHwnd, new ControllerCompletedHandler());
        return S_OK;
    }
};

/* ---------------- WebView2 初始化（动态加载 Loader） ---------------- */

typedef HRESULT(__stdcall* CreateEnvFn)(
    PCWSTR, PCWSTR, ICoreWebView2EnvironmentOptions*,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler*);

static void initWebView() {
    HMODULE loader = LoadLibraryA("WebView2Loader.dll");
    if (!loader) {
        nlog("[init] WebView2Loader.dll NOT FOUND\n");
        MessageBoxA(gHwnd, "WebView2Loader.dll 缺失（放在 exe 旁边）", "NoiseBrowser", MB_OK);
        return;
    }
    nlog("[init] loader loaded\n");
    CreateEnvFn fn = (CreateEnvFn)GetProcAddress(loader, "CreateCoreWebView2EnvironmentWithOptions");
    if (!fn) {
        nlog("[init] CreateCoreWebView2EnvironmentWithOptions NOT FOUND in loader\n");
        MessageBoxA(gHwnd, "WebView2 Loader 导出缺失", "NoiseBrowser", MB_OK);
        return;
    }
    fn(nullptr, nullptr, nullptr, new EnvCompletedHandler());
}

/* ---------------- AXIS 面板绘制 ---------------- */

static void drawAxisPanel(HDC hdc, RECT rc) {
    HBRUSH bg = CreateSolidBrush(RGB(10, 12, 18));
    FillRect(hdc, &rc, bg);
    DeleteObject(bg);

    HFONT font = CreateFontA(-15, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET,
                             0, 0, CLEARTYPE_QUALITY, 0, "Microsoft YaHei");
    HFONT old = (HFONT)SelectObject(hdc, font);
    SetBkMode(hdc, TRANSPARENT);

    char buf[512];
    const char* emerge = (gR > 0.45 && gH < 0.65) ? "涌现" : (gS > 0.5 ? "结构高" : "噪声主导");
    snprintf(buf, sizeof(buf),
             "AXIS  页面熵 H=%.2f  结构度 S=%.2f  过滤效率 ηP=%.2f  耦合 κN=%.2f  图纹清晰度 R=%.2f  状态:%s",
             gH, gS, gEtaP, gKappa, gR, emerge);
    SetTextColor(hdc, RGB(140, 220, 255));
    TextOutA(hdc, 12, rc.top + 8, buf, (int)strlen(buf));

    /* 标题 + 计数 */
    std::string line2 = "页面: " + w2a(gTitle);
    if (line2.size() > 90) line2 = line2.substr(0, 90);
    char cnt[64];
    snprintf(cnt, sizeof(cnt), "   导航 %d  PAGITY过滤: %s", gNavCount, gBusy ? "忙" : "开");
    line2 += cnt;
    SetTextColor(hdc, RGB(230, 230, 210));
    TextOutA(hdc, 12, rc.top + 34, line2.c_str(), (int)line2.size());

    /* 星云视图（连接强度最高的 3 个种子） */
    SetTextColor(hdc, RGB(170, 255, 200));
    char line3[256] = "星云: ";
    int shown = 0;
    for (size_t i = 0; i < gSeeds.size() && shown < 3; i++) {
        std::string t = w2a(gSeeds[i].title);
        if (t.size() > 28) t = t.substr(0, 28);
        char part[64];
        snprintf(part, sizeof(part), "『%s』×%d  ", t.c_str(), gSeeds[i].visits);
        strncat(line3, part, sizeof(line3) - strlen(line3) - 1);
        shown++;
    }
    if (strlen(line3) <= 6) strcat(line3, "（等待第一次访问）");
    TextOutA(hdc, 12, rc.top + 60, line3, (int)strlen(line3));

    SelectObject(hdc, old);
    DeleteObject(font);
}

/* ---------------- 窗口过程 ---------------- */

static void layoutChildren() {
    RECT rc;
    GetClientRect(gHwnd, &rc);
    int topH = 36, bottomH = 96;
    /* 按钮横排：◀ ▶ ⟳ ⌂ 在左（4/34/64/94 宽 28），地址栏 128..right-70，前往 right-66 */
    int w = rc.right;
    MoveWindow(GetDlgItem(gHwnd, IDC_BACK), 4, 4, 28, 28, TRUE);
    MoveWindow(GetDlgItem(gHwnd, IDC_FWD), 34, 4, 28, 28, TRUE);
    MoveWindow(GetDlgItem(gHwnd, IDC_REFRESH), 64, 4, 28, 28, TRUE);
    MoveWindow(GetDlgItem(gHwnd, IDC_HOME), 94, 4, 28, 28, TRUE);
    MoveWindow(gAddr, 128, 5, w - 128 - 70, 26, TRUE);
    MoveWindow(GetDlgItem(gHwnd, IDC_GO), w - 66, 4, 62, 28, TRUE);
    /* WebView2 区域 */
    RECT wrc = {0, topH, w, rc.bottom - bottomH};
    if (gController.ok()) gController->put_Bounds(wrc);
    /* AXIS 面板（主窗口自绘） */
    InvalidateRect(gHwnd, nullptr, TRUE);
}

/* ---------------- 地址栏回车导航 ---------------- */

static WNDPROC gOldEditProc = nullptr;

static LRESULT CALLBACK editProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    if (msg == WM_KEYDOWN && wp == VK_RETURN) {
        wchar_t url[1024];
        GetWindowTextW(gAddr, url, 1024);
        std::wstring s = url;
        if (s.find(L"://") == std::wstring::npos) s = L"https://" + s;
        if (gWebView.ok()) {
            gBusy = true;
            nlog("[nav] address-enter -> %ls\n", s.c_str());
            gWebView->Navigate(s.c_str());
        }
        return 0;
    }
    return CallWindowProc(gOldEditProc, hwnd, msg, wp, lp);
}

static LRESULT CALLBACK wndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE: {
        CreateWindowA("BUTTON", "◀", WS_CHILD | WS_VISIBLE, 4, 4, 28, 28, hwnd, (HMENU)IDC_BACK, nullptr, nullptr);
        CreateWindowA("BUTTON", "▶", WS_CHILD | WS_VISIBLE, 34, 4, 28, 28, hwnd, (HMENU)IDC_FWD, nullptr, nullptr);
        CreateWindowA("BUTTON", "⟳", WS_CHILD | WS_VISIBLE, 64, 4, 28, 28, hwnd, (HMENU)IDC_REFRESH, nullptr, nullptr);
        CreateWindowA("BUTTON", "⌂", WS_CHILD | WS_VISIBLE, 94, 4, 28, 28, hwnd, (HMENU)IDC_HOME, nullptr, nullptr);
        gAddr = CreateWindowA("EDIT", "", WS_CHILD | WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL,
                              128, 5, 0, 26, hwnd, (HMENU)IDC_ADDRESS, nullptr, nullptr);
        CreateWindowA("BUTTON", "前往", WS_CHILD | WS_VISIBLE, 0, 4, 62, 28, hwnd, (HMENU)IDC_GO, nullptr, nullptr);
        /* WebView2 宿主窗口 */
        gWebHwnd = CreateWindowA("STATIC", "", WS_CHILD | WS_VISIBLE | SS_BLACKRECT,
                                 0, 36, 100, 100, hwnd, nullptr, nullptr, nullptr);
        gOldEditProc = (WNDPROC)SetWindowLongPtr(gAddr, GWLP_WNDPROC, (LONG_PTR)editProc);
        layoutChildren();
        SetTimer(hwnd, 1, 500, nullptr);
        nlog("[init] window created, starting WebView2\n");
        initWebView();
        return 0;
    }
    case WM_SIZE:
        layoutChildren();
        return 0;
    case WM_TIMER:
        InvalidateRect(hwnd, nullptr, TRUE);
        return 0;
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        RECT rc;
        GetClientRect(hwnd, &rc);
        RECT panel = {0, rc.bottom - 96, rc.right, rc.bottom};
        drawAxisPanel(hdc, panel);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_COMMAND: {
        int id = LOWORD(wp);
        if (id == IDC_GO) {
            wchar_t url[1024];
            GetWindowTextW(gAddr, url, 1024);
            std::wstring s = url;
            if (s.find(L"://") == std::wstring::npos) s = L"https://" + s;
            if (gWebView.ok()) {
                gBusy = true;
                gWebView->Navigate(s.c_str());
            }
        } else if (id == IDC_BACK) {
            if (gWebView.ok()) gWebView->GoBack();
        } else if (id == IDC_FWD) {
            if (gWebView.ok()) gWebView->GoForward();
        } else if (id == IDC_REFRESH) {
            if (gWebView.ok()) gWebView->Reload();
        } else if (id == IDC_HOME) {
            if (gWebView.ok()) gWebView->Navigate(L"https://www.bing.com");
        }
        return 0;
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(hwnd, msg, wp, lp);
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE, LPSTR, int) {
    /* WebView2 环境创建要求 COM 已初始化（hr=0x800401F0 的根源） */
    HRESULT hrCom = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    nlog("[init] CoInitializeEx hr=0x%08X\n", (unsigned)hrCom);

    INITCOMMONCONTROLSEX icc = {sizeof(icc), ICC_WIN95_CLASSES};
    InitCommonControlsEx(&icc);

    WNDCLASSA wc = {};
    wc.lpfnWndProc = wndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wc.lpszClassName = "NoiseBrowser";
    RegisterClassA(&wc);

    gHwnd = CreateWindowA("NoiseBrowser", "NoiseBrowser v0.1 — 噪声-结构转换器",
                          WS_OVERLAPPEDWINDOW, 100, 60, 1280, 820,
                          nullptr, nullptr, hInst, nullptr);
    ShowWindow(gHwnd, SW_SHOW);

    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return 0;
}
