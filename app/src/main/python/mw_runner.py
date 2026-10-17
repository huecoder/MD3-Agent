import ctypes
import io
import os
import sys
import threading
import time
import traceback

import phone

POLL_SECONDS = 0.05
GRACE_SECONDS = 2

_install_lock = threading.Lock()


class _Capture(io.TextIOBase):
    def __init__(self, fallback):
        self._fallback = fallback
        self._by_thread = {}
        self._latest = None

    def attach(self, buffer):
        self._by_thread[threading.get_ident()] = buffer
        self._latest = buffer

    def detach(self, buffer):
        self._by_thread.pop(threading.get_ident(), None)
        if self._latest is buffer:
            self._latest = None

    def writable(self):
        return True

    def write(self, text):
        target = self._by_thread.get(threading.get_ident()) or self._latest or self._fallback
        return target.write(text)

    def flush(self):
        pass


def _captures():
    with _install_lock:
        if not isinstance(sys.stdout, _Capture):
            sys.stdout = _Capture(sys.stdout)
        if not isinstance(sys.stderr, _Capture):
            sys.stderr = _Capture(sys.stderr)
        return sys.stdout, sys.stderr


class Script:
    def __init__(self, code, workdir, bridge=None):
        self.code = code
        self.workdir = workdir
        self.bridge = bridge
        self.output = io.StringIO()
        self.failed = False
        self.cancelled = threading.Event()
        self.worker = threading.Thread(target=self._execute, daemon=True)

    def start(self):
        self.worker.start()

    def wait(self, timeout_seconds):
        deadline = time.monotonic() + timeout_seconds
        while self.worker.is_alive() and not self.cancelled.is_set() and time.monotonic() < deadline:
            self.worker.join(POLL_SECONDS)
        timed_out = self.worker.is_alive() and not self.cancelled.is_set()
        if timed_out:
            self._interrupt(TimeoutError)
            self.worker.join(GRACE_SECONDS)
        return [self.output.getvalue(), self.failed, timed_out]

    def cancel(self):
        self.cancelled.set()
        self._interrupt(KeyboardInterrupt)

    def _interrupt(self, error):
        if self.worker.is_alive():
            ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(self.worker.ident), ctypes.py_object(error))

    def _execute(self):
        captures = _captures()
        for capture in captures:
            capture.attach(self.output)
        phone.attach(self.bridge)
        try:
            os.chdir(self.workdir)
            exec(compile(self.code, "<agent>", "exec"), {"__name__": "__main__"})
        except SystemExit as exit_request:
            if exit_request.code not in (None, 0):
                self.failed = True
                self.output.write(f"\nExited with code {exit_request.code}")
        except BaseException:
            self.failed = True
            self.output.write(traceback.format_exc())
        finally:
            phone.detach()
            for capture in captures:
                capture.detach(self.output)


def start(code, workdir, bridge=None):
    script = Script(code, workdir, bridge)
    script.start()
    return script
