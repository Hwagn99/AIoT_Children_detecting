"""Theo dõi 'trẻ ở một mình bao lâu', có độ trễ chống nhấp nháy khi YOLO rớt khung."""


class PresenceTracker:
    def __init__(self, hold_s: float = 3.0):
        self.hold_s = hold_s
        self._last_adult = -1e9
        self._last_child = -1e9
        self._alone_since: float | None = None

    def update(self, n_adults: int, n_children: int, now: float) -> None:
        if n_adults > 0:
            self._last_adult = now
        if n_children > 0:
            self._last_child = now
        if self.child_present(now) and not self.adult_present(now):
            if self._alone_since is None:
                self._alone_since = now
        else:
            self._alone_since = None

    def adult_present(self, now: float) -> bool:
        return now - self._last_adult < self.hold_s

    def child_present(self, now: float) -> bool:
        return now - self._last_child < self.hold_s

    def alone_seconds(self, now: float) -> float:
        return 0.0 if self._alone_since is None else now - self._alone_since
