"""
LauncherApp — main window.
Builds the header, path selectors, action bar, service cards, and console.
"""

import tkinter as tk
from tkinter import filedialog, scrolledtext
from pathlib import Path
from datetime import datetime

from core.colors import *
from core.fonts import FontManager
from core.card  import ServiceCard
from services   import load_services


class LauncherApp:
    def __init__(self):
        self.backend_root:  Path | None = None
        self.frontend_root: Path | None = None
        self.cards: list[ServiceCard]   = []
        self.fonts = FontManager()

        self.root = tk.Tk()
        self.root.title("Proyecto Taller — Service Launcher")
        self.root.configure(bg=BG)
        self.root.geometry("860x840")
        self.root.minsize(720, 640)

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    # ── top-level build ───────────────────────────────────────────────────────
    def _build_ui(self):
        self._build_header()
        self._build_path_selectors()
        self._build_action_bar()
        self._build_cards()
        self._build_console()
        self.global_log(
            "Launcher ready. Select Backend & Frontend folders, then ▶▶ Start All."
        )

    # ── header ────────────────────────────────────────────────────────────────
    def _build_header(self):
        header = tk.Frame(self.root, bg=BG2,
                          highlightbackground=BORDER, highlightthickness=1)
        header.pack(fill="x")

        left = tk.Frame(header, bg=BG2)
        left.pack(side="left", padx=14, pady=10)

        tk.Label(left, text="⬡", fg=GREEN, bg=BG2,
                 font=("Courier", 20)).pack(side="left", padx=(0, 8))
        self._h_title = tk.Label(left, text="PROYECTO TALLER", fg=FG, bg=BG2,
                                 font=("Courier", self.fonts.ui_size + 3, "bold"))
        self._h_title.pack(side="left")
        self._h_sub = tk.Label(left, text=" / service launcher", fg=FG_DIM, bg=BG2,
                               font=("Courier", self.fonts.ui_size))
        self._h_sub.pack(side="left")

        # UI font controls — top right
        ctrl = tk.Frame(header, bg=BG2)
        ctrl.pack(side="right", padx=12)

        tk.Label(ctrl, text="UI font:", fg=FG_DIM, bg=BG2,
                 font=("Courier", 8)).pack(side="left", padx=(0, 4))
        self._ui_sz_var = tk.IntVar(value=self.fonts.ui_size)

        ServiceCard.sz_btn(ctrl, "−", lambda: self.fonts.change_ui(-1))
        tk.Label(ctrl, textvariable=self._ui_sz_var, fg=FG, bg=BG2,
                 font=("Courier", 9), width=2).pack(side="left")
        ServiceCard.sz_btn(ctrl, "+", lambda: self.fonts.change_ui(+1))

        def _hdr_font(sz):
            self._h_title.config(font=("Courier", sz + 3, "bold"))
            self._h_sub.config(font=("Courier", sz))
            self._ui_sz_var.set(sz)

        self.fonts.register_ui(_hdr_font)

    # ── path selectors ────────────────────────────────────────────────────────
    def _build_path_selectors(self):
        outer = tk.Frame(self.root, bg=BG)
        outer.pack(fill="x", padx=12, pady=(10, 2))

        self._backend_var  = tk.StringVar(value="(not selected)")
        self._frontend_var = tk.StringVar(value="(not selected)")

        self._make_path_row(outer, "BACKEND FOLDER:",  ORANGE, self._backend_var,
                            lambda: self._browse("backend"),  pady_top=0)
        self._make_path_row(outer, "FRONTEND FOLDER:", GREEN,  self._frontend_var,
                            lambda: self._browse("frontend"), pady_top=4)

    def _make_path_row(self, parent, label_text, color, var, cmd, pady_top=0):
        row = tk.Frame(parent, bg=BG3,
                       highlightbackground=BORDER, highlightthickness=1)
        row.pack(fill="x", pady=(pady_top, 0))

        tk.Frame(row, bg=color, width=3).pack(side="left", fill="y")

        lbl = tk.Label(row, text=f"  {label_text}", fg=FG_DIM, bg=BG3,
                       font=("Courier", self.fonts.ui_size - 1, "bold"),
                       width=18, anchor="w")
        lbl.pack(side="left", pady=6)

        path_lbl = tk.Label(row, textvariable=var, fg=CYAN, bg=BG3,
                            font=("Courier", self.fonts.ui_size - 1), anchor="w")
        path_lbl.pack(side="left", fill="x", expand=True, padx=4)

        browse_btn = tk.Button(
            row, text="📂 Browse", command=cmd,
            bg=BG2, fg=FG, font=("Courier", self.fonts.ui_size - 1),
            relief="flat", cursor="hand2", padx=8, pady=4,
            activebackground=BORDER, highlightthickness=0, borderwidth=0,
        )
        browse_btn.pack(side="right", padx=6, pady=4)

        def _font(sz):
            lbl.config(font=("Courier", sz - 1, "bold"))
            path_lbl.config(font=("Courier", sz - 1))
            browse_btn.config(font=("Courier", sz - 1))

        self.fonts.register_ui(_font)

    # ── action bar ────────────────────────────────────────────────────────────
    def _build_action_bar(self):
        bar = tk.Frame(self.root, bg=BG)
        bar.pack(fill="x", padx=12, pady=8)

        self._action_btns = []
        for text, color, cmd in [
            ("▶▶ Start All",   GREEN,  self._start_all),
            ("■■ Stop All",    RED,    self._stop_all),
            ("↻  Restart All", YELLOW, self._restart_all),
        ]:
            b = tk.Button(
                bar, text=text, command=cmd,
                bg=BG3, fg=color,
                font=("Courier", self.fonts.ui_size, "bold"),
                relief="flat", cursor="hand2", padx=14, pady=6,
                activebackground=BORDER, activeforeground=color,
                highlightthickness=0, borderwidth=0,
            )
            b.pack(side="left", padx=(0, 6))
            self._action_btns.append(b)

        self.fonts.register_ui(lambda sz: [
            b.config(font=("Courier", sz, "bold")) for b in self._action_btns
        ])

    # ── service cards (auto-loaded) ───────────────────────────────────────────
    def _build_cards(self):
        self._svc_lbl = tk.Label(
            self.root, text="  SERVICES", fg=FG_DIM, bg=BG,
            font=("Courier", self.fonts.ui_size - 1, "bold"), anchor="w",
        )
        self._svc_lbl.pack(fill="x", padx=12, pady=(2, 0))
        self.fonts.register_ui(
            lambda sz: self._svc_lbl.config(font=("Courier", sz - 1, "bold"))
        )

        cards_frame = tk.Frame(self.root, bg=BG)
        cards_frame.pack(fill="x")

        for svc in load_services():          # ← auto-discovers services/
            self.cards.append(ServiceCard(cards_frame, svc, self))

    # ── console ───────────────────────────────────────────────────────────────
    def _build_console(self):
        log_hdr = tk.Frame(self.root, bg=BG)
        log_hdr.pack(fill="x", padx=12, pady=(8, 2))

        self._console_lbl = tk.Label(
            log_hdr, text="CONSOLE", fg=FG_DIM, bg=BG,
            font=("Courier", self.fonts.ui_size - 1, "bold"),
        )
        self._console_lbl.pack(side="left")

        # log font controls
        ctrl = tk.Frame(log_hdr, bg=BG)
        ctrl.pack(side="left", padx=14)

        tk.Label(ctrl, text="log font:", fg=FG_DIM, bg=BG,
                 font=("Courier", 8)).pack(side="left", padx=(0, 4))
        self._log_sz_var = tk.IntVar(value=self.fonts.log_size)

        ServiceCard.sz_btn(ctrl, "−", lambda: self.fonts.change_log(-1))
        tk.Label(ctrl, textvariable=self._log_sz_var, fg=FG, bg=BG,
                 font=("Courier", 9), width=2).pack(side="left")
        ServiceCard.sz_btn(ctrl, "+", lambda: self.fonts.change_log(+1))

        clear_btn = tk.Button(
            log_hdr, text="✕ Clear", command=self._clear_log,
            bg=BG, fg=FG_DIM,
            font=("Courier", self.fonts.ui_size - 2),
            relief="flat", cursor="hand2", padx=6,
            activebackground=BORDER, highlightthickness=0, borderwidth=0,
        )
        clear_btn.pack(side="right")

        self.log_box = scrolledtext.ScrolledText(
            self.root, bg=BG2, fg=FG_DIM,
            font=("Courier", self.fonts.log_size),
            relief="flat", borderwidth=0, height=11,
            insertbackground=FG, selectbackground=BORDER, wrap="none",
            highlightbackground=BORDER, highlightthickness=1,
        )
        self.log_box.pack(fill="both", expand=True, padx=12, pady=(0, 12))
        self.log_box.config(state="disabled")
        self.log_box.tag_config("INFO",  foreground=FG_DIM)
        self.log_box.tag_config("START", foreground=GREEN)
        self.log_box.tag_config("STOP",  foreground=RED)
        self.log_box.tag_config("WARN",  foreground=YELLOW)

        self.fonts.register_ui(lambda sz: (
            self._console_lbl.config(font=("Courier", sz - 1, "bold")),
            clear_btn.config(font=("Courier", sz - 2)),
        ))
        self.fonts.register_log(lambda sz: (
            self.log_box.config(font=("Courier", sz)),
            self._log_sz_var.set(sz),
        ))

    # ── browse ────────────────────────────────────────────────────────────────
    def _browse(self, target: str):
        titles = {
            "backend":  "Select Backend folder (contains api-gateway, service-registry, …)",
            "frontend": "Select Frontend folder (contains package.json)",
        }
        path = filedialog.askdirectory(title=titles[target])
        if not path:
            return
        if target == "backend":
            self.backend_root = Path(path)
            self._backend_var.set(str(self.backend_root))
            self.global_log(f"📂 Backend  → {self.backend_root}")
        else:
            self.frontend_root = Path(path)
            self._frontend_var.set(str(self.frontend_root))
            self.global_log(f"📂 Frontend → {self.frontend_root}")

    # ── global actions ────────────────────────────────────────────────────────
    def _start_all(self):
        if not self.backend_root or not self.frontend_root:
            self.global_log("⚠  Please select both Backend and Frontend folders first.")
            return
        for i, card in enumerate(self.cards):
            self.root.after(i * 500, lambda c=card: c.start() if not c.running else None)

    def _stop_all(self):
        for card in reversed(self.cards):
            if card.running:
                card.stop()

    def _restart_all(self):
        self._stop_all()
        self.root.after(1500, self._start_all)

    # ── logging ───────────────────────────────────────────────────────────────
    def global_log(self, msg: str):
        def _write():
            self.log_box.config(state="normal")
            ts  = datetime.now().strftime("%H:%M:%S")
            tag = ("START" if msg.startswith("▶") else
                   "STOP"  if msg.startswith("■") else
                   "WARN"  if msg.startswith("⚠") else "INFO")
            self.log_box.insert("end", f"[{ts}] {msg}\n", tag)
            self.log_box.see("end")
            self.log_box.config(state="disabled")
        self.root.after(0, _write)

    def _clear_log(self):
        self.log_box.config(state="normal")
        self.log_box.delete("1.0", "end")
        self.log_box.config(state="disabled")

    # ── shutdown ──────────────────────────────────────────────────────────────
    def _on_close(self):
        for card in self.cards:
            if card.running:
                card.stop()
        self.root.destroy()

    def run(self):
        self.root.mainloop()
