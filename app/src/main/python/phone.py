import json
import os
import re
import runpy
import sys
import threading
import time

POLL_SECONDS = 0.03
MILLIS = 1000
MAX_WAIT_MS = 10000
LABEL_SEPARATOR = " · "
ROUTINES_DIRECTORY = "routines"
ROUTINE_NAME = re.compile(r"[A-Za-z0-9_-]+")
NOT_ALLOWED = (
    "Python is not allowed to control the phone. "
    "Turn on 'Python can control the phone' in Settings > Phone use."
)

_local = threading.local()


class PhoneError(Exception):
    pass


class Cancelled(KeyboardInterrupt):
    pass


def attach(bridge):
    _local.bridge = bridge


def detach():
    _local.bridge = None


def _bridge():
    bridge = getattr(_local, "bridge", None)
    if bridge is None:
        raise PermissionError(NOT_ALLOWED)
    return bridge


def _call(operation, payload):
    bridge = _bridge()
    handle = bridge.begin(operation, json.dumps(payload))
    reply = bridge.poll(handle)
    while reply is None:
        time.sleep(POLL_SECONDS)
        reply = bridge.poll(handle)
    data = json.loads(str(reply))
    if data.get("cancelled"):
        raise Cancelled(data.get("error", "Stopped"))
    if "error" in data:
        raise PhoneError(data["error"])
    return data


class Element:
    def __init__(self, data):
        self.number = data["number"]
        self.role = data["role"]
        self.label = data["label"]
        self.x = data["x"]
        self.y = data["y"]
        self.left = data["left"]
        self.top = data["top"]
        self.right = data["right"]
        self.bottom = data["bottom"]
        self.clickable = data["clickable"]
        self.editable = data["editable"]
        self.scrollable = data["scrollable"]
        self.checked = data["checked"]
        self.focused = data["focused"]
        self.id = data["id"]
        self.partial = data["partial"]

    def matches(self, text=None, id=None, role=None, exact=False):
        if text is not None:
            wanted = text.lower()
            if exact:
                parts = [part.strip().lower() for part in self.label.split(LABEL_SEPARATOR)]
                if wanted not in parts:
                    return False
            elif wanted not in self.label.lower():
                return False
        if id is not None and (self.id or "").lower() != id.split(":id/")[-1].lower():
            return False
        if role is not None and self.role.lower() != role.lower():
            return False
        return True

    def __repr__(self):
        return f"[{self.number}] {self.role} {self.label!r} @{self.x},{self.y}"


class Screen:
    def __init__(self, data):
        self.app = data["app"]
        self.width = data["width"]
        self.height = data["height"]
        self.text = data["text"]
        self.elements = [Element(item) for item in data["elements"]]

    def find(self, text=None, id=None, role=None, exact=False):
        return [element for element in self.elements if element.matches(text, id, role, exact)]

    def has(self, text, exact=False):
        return bool(self.find(text=text, exact=exact))

    def __str__(self):
        return self.text


class Result:
    def __init__(self, data):
        self.ok = data["ok"]
        self.text = data.get("text", "")
        self.changed = data.get("changed")
        self.screen = data.get("screen")

    def __bool__(self):
        return self.ok

    def __str__(self):
        return self.text


def _selector(text, id, role, exact, index):
    selector = {}
    if text is not None:
        selector["text"] = text
    if id is not None:
        selector["id"] = id
    if role is not None:
        selector["role"] = role
    if exact:
        selector["exact"] = True
    if index is not None:
        selector["index"] = index
    return selector


def _named(target, text):
    if isinstance(target, str):
        if text is not None:
            raise ValueError("Pass the text either as the target or as text=, not both")
        return target
    return text


def _aim(target, text, id, role, exact, index):
    if isinstance(target, Element):
        return {"element": target.number}
    if isinstance(target, int) and not isinstance(target, bool):
        return {"element": target}
    if isinstance(target, dict):
        return {"target": dict(target)}
    if target is not None and not isinstance(target, str):
        raise TypeError("A target is a text, an element number, an Element or a dict with text, id and role")
    selector = _selector(_named(target, text), id, role, exact, index)
    return {"target": selector} if selector else {}


def _required_aim(action, target, text, id, role, exact, index):
    aim = _aim(target, text, id, role, exact, index)
    if not aim:
        raise ValueError(f"{action} needs a target: a text, an element number, or id= or role=")
    return aim


def _point(point):
    if point is None:
        return None
    x, y = point
    return {"x": int(x), "y": int(y)}


def _millis(seconds):
    return None if seconds is None else int(seconds * MILLIS)


def _act(action, say=None, strict=True, **fields):
    request = {"action": action, "observe": "none"}
    request.update({key: value for key, value in fields.items() if value is not None})
    if say is not None:
        request["say"] = say
    result = Result(_call("act", request))
    if strict and not result.ok:
        raise PhoneError(result.text)
    return result


def screen(*, text=None, id=None, role=None, exact=False, index=None, detailed=False, say=None):
    payload = {"detailed": detailed}
    selector = _selector(text, id, role, exact, index)
    if selector:
        payload["find"] = selector
    if say is not None:
        payload["say"] = say
    return Screen(_call("screen", payload))


def find(text=None, *, id=None, role=None, exact=False, index=None):
    if text is None and id is None and role is None:
        raise ValueError("find needs a text, an id or a role")
    return screen(text=text, id=id, role=role, exact=exact, index=index).elements


def exists(text=None, *, id=None, role=None, exact=False):
    return bool(find(text, id=id, role=role, exact=exact))


def tap(target=None, *, text=None, id=None, role=None, exact=False, index=None, say=None):
    return _act("tap", say, **_required_aim("tap", target, text, id, role, exact, index))


def long_press(target=None, *, text=None, id=None, role=None, exact=False, index=None, say=None):
    return _act("long_press", say, **_required_aim("long_press", target, text, id, role, exact, index))


def double_tap(target=None, *, text=None, id=None, role=None, exact=False, index=None, say=None):
    return _act("double_tap", say, **_required_aim("double_tap", target, text, id, role, exact, index))


def tap_at(x, y, say=None):
    return _act("tap_at", say, x=int(x), y=int(y))


def type_text(text, target=None, *, id=None, role=None, exact=False, index=None, submit=False, say=None):
    return _act("type", say, text=text, submit=True if submit else None, **_aim(target, None, id, role, exact, index))


def scroll(direction, target=None, *, text=None, id=None, role=None, exact=False, index=None, say=None):
    return _act("scroll", say, direction=direction, **_aim(target, text, id, role, exact, index))


def scroll_to(target=None, *, text=None, id=None, role=None, exact=False, index=None, direction=None, max_swipes=None, say=None):
    aim = _required_aim("scroll_to", target, text, id, role, exact, index)
    if "target" not in aim:
        raise ValueError("scroll_to looks for text, an id or a role, not for an element number")
    return _act("scroll_to", say, direction=direction, max_swipes=max_swipes, **aim)


def swipe(direction=None, *, start=None, end=None, distance=None, duration=None, say=None):
    fields = {"direction": direction, "distance": distance, "duration_ms": _millis(duration), "from": _point(start), "to": _point(end)}
    return _act("swipe", say, **fields)


def drag(points, duration=None, say=None):
    return _act("drag", say, path=[_point(point) for point in points], duration_ms=_millis(duration))


def press(key, say=None):
    return _act("press", say, key=key)


def open_app(name, say=None):
    return _act("open_app", say, app=name)


def wait(seconds, say=None):
    remaining = int(seconds * MILLIS)
    while remaining > 0:
        step = min(remaining, MAX_WAIT_MS)
        _act("wait", say, ms=step)
        remaining -= step


def wait_for(text=None, *, gone=None, app=None, idle=False, changed=False, timeout=10, say=None):
    conditions = [
        (name, given)
        for name, given in (("text_visible", text), ("text_gone", gone), ("app", app), ("idle", idle or None), ("screen_changed", changed or None))
        if given is not None
    ]
    if len(conditions) != 1:
        raise ValueError("Pass exactly one of text, gone, app, idle or changed")
    condition, given = conditions[0]
    fields = {"condition": condition, "timeout_ms": int(timeout * MILLIS)}
    if condition in ("text_visible", "text_gone"):
        fields["text"] = given
    if condition == "app":
        fields["app"] = given
    return _act("wait_for", say, strict=False, **fields)


def say(text):
    _call("say", {"text": text})


def do(action, **fields):
    return _act(action, fields.pop("say", None), **fields)


def routines():
    if not os.path.isdir(ROUTINES_DIRECTORY):
        return []
    return sorted(name[: -len(".py")] for name in os.listdir(ROUTINES_DIRECTORY) if name.endswith(".py"))


def routine(name, **params):
    if not ROUTINE_NAME.fullmatch(name):
        raise PhoneError("A routine name has letters, digits, dash and underscore only")
    path = os.path.join(ROUTINES_DIRECTORY, name + ".py")
    if not os.path.isfile(path):
        saved = ", ".join(routines()) or "none"
        raise PhoneError(f"No routine called {name}, saved routines: {saved}")
    namespace = runpy.run_path(path, init_globals={"params": params, "phone": sys.modules[__name__]})
    return namespace.get("result")


type = type_text

__all__ = [
    "Cancelled", "Element", "PhoneError", "Result", "Screen", "do", "double_tap", "drag", "exists", "find", "long_press",
    "open_app", "press", "routine", "routines", "say", "screen", "scroll", "scroll_to", "swipe", "tap", "tap_at",
    "type_text", "wait", "wait_for",
]
