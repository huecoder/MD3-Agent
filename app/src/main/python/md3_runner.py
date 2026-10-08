import contextlib
import ctypes
import io
import os
import threading
import traceback


def run(code, workdir, timeout_seconds):
    output = io.StringIO()
    state = {"failed": False}

    def execute():
        try:
            os.chdir(workdir)
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                exec(compile(code, "<agent>", "exec"), {"__name__": "__main__"})
        except SystemExit as exit_request:
            if exit_request.code not in (None, 0):
                state["failed"] = True
                output.write(f"\nExited with code {exit_request.code}")
        except BaseException:
            state["failed"] = True
            output.write(traceback.format_exc())

    worker = threading.Thread(target=execute, daemon=True)
    worker.start()
    worker.join(timeout_seconds)
    timed_out = worker.is_alive()
    if timed_out:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(worker.ident), ctypes.py_object(TimeoutError))
        worker.join(2)
    return [output.getvalue(), state["failed"], timed_out]
