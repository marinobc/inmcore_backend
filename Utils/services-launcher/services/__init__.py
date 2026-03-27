"""
Auto-discovers every SERVICE dict defined in this package.
To add a new microservice, just drop a new .py file here with a SERVICE dict.
The launcher will pick it up automatically, sorted by SERVICE["order"].
"""

import importlib
import pkgutil
from pathlib import Path

def load_services() -> list[dict]:
    """Import all modules in services/ and collect their SERVICE dicts."""
    services = []
    package_path = Path(__file__).parent
    package_name = __name__          # "services"

    for finder, module_name, _ in pkgutil.iter_modules([str(package_path)]):
        full_name = f"{package_name}.{module_name}"
        module = importlib.import_module(full_name)
        if hasattr(module, "SERVICE"):
            services.append(module.SERVICE)

    # sort by "order" key (default 99 if omitted)
    services.sort(key=lambda s: s.get("order", 99))
    return services
