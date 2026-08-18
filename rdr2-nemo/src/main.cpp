/*
 * rdr2-nemo — main.cpp
 * 插件入口：DllMain 注册脚本 + 键盘钩子；ScriptMain 启动 AI 服务器。
 */
#include "nemo.h"
#include "..\inc\main.h"

void ScriptMain();
void onKey(DWORD key, WORD, BYTE, BOOL, BOOL, BOOL, BOOL);

NemoQueue* gQ = nullptr;

BOOL APIENTRY DllMain(HMODULE hInstance, DWORD reason, LPVOID)
{
    switch (reason) {
    case DLL_PROCESS_ATTACH:
#ifdef SCRIPT_HOOK_EXTERN_C
        if (!initScriptHook()) return TRUE; /* ScriptHookRDR2.dll 未加载则不启用 */
#endif
        gQ = new NemoQueue();
        scriptRegister(hInstance, ScriptMain);
        keyboardHandlerRegister(onKey);
        break;
    case DLL_PROCESS_DETACH:
        keyboardHandlerUnregister(onKey);
        scriptUnregister(hInstance);
        delete gQ;
        gQ = nullptr;
        break;
    }
    return TRUE;
}
