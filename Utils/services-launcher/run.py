#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════════════════════════╗
║            Proyecto Taller Desarrollo — Service Launcher             ║
║                                                                      ║
║  Project structure expected:                                         ║
║                                                                      ║
║  Proyecto Taller Desarrollo/                                         ║
║  ├── Backend/                        ← browse to this                ║
║  │   ├── api-gateway/    (order 1)                                   ║
║  │   ├── service-registry/ (order 2)                                 ║
║  │   ├── access-control-service/ (order 3)                           ║
║  │   ├── identity-service/ (order 4)                                 ║
║  │   ├── notification-service/ (order 5)                             ║
║  │   └── visit-calendar-service/ (order 8)                           ║
║  └── Frontend/                       ← browse to this                ║
║      └── package.json  (npm run dev, order 9)                        ║
║                                                                      ║
║  Adding a new microservice                                           ║
║  ─────────────────────────                                           ║
║  1. Create  services/my_service.py                                   ║
║  2. Define  SERVICE = { "id": "my-service", "label": "My Service",   ║
║                         "type": "maven", "color": BLUE, "order": 7 } ║
║  3. Run the launcher — it appears automatically, no other changes.   ║
╚══════════════════════════════════════════════════════════════════════╝
"""

import sys
import os

# Make sure all sub-packages are importable when run from any working directory
sys.path.insert(0, os.path.dirname(__file__))

from app.launcher import LauncherApp

if __name__ == "__main__":
    LauncherApp().run()
