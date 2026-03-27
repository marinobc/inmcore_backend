"""
ServiceCard — the UI row widget for a single service.
Depends on: core.colors, core.fonts, core.process
"""

import subprocess
import threading
import platform
import tkinter as tk
from tkinter import scrolledtext
from datetime import datetime

from core.colors import *
from core.process import build_command


class ServiceCard:
    """
    Renders one labelled row with status dot, Start/Stop/Logs buttons.
    Registers itself with the app's FontManager for live size updates.
    """

    def __init__(self, parent: tk.Widget, service: dict, app):
        self.service  = service
        self.app      = app          # LauncherApp instance
        self.process  = None
        self.running  = False
        self._log_buf: list[str] = []

        # ── outer frame ───────────────────────────────────────────────────────
        self.frame = tk.Frame(parent, bg=BG2,
                              highlightbackground=BORDER, highlightthickness=1)
        self.frame.pack(fill="x", padx=12, pady=3)

        # left accent bar (service colour)
        tk.Frame(self.frame, bg=service["color"], width=4).pack(side="left", fill="y")

        inner = tk.Frame(self.frame, bg=BG2)
        inner.pack(side="left", fill="both", expand=True, padx=10, pady=7)

        top = tk.Frame(inner, bg=BG2)
        top.pack(fill="x")

        # status dot
        self.dot = tk.Label(top, text="●", fg=RED, bg=BG2,
                            font=("Courier", app.fonts.ui_size + 3, "bold"))
        self.dot.pack(side="left", padx=(0, 6))

        # service name
        self.name_lbl = tk.Label(top, text=service["label"], fg=FG, bg=BG2,
                                 font=("Courier", app.fonts.ui_size, "bold"))
        self.name_lbl.pack(side="left")

        # status text
        self.status_lbl = tk.Label(top, text="STOPPED", fg=RED, bg=BG2,
                                   font=("Courier", app.fonts.ui_size - 1))
        self.status_lbl.pack(side="left", padx=10)

        # buttons
        btn_frame = tk.Frame(top, bg=BG2)
        btn_frame.pack(side="right")

        self.start_btn = self._btn(btn_frame, "▶ Start", GREEN, self.start)
        self.stop_btn  = self._btn(btn_frame, "■ Stop",  RED,   self.stop, state="disabled")
        self.logs_btn  = self._btn(btn_frame, "≡ Logs",  BLUE,  self.show_logs)

        # register for live UI font updates
        app.fonts.register_ui(self._on_ui_font)

    # ── widget helpers ────────────────────────────────────────────────────────
    def _btn(self, parent, text, color, cmd, state="normal"):
        b = tk.Button(
            parent, text=text, command=cmd, state=state,
            bg=BG3, fg=color,
            font=("Courier", self.app.fonts.ui_size - 1, "bold"),
            relief="flat", cursor="hand2", padx=8, pady=3,
            activebackground=BORDER, activeforeground=color,
            disabledforeground=BORDER, highlightthickness=0, borderwidth=0,
        )
        b.pack(side="left", padx=2)
        return b

    @staticmethod
    def sz_btn(parent, text, cmd):
        """Reusable small +/− font-size button (also used by LauncherApp)."""
        tk.Button(
            parent, text=text, command=cmd,
            bg=BG3, fg=FG, font=("Courier", 9, "bold"),
            relief="flat", cursor="hand2", padx=6, pady=1,
            activebackground=BORDER, highlightthickness=0, borderwidth=0,
        ).pack(side="left", padx=1)

    def _on_ui_font(self, sz: int):
        self.dot.config(font=("Courier", sz + 3, "bold"))
        self.name_lbl.config(font=("Courier", sz, "bold"))
        self.status_lbl.config(font=("Courier", sz - 1))
        for btn in (self.start_btn, self.stop_btn, self.logs_btn):
            btn.config(font=("Courier", sz - 1, "bold"))

    # ── running state ─────────────────────────────────────────────────────────
    def _set_running(self, yes: bool):
        self.running = yes
        color = GREEN if yes else RED
        label = "RUNNING" if yes else "STOPPED"
        self.dot.config(fg=color)
        self.status_lbl.config(text=label, fg=color)
        self.start_btn.config(state="disabled" if yes else "normal")
        self.stop_btn.config(state="normal"    if yes else "disabled")

    # ── log buffer ────────────────────────────────────────────────────────────
    def append_log(self, line: str):
        ts = datetime.now().strftime("%H:%M:%S")
        self._log_buf.append(f"[{ts}] {line}")
        if len(self._log_buf) > 2000:
            self._log_buf = self._log_buf[-2000:]

    # ── process control ───────────────────────────────────────────────────────
    def start(self):
        try:
            cmd, cwd = build_command(
                self.service, self.app.backend_root, self.app.frontend_root
            )
        except ValueError as e:
            self.app.global_log(f"⚠  {e}")
            return

        if not cwd.exists():
            self.app.global_log(f"⚠  Path not found: {cwd}")
            return

        try:
            self.process = subprocess.Popen(
                cmd, cwd=str(cwd),
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1,
                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
                              if platform.system() == "Windows" else 0,
            )
        except FileNotFoundError as e:
            self.app.global_log(f"⚠  Could not launch {self.service['label']}: {e}")
            return

        self._set_running(True)
        self.app.global_log(f"▶  {self.service['label']} started (PID {self.process.pid})")
        threading.Thread(target=self._stream_output, daemon=True).start()

    def _stream_output(self):
        for line in self.process.stdout:
            line = line.rstrip()
            self.append_log(line)
            self.app.global_log(f"[{self.service['label']}] {line}")
        self.process.wait()
        self.app.global_log(
            f"{'■' if self.running else '✓'}  {self.service['label']} "
            f"exited (code {self.process.returncode})"
        )
        self.process = None
        self.app.root.after(0, lambda: self._set_running(False))

    def stop(self):
        if self.process:
            try:
                if platform.system() == "Windows":
                    subprocess.call(["taskkill", "/F", "/T", "/PID", str(self.process.pid)])
                else:
                    self.process.terminate()
            except Exception as e:
                self.app.global_log(f"⚠  Stop error for {self.service['label']}: {e}")
        self._set_running(False)
        self.app.global_log(f"■  {self.service['label']} stopped.")

    # ── log window ────────────────────────────────────────────────────────────
    def show_logs(self):
        win = tk.Toplevel(self.app.root)
        win.title(f"Logs — {self.service['label']}")
        win.configure(bg=BG)
        win.geometry("860x520")

        tk.Frame(win, bg=self.service["color"], height=3).pack(fill="x")

        hdr = tk.Frame(win, bg=BG)
        hdr.pack(fill="x", padx=10, pady=(8, 2))

        title_lbl = tk.Label(
            hdr, text=f"  {self.service['label']}  •  log output",
            fg=FG, bg=BG,
            font=("Courier", self.app.fonts.log_size + 1, "bold"),
            anchor="w",
        )
        title_lbl.pack(side="left")

        # font controls for this log window
        ctrl = tk.Frame(hdr, bg=BG)
        ctrl.pack(side="right")

        log_sz_var = tk.IntVar(value=self.app.fonts.log_size)
        tk.Label(ctrl, text="font:", fg=FG_DIM, bg=BG,
                 font=("Courier", 8)).pack(side="left", padx=(0, 4))
        self.sz_btn(ctrl, "−", lambda: self.app.fonts.change_log(-1))
        tk.Label(ctrl, textvariable=log_sz_var, fg=FG, bg=BG,
                 font=("Courier", 9), width=2).pack(side="left")
        self.sz_btn(ctrl, "+", lambda: self.app.fonts.change_log(+1))

        txt = scrolledtext.ScrolledText(
            win, bg=BG2, fg=FG_DIM,
            font=("Courier", self.app.fonts.log_size),
            relief="flat", borderwidth=0,
            insertbackground=FG, selectbackground=BORDER, wrap="none",
            highlightbackground=BORDER, highlightthickness=1,
        )
        txt.pack(fill="both", expand=True, padx=10, pady=(4, 10))
        txt.insert("end", "\n".join(self._log_buf) or "(no output yet)")
        txt.config(state="disabled")

        def _on_log_font(sz):
            txt.config(font=("Courier", sz))
            title_lbl.config(font=("Courier", sz + 1, "bold"))
            log_sz_var.set(sz)

        self.app.fonts.register_log(_on_log_font)
