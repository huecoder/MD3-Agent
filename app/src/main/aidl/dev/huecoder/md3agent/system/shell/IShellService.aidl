package dev.huecoder.md3agent.system.shell;

import android.os.Bundle;

interface IShellService {
    void destroy() = 16777114;

    Bundle run(String command, long timeoutMs) = 1;
}
