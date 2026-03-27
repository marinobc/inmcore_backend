# ── font size bounds & manager ────────────────────────────────────────────────
UI_SIZE_MIN,  UI_SIZE_MAX,  UI_SIZE_DEF  = 7, 20, 10
LOG_SIZE_MIN, LOG_SIZE_MAX, LOG_SIZE_DEF = 7, 20,  8


class FontManager:
    """
    Holds current UI and log font sizes and notifies registered
    callbacks whenever either value changes.

    Usage
    -----
    fm = FontManager()
    fm.register_ui(lambda sz: my_label.config(font=("Courier", sz)))
    fm.register_log(lambda sz: my_text.config(font=("Courier", sz)))
    fm.change_ui(+1)   # all UI callbacks fired with new size
    fm.change_log(-1)  # all log callbacks fired with new size
    """

    def __init__(self):
        self.ui_size  = UI_SIZE_DEF
        self.log_size = LOG_SIZE_DEF
        self._ui_cbs:  list = []
        self._log_cbs: list = []

    def register_ui(self,  cb): self._ui_cbs.append(cb)
    def register_log(self, cb): self._log_cbs.append(cb)

    def change_ui(self, delta: int):
        self.ui_size = max(UI_SIZE_MIN, min(UI_SIZE_MAX, self.ui_size + delta))
        for cb in self._ui_cbs:
            try: cb(self.ui_size)
            except Exception: pass

    def change_log(self, delta: int):
        self.log_size = max(LOG_SIZE_MIN, min(LOG_SIZE_MAX, self.log_size + delta))
        for cb in self._log_cbs:
            try: cb(self.log_size)
            except Exception: pass
