"""
Helpers to build subprocess commands for Maven and Node services.
Cross-platform: Windows uses cmd /c, Linux/macOS calls mvnw or npm directly.
"""

import platform
import shutil
from pathlib import Path


def get_maven_cmd(cwd: Path) -> str:
    """Return the best maven wrapper / binary for a given service directory."""
    if platform.system() == "Windows":
        mvnw = cwd / "mvnw.cmd"
        return str(mvnw) if mvnw.exists() else "mvn"
    mvnw = cwd / "mvnw"
    return "./mvnw" if mvnw.exists() else "mvn"


def build_command(service: dict, backend_root: Path | None, frontend_root: Path | None):
    """
    Build (cmd_list, cwd_path) for a service definition dict.

    Raises ValueError if the required root folder has not been selected yet.
    """
    svc_type = service.get("type", "maven")

    if svc_type == "maven":
        if not backend_root:
            raise ValueError("Backend folder not selected.")
        cwd = backend_root / service["id"]
        mvn = get_maven_cmd(cwd)
        if platform.system() == "Windows":
            return ["cmd", "/c", mvn, "spring-boot:run"], cwd
        return [mvn, "spring-boot:run"], cwd

    elif svc_type == "node":
        if not frontend_root:
            raise ValueError("Frontend folder not selected.")
        npm_script = service.get("npm_script", "dev")
        if platform.system() == "Windows":
            return ["cmd", "/c", "npm", "run", npm_script], frontend_root
        npm = shutil.which("npm") or "npm"
        return [npm, "run", npm_script], frontend_root

    else:
        raise ValueError(f"Unknown service type: '{svc_type}'")
