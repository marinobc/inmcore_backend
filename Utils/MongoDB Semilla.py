#!/usr/bin/env python3
"""
seed.py — Bootstrap script for inmobiliaria_db.

Seeded data:
  Master admin  : admin@admin / admin  (permanent, no forced change)
  Testing users : 10 users covering all roles (password: password)
    - 2 admins      → admin1@admin,      admin2@admin
    - 2 agents      → agent1@user,       agent2@user
    - 2 owners      → owner1@user,       owner2@user
    - 2 clients     → client1@user,      client2@user
    - 1 supervisor  → supervisor1@user   (custom role)
    - 1 viewer      → viewer1@user       (custom role)
  Custom roles  : 2 CUSTOM roles for QA coverage
    - SUPERVISOR  (operational management, no roles/audit access)
    - VIEWER      (read-only across all main resources)
"""

import os
import sys
from datetime import datetime, timezone

import bcrypt
from pymongo import MongoClient, ASCENDING
from pymongo.errors import PyMongoError

# =========================================================
# CONFIG
# =========================================================
MONGO_URI: str = os.getenv(
    "MONGO_URI",
    "mongodb://admin:admin@localhost:27017/inmobiliaria_db?authSource=admin",
)
DB_NAME: str = os.getenv("MONGO_DB_NAME", "inmobiliaria_db")

BOOTSTRAP_ADMIN_EMAIL: str = os.getenv("BOOTSTRAP_ADMIN_EMAIL", "admin@admin")
BOOTSTRAP_ADMIN_PASSWORD: str = os.getenv("BOOTSTRAP_ADMIN_PASSWORD", "admin")

# All testing users share this password.
TESTING_PASSWORD: str = os.getenv("TESTING_PASSWORD", "password")

BCRYPT_ROUNDS: int = int(os.getenv("BCRYPT_ROUNDS", "12"))

# If true, the master admin's hash is regenerated on every re-seed.
ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED: bool = (
    os.getenv("ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED", "false").lower() == "true"
)

# If true, skips the interactive confirmation prompt (handy for CI/CD).
SEED_FORCE_CLEAR: bool = os.getenv("SEED_FORCE_CLEAR", "false").lower() == "true"

SYSTEM_ACTOR: str = "system"

COLLECTIONS_TO_CLEAR: list[str] = [
    "permissions_catalog",
    "roles",
    "users",
    "audit_events",
    "employment_cycles",
    "password_reset_tokens",
    "refresh_tokens",
]

COLLECTIONS_WITH_INDEXES: list[str] = [
    "permissions_catalog",
    "roles",
    "users",
    "employment_cycles",
    "password_reset_tokens",
    "refresh_tokens",
    "audit_events",
]


# =========================================================
# HELPERS
# =========================================================
def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def bcrypt_hash(password: str) -> str:
    return bcrypt.hashpw(
        password.encode("utf-8"),
        bcrypt.gensalt(rounds=BCRYPT_ROUNDS),
    ).decode("utf-8")


def make_user(
    *,
    _id: str,
    first: str,
    last: str,
    email: str,
    password_hash: str,
    user_type: str,
    role_codes: list[str],  # Changed from role_ids to role_codes
    role_id_map: dict,  # Map of role_code -> role_id
    current_time: datetime,
) -> dict:
    """Build a normalized user document ready for upsert."""
    # Convert role codes to actual role IDs using the map
    role_ids = [role_id_map[code] for code in role_codes if code in role_id_map]
    
    return {
        "_id": _id,
        "firstName": first,
        "lastName": last,
        "fullName": f"{first} {last}",
        "email": email,
        "emailNormalized": email.strip().lower(),
        "passwordHash": password_hash,
        "userType": user_type,
        "status": "ACTIVE",
        "temporaryPassword": False,
        "temporaryPasswordExpiresAt": None,
        "mustChangePassword": False,
        "passwordChangedAt": None,
        "failedLoginAttempts": 0,
        "lockedUntil": None,
        "lastLoginAt": None,
        "primaryRoleIds": role_ids,
        "primaryRoleCodes": role_codes,  # Store codes for easier debugging
        "activeEmploymentCycleId": None,
        "metadata": {"testing": True} if _id.startswith("usr_test") else {},
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
        "createdAt": current_time,
    }


def confirm_and_clear(db) -> None:
    if not SEED_FORCE_CLEAR:
        print("\n⚠️  WARNING ⚠️")
        print("This will DELETE ALL documents from the following collections:")
        for col in COLLECTIONS_TO_CLEAR:
            print(f"  - {col}")
        print(f"\n  Database: {DB_NAME}\n")
        answer = input("Continue? Type 'yes' to confirm: ").strip().lower()
        if answer != "yes":
            print("Operation cancelled.")
            sys.exit(0)
    else:
        print("SEED_FORCE_CLEAR=true — clearing without confirmation...")

    print("\nClearing collections...")
    for col_name in COLLECTIONS_TO_CLEAR:
        result = db[col_name].delete_many({})
        print(f"  ✓ {col_name}: {result.deleted_count} document(s) deleted")
    print("Clear complete.\n")


def drop_collection_indexes(db) -> None:
    print("Removing existing indexes...")
    for col_name in COLLECTIONS_WITH_INDEXES:
        col = db[col_name]
        non_default = [idx["name"] for idx in col.list_indexes() if idx["name"] != "_id_"]
        for idx_name in non_default:
            col.drop_index(idx_name)
            print(f"  ✓ {col_name}.{idx_name} dropped")
    print("Indexes removed.\n")


def ensure_indexes(db) -> None:
    # permissions_catalog
    db.permissions_catalog.create_index(
        [("resource", ASCENDING), ("action", ASCENDING), ("scope", ASCENDING)],
        unique=True,
        name="uk_permission_catalog_resource_action_scope",
    )

    # roles
    db.roles.create_index([("code", ASCENDING)], unique=True, name="uk_roles_code")
    db.roles.create_index([("name", ASCENDING)], unique=True, name="uk_roles_name")

    # users
    db.users.create_index(
        [("emailNormalized", ASCENDING)], unique=True, name="uk_users_email_normalized"
    )
    db.users.create_index([("status", ASCENDING)], name="idx_users_status")
    db.users.create_index(
        [("activeEmploymentCycleId", ASCENDING)], name="idx_users_active_employment_cycle_id"
    )
    db.users.create_index(
        [("primaryRoleCodes", ASCENDING)], name="idx_users_primary_role_codes"
    )

    # employment_cycles
    db.employment_cycles.create_index(
        [("userId", ASCENDING), ("employmentStatus", ASCENDING)],
        name="idx_employment_cycles_user_status",
    )

    # password_reset_tokens — TTL
    db.password_reset_tokens.create_index(
        [("expiresAt", ASCENDING)],
        expireAfterSeconds=0,
        name="ttl_password_reset_tokens_expires_at",
    )

    # refresh_tokens — TTL
    db.refresh_tokens.create_index(
        [("expiresAt", ASCENDING)],
        expireAfterSeconds=0,
        name="ttl_refresh_tokens_expires_at",
    )

    # audit_events
    db.audit_events.create_index([("eventType", ASCENDING)], name="idx_audit_events_event_type")
    db.audit_events.create_index([("occurredAt", ASCENDING)], name="idx_audit_events_occurred_at")


def upsert_many_by_id(collection, documents: list[dict], current_time: datetime) -> int:
    """Upsert documents by _id."""
    count = 0
    for doc in documents:
        _id = doc["_id"]
        payload = {k: v for k, v in doc.items() if k != "createdAt"}
        payload["updatedAt"] = current_time
        created_at = doc.get("createdAt", current_time)

        collection.update_one(
            {"_id": _id},
            {"$set": payload, "$setOnInsert": {"createdAt": created_at}},
            upsert=True,
        )
        count += 1

    return count


# =========================================================
# DATA BUILDERS — PERMISSIONS
# =========================================================
def build_permissions(current_time: datetime) -> list[dict]:
    definitions = [
        # AUTH
        ("perm_auth_login_any",                 "auth",                "login",           "ANY",      "Login"),
        ("perm_auth_refresh_any",               "auth",                "refresh",         "ANY",      "Refresh token"),
        ("perm_auth_logout_any",                "auth",                "logout",          "ANY",      "Logout"),
        ("perm_auth_change_password_own",       "auth",                "change_password", "OWN",      "Change own password"),
        ("perm_auth_forgot_password_any",       "auth",                "forgot_password", "ANY",      "Request password reset"),
        ("perm_auth_reset_password_any",        "auth",                "reset_password",  "ANY",      "Reset password with token"),

        # USERS
        ("perm_users_create_any",               "users",               "create",          "ANY",      "Create users"),
        ("perm_users_read_any",                 "users",               "read",            "ANY",      "Read any user"),
        ("perm_users_read_own",                 "users",               "read",            "OWN",      "Read own profile"),
        ("perm_users_update_any",               "users",               "update",          "ANY",      "Update any user"),
        ("perm_users_update_own",               "users",               "update",          "OWN",      "Update own profile"),
        ("perm_users_assign_role_any",          "users",               "assign_role",     "ANY",      "Assign roles to users"),
        ("perm_users_change_status_any",        "users",               "change_status",   "ANY",      "Change user status"),

        # ROLES
        ("perm_roles_create_any",               "roles",               "create",          "ANY",      "Create roles"),
        ("perm_roles_read_any",                 "roles",               "read",            "ANY",      "Read roles"),
        ("perm_roles_update_any",               "roles",               "update",          "ANY",      "Update roles"),
        ("perm_roles_delete_any",               "roles",               "delete",          "ANY",      "Delete roles"),

        # ADMIN / AUDIT
        ("perm_admin_dashboard_read_any",       "admin_dashboard",     "read",            "ANY",      "Access admin dashboard"),
        ("perm_audit_read_any",                 "audit",               "read",            "ANY",      "Read system audit"),

        # PROPERTIES
        ("perm_properties_create_any",          "properties",          "create",          "ANY",      "Create properties"),
        ("perm_properties_read_any",            "properties",          "read",            "ANY",      "Read any property"),
        ("perm_properties_read_assigned",       "properties",          "read",            "ASSIGNED", "Read assigned properties"),
        ("perm_properties_read_own",            "properties",          "read",            "OWN",      "Read own properties"),
        ("perm_properties_read_matched",        "properties",          "read",            "MATCHED",  "Read matched properties"),
        ("perm_properties_update_any",          "properties",          "update",          "ANY",      "Update any property"),
        ("perm_properties_update_assigned",     "properties",          "update",          "ASSIGNED", "Update assigned properties"),

        # PROPERTY PRICE / ASSIGNMENT
        ("perm_property_price_set_initial_any", "property_price",      "set_initial",     "ANY",      "Set initial price"),
        ("perm_property_price_change_any",      "property_price",      "change",          "ANY",      "Change price"),
        ("perm_property_assignment_assign_any", "property_assignment", "assign",          "ANY",      "Assign property to agent"),
        ("perm_property_assignment_reassign_any","property_assignment","reassign",        "ANY",      "Reassign property"),

        # CONTRACTS
        ("perm_contracts_read_any",             "contracts",           "read",            "ANY",      "Read any contract"),
        ("perm_contracts_read_own",             "contracts",           "read",            "OWN",      "Read own contracts"),
        ("perm_contracts_read_assigned",        "contracts",           "read",            "ASSIGNED", "Read assigned contracts"),

        # CLIENT PREFERENCES / MATCHES
        ("perm_client_preferences_read_own",    "client_preferences",  "read",            "OWN",      "Read own preferences"),
        ("perm_client_preferences_update_own",  "client_preferences",  "update",          "OWN",      "Update own preferences"),
        ("perm_matches_read_own",               "matches",             "read",            "OWN",      "Read own matches"),

        # CALENDAR
        ("perm_calendar_events_read_any",        "calendar_events",    "read",            "ANY",      "Read any calendar events"),
        ("perm_calendar_events_read_own",        "calendar_events",    "read",            "OWN",      "Read own events"),
        ("perm_calendar_events_read_assigned",   "calendar_events",    "read",            "ASSIGNED", "Read assigned events"),
        ("perm_calendar_events_create_any",      "calendar_events",    "create",          "ANY",      "Create calendar events"),
        ("perm_calendar_events_update_any",      "calendar_events",    "update",          "ANY",      "Update any calendar events"),
        ("perm_calendar_events_update_assigned", "calendar_events",    "update",          "ASSIGNED", "Update assigned events"),

        # RESOURCES / BUDGETS
        ("perm_resources_read_any",             "resources",           "read",            "ANY",      "Read resources"),
        ("perm_resources_read_assigned",        "resources",           "read",            "ASSIGNED", "Read assigned resources"),
        ("perm_resources_assign_any",           "resources",           "assign",          "ANY",      "Assign resources"),
        ("perm_resources_reassign_any",         "resources",           "reassign",        "ANY",      "Reassign resources"),
        ("perm_budgets_read_any",               "budgets",             "read",            "ANY",      "Read budgets"),
        ("perm_budgets_read_assigned",          "budgets",             "read",            "ASSIGNED", "Read assigned budgets"),
        ("perm_budgets_assign_any",             "budgets",             "assign",          "ANY",      "Assign budget"),
        ("perm_budgets_approve_any",            "budgets",             "approve",         "ANY",      "Approve budget"),
    ]

    return [
        {
            "_id": _id,
            "resource": resource,
            "action": action,
            "scope": scope,
            "description": description,
            "active": True,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        }
        for _id, resource, action, scope, description in definitions
    ]


# =========================================================
# DATA BUILDERS — ROLES
# =========================================================
def build_roles(current_time: datetime) -> list[dict]:

    # ── SYSTEM roles ──────────────────────────────────────────────────────────
    role_admin_permissions = [
        {"resource": "auth",                "action": "change_password", "scope": "OWN"},
        {"resource": "users",               "action": "create",          "scope": "ANY"},
        {"resource": "users",               "action": "read",            "scope": "ANY"},
        {"resource": "users",               "action": "update",          "scope": "ANY"},
        {"resource": "users",               "action": "assign_role",     "scope": "ANY"},
        {"resource": "users",               "action": "change_status",   "scope": "ANY"},
        {"resource": "roles",               "action": "create",          "scope": "ANY"},
        {"resource": "roles",               "action": "read",            "scope": "ANY"},
        {"resource": "roles",               "action": "update",          "scope": "ANY"},
        {"resource": "roles",               "action": "delete",          "scope": "ANY"},
        {"resource": "admin_dashboard",     "action": "read",            "scope": "ANY"},
        {"resource": "audit",               "action": "read",            "scope": "ANY"},
        {"resource": "properties",          "action": "create",          "scope": "ANY"},
        {"resource": "properties",          "action": "read",            "scope": "ANY"},
        {"resource": "properties",          "action": "update",          "scope": "ANY"},
        {"resource": "property_price",      "action": "set_initial",     "scope": "ANY"},
        {"resource": "property_price",      "action": "change",          "scope": "ANY"},
        {"resource": "property_assignment", "action": "assign",          "scope": "ANY"},
        {"resource": "property_assignment", "action": "reassign",        "scope": "ANY"},
        {"resource": "contracts",           "action": "read",            "scope": "ANY"},
        {"resource": "calendar_events",     "action": "read",            "scope": "ANY"},
        {"resource": "calendar_events",     "action": "create",          "scope": "ANY"},
        {"resource": "calendar_events",     "action": "update",          "scope": "ANY"},
        {"resource": "resources",           "action": "read",            "scope": "ANY"},
        {"resource": "resources",           "action": "assign",          "scope": "ANY"},
        {"resource": "resources",           "action": "reassign",        "scope": "ANY"},
        {"resource": "budgets",             "action": "read",            "scope": "ANY"},
        {"resource": "budgets",             "action": "assign",          "scope": "ANY"},
        {"resource": "budgets",             "action": "approve",         "scope": "ANY"},
    ]

    role_agent_permissions = [
        {"resource": "auth",            "action": "change_password", "scope": "OWN"},
        {"resource": "users",           "action": "read",            "scope": "OWN"},
        {"resource": "users",           "action": "update",          "scope": "OWN"},
        {"resource": "properties",      "action": "create",          "scope": "ANY"},
        {"resource": "properties",      "action": "read",            "scope": "ASSIGNED"},
        {"resource": "properties",      "action": "update",          "scope": "ASSIGNED"},
        {"resource": "property_price",  "action": "set_initial",     "scope": "ANY"},
        {"resource": "contracts",       "action": "read",            "scope": "ASSIGNED"},
        {"resource": "calendar_events", "action": "read",            "scope": "ASSIGNED"},
        {"resource": "calendar_events", "action": "update",          "scope": "ASSIGNED"},
        {"resource": "resources",       "action": "read",            "scope": "ASSIGNED"},
        {"resource": "budgets",         "action": "read",            "scope": "ASSIGNED"},
    ]

    role_owner_permissions = [
        {"resource": "auth",       "action": "change_password", "scope": "OWN"},
        {"resource": "users",      "action": "read",            "scope": "OWN"},
        {"resource": "users",      "action": "update",          "scope": "OWN"},
        {"resource": "properties", "action": "read",            "scope": "OWN"},
        {"resource": "contracts",  "action": "read",            "scope": "OWN"},
    ]

    role_interested_permissions = [
        {"resource": "auth",               "action": "change_password", "scope": "OWN"},
        {"resource": "users",              "action": "read",            "scope": "OWN"},
        {"resource": "users",              "action": "update",          "scope": "OWN"},
        {"resource": "client_preferences", "action": "read",            "scope": "OWN"},
        {"resource": "client_preferences", "action": "update",          "scope": "OWN"},
        {"resource": "matches",            "action": "read",            "scope": "OWN"},
        {"resource": "properties",         "action": "read",            "scope": "MATCHED"},
    ]

    # ── CUSTOM roles (QA / testing) ───────────────────────────────────────────
    role_supervisor_permissions = [
        {"resource": "auth",                "action": "change_password", "scope": "OWN"},
        {"resource": "users",               "action": "read",            "scope": "ANY"},
        {"resource": "users",               "action": "update",          "scope": "ANY"},
        {"resource": "users",               "action": "change_status",   "scope": "ANY"},
        {"resource": "admin_dashboard",     "action": "read",            "scope": "ANY"},
        {"resource": "properties",          "action": "create",          "scope": "ANY"},
        {"resource": "properties",          "action": "read",            "scope": "ANY"},
        {"resource": "properties",          "action": "update",          "scope": "ANY"},
        {"resource": "property_price",      "action": "set_initial",     "scope": "ANY"},
        {"resource": "property_price",      "action": "change",          "scope": "ANY"},
        {"resource": "property_assignment", "action": "assign",          "scope": "ANY"},
        {"resource": "property_assignment", "action": "reassign",        "scope": "ANY"},
        {"resource": "contracts",           "action": "read",            "scope": "ANY"},
        {"resource": "calendar_events",     "action": "read",            "scope": "ANY"},
        {"resource": "calendar_events",     "action": "create",          "scope": "ANY"},
        {"resource": "calendar_events",     "action": "update",          "scope": "ANY"},
        {"resource": "resources",           "action": "read",            "scope": "ANY"},
        {"resource": "budgets",             "action": "read",            "scope": "ANY"},
        {"resource": "budgets",             "action": "approve",         "scope": "ANY"},
    ]

    role_viewer_permissions = [
        {"resource": "auth",            "action": "change_password", "scope": "OWN"},
        {"resource": "users",           "action": "read",            "scope": "ANY"},
        {"resource": "roles",           "action": "read",            "scope": "ANY"},
        {"resource": "properties",      "action": "read",            "scope": "ANY"},
        {"resource": "contracts",       "action": "read",            "scope": "ANY"},
        {"resource": "calendar_events", "action": "read",            "scope": "ANY"},
        {"resource": "resources",       "action": "read",            "scope": "ANY"},
        {"resource": "budgets",         "action": "read",            "scope": "ANY"},
        {"resource": "audit",           "action": "read",            "scope": "ANY"},
    ]

    return [
        # ── System ────────────────────────────────────────────────────────────
        {
            "_id": "rol_admin",
            "code": "ADMIN",
            "name": "Administrator",
            "description": "Full system access",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_admin_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_agent",
            "code": "AGENT",
            "name": "Agent",
            "description": "Operational role for real estate agents",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_agent_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_owner",
            "code": "OWNER",
            "name": "Property Owner",
            "description": "Property owner client",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_owner_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_interested_client",
            "code": "INTERESTED_CLIENT",
            "name": "Interested Client",
            "description": "Client with preferences and matches",
            "type": "SYSTEM",
            "active": True,
            "permissions": role_interested_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        # ── Custom / Testing ──────────────────────────────────────────────────
        {
            "_id": "rol_supervisor",
            "code": "SUPERVISOR",
            "name": "Supervisor Testing",
            "description": "Custom testing: operational management without roles/audit",
            "type": "CUSTOM",
            "active": True,
            "permissions": role_supervisor_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
        {
            "_id": "rol_viewer",
            "code": "VIEWER",
            "name": "Viewer Testing",
            "description": "Custom testing: read-only on all main resources",
            "type": "CUSTOM",
            "active": True,
            "permissions": role_viewer_permissions,
            "version": 1,
            "createdBy": SYSTEM_ACTOR,
            "createdAt": current_time,
        },
    ]


# =========================================================
# DATA BUILDERS — TESTING USERS
# =========================================================
def build_testing_users(current_time: datetime, role_id_map: dict) -> list[dict]:
    """
    10 testing users, one shared bcrypt hash (TESTING_PASSWORD).
    Uses role codes which are mapped to actual role IDs.
    """
    ph = bcrypt_hash(TESTING_PASSWORD)

    return [
        # ── Admins ────────────────────────────────────────────────────────────
        make_user(_id="usr_test_admin_01",      first="Testing Admin",      last="One",
                  email="admin1@admin",          password_hash=ph, user_type="ADMIN",
                  role_codes=["ADMIN"],          role_id_map=role_id_map, current_time=current_time),

        make_user(_id="usr_test_admin_02",      first="Testing Admin",      last="Two",
                  email="admin2@admin",          password_hash=ph, user_type="ADMIN",
                  role_codes=["ADMIN"],          role_id_map=role_id_map, current_time=current_time),

        # ── Agents ────────────────────────────────────────────────────────────
        make_user(_id="usr_test_agent_01",      first="Testing Agent",      last="One",
                  email="agent1@user",           password_hash=ph, user_type="EMPLOYEE",
                  role_codes=["AGENT"],          role_id_map=role_id_map, current_time=current_time),

        make_user(_id="usr_test_agent_02",      first="Testing Agent",      last="Two",
                  email="agent2@user",           password_hash=ph, user_type="EMPLOYEE",
                  role_codes=["AGENT"],          role_id_map=role_id_map, current_time=current_time),

        # ── Owners ────────────────────────────────────────────────────────────
        make_user(_id="usr_test_owner_01",      first="Testing Owner",      last="One",
                  email="owner1@user",           password_hash=ph, user_type="OWNER",
                  role_codes=["OWNER"],          role_id_map=role_id_map, current_time=current_time),

        make_user(_id="usr_test_owner_02",      first="Testing Owner",      last="Two",
                  email="owner2@user",           password_hash=ph, user_type="OWNER",
                  role_codes=["OWNER"],          role_id_map=role_id_map, current_time=current_time),

        # ── Interested clients ────────────────────────────────────────────────
        make_user(_id="usr_test_client_01",     first="Testing Client",     last="One",
                  email="client1@user",          password_hash=ph, user_type="INTERESTED_CLIENT",
                  role_codes=["INTERESTED_CLIENT"], role_id_map=role_id_map, current_time=current_time),

        make_user(_id="usr_test_client_02",     first="Testing Client",     last="Two",
                  email="client2@user",          password_hash=ph, user_type="INTERESTED_CLIENT",
                  role_codes=["INTERESTED_CLIENT"], role_id_map=role_id_map, current_time=current_time),

        # ── Custom-role users ─────────────────────────────────────────────────
        make_user(_id="usr_test_supervisor_01", first="Testing Supervisor",  last="One",
                  email="supervisor1@user",      password_hash=ph, user_type="EMPLOYEE",
                  role_codes=["SUPERVISOR"],    role_id_map=role_id_map, current_time=current_time),

        make_user(_id="usr_test_viewer_01",     first="Testing Viewer",      last="One",
                  email="viewer1@user",          password_hash=ph, user_type="EMPLOYEE",
                  role_codes=["VIEWER"],        role_id_map=role_id_map, current_time=current_time),
    ]


# =========================================================
# BOOTSTRAP ADMIN
# =========================================================
def seed_bootstrap_admin(db, current_time: datetime, role_id_map: dict) -> dict:
    existing = db.users.find_one({"_id": "usr_admin_001"})
    admin_existed = existing is not None

    # Preserve the existing hash unless rotation is explicitly requested.
    if admin_existed and not ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED:
        password_hash = existing.get("passwordHash") or bcrypt_hash(BOOTSTRAP_ADMIN_PASSWORD)
    else:
        password_hash = bcrypt_hash(BOOTSTRAP_ADMIN_PASSWORD)

    # Get the ADMIN role ID
    admin_role_id = role_id_map.get("ADMIN")
    if not admin_role_id:
        raise Exception("ADMIN role not found in role_id_map!")

    admin_user = {
        "_id": "usr_admin_001",
        "firstName": "Super",
        "lastName": "Admin",
        "fullName": "Super Admin",
        "email": BOOTSTRAP_ADMIN_EMAIL,
        "emailNormalized": BOOTSTRAP_ADMIN_EMAIL.strip().lower(),
        "passwordHash": password_hash,
        "userType": "ADMIN",
        "status": "ACTIVE",
        "temporaryPassword": False,
        "temporaryPasswordExpiresAt": None,
        "mustChangePassword": False,
        "passwordChangedAt": None,
        "failedLoginAttempts": 0,
        "lockedUntil": None,
        "lastLoginAt": None,
        "primaryRoleIds": [admin_role_id],
        "primaryRoleCodes": ["ADMIN"],
        "activeEmploymentCycleId": None,
        "metadata": {},
        "updatedAt": current_time,
        "createdBy": SYSTEM_ACTOR,
    }

    db.users.update_one(
        {"_id": admin_user["_id"]},
        {"$set": admin_user, "$setOnInsert": {"createdAt": current_time}},
        upsert=True,
    )

    db.audit_events.update_one(
        {"_id": "aud_bootstrap_admin_created"},
        {
            "$set": {
                "eventType": "USER_CREATED",
                "actorUserId": SYSTEM_ACTOR,
                "targetType": "USER",
                "targetId": "usr_admin_001",
                "data": {
                    "roleIds": [admin_role_id],
                    "roleCodes": ["ADMIN"],
                    "bootstrap": True,
                    "email": BOOTSTRAP_ADMIN_EMAIL,
                    "passwordRotatedOnReseed": ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED,
                },
                "occurredAt": current_time,
            }
        },
        upsert=True,
    )

    return {
        "admin_existed": admin_existed,
        "email": BOOTSTRAP_ADMIN_EMAIL,
        "password": BOOTSTRAP_ADMIN_PASSWORD,
        "password_rotated": (not admin_existed) or ROTATE_BOOTSTRAP_ADMIN_PASSWORD_ON_RESEED,
    }


# =========================================================
# VERIFICATION
# =========================================================
def verify_seed(db) -> None:
    """Verify that all critical data was seeded correctly."""
    print("\n" + "="*58)
    print("  SEED VERIFICATION")
    print("="*58)
    
    # Check roles
    roles_count = db.roles.count_documents({})
    print(f"Roles: {roles_count} (expected: 6)")
    
    # Check master admin
    master_admin = db.users.find_one({"_id": "usr_admin_001"})
    if master_admin:
        role_ids = master_admin.get("primaryRoleIds", [])
        role_codes = master_admin.get("primaryRoleCodes", [])
        print(f"Master Admin: FOUND")
        print(f"  - Role IDs: {role_ids}")
        print(f"  - Role Codes: {role_codes}")
        
        # Verify the role actually exists
        if role_ids:
            role = db.roles.find_one({"_id": role_ids[0]})
            if role:
                print(f"  - Role exists: {role.get('code')} ✓")
            else:
                print(f"  - ❌ Role ID {role_ids[0]} not found in roles collection!")
    else:
        print("❌ Master Admin NOT FOUND!")
    
    # Check test users
    test_users = db.users.count_documents({"_id": {"$regex": "^usr_test_"}})
    print(f"Test Users: {test_users} (expected: 10)")
    
    print("="*58 + "\n")


# =========================================================
# SUMMARY
# =========================================================
_TESTING_USERS_TABLE: list[tuple[str, str, str]] = [
    ("Admin 1",          "admin1@admin",      "ADMIN"),
    ("Admin 2",          "admin2@admin",      "ADMIN"),
    ("Agent 1",          "agent1@user",       "AGENT"),
    ("Agent 2",          "agent2@user",       "AGENT"),
    ("Owner 1",          "owner1@user",       "OWNER"),
    ("Owner 2",          "owner2@user",       "OWNER"),
    ("Client 1",         "client1@user",      "INTERESTED_CLIENT"),
    ("Client 2",         "client2@user",      "INTERESTED_CLIENT"),
    ("Supervisor 1",     "supervisor1@user",  "SUPERVISOR (custom)"),
    ("Viewer 1",         "viewer1@user",      "VIEWER (custom)"),
]


def print_summary(
    db,
    admin_info: dict,
    permissions_count: int,
    roles_count: int,
    total_users: int,
) -> None:
    sep = "=" * 58
    print(f"\n{sep}")
    print("  SEED COMPLETED")
    print(sep)
    print(f"  DB           : {DB_NAME}")
    print(f"  Mongo URI    : {MONGO_URI}")
    print(f"  permissions  : {db.permissions_catalog.count_documents({})}")
    print(f"  roles        : {db.roles.count_documents({})}")
    print(f"  users        : {db.users.count_documents({})}")
    print(f"  audit_events : {db.audit_events.count_documents({})}")
    print("-" * 58)
    print(f"  Permissions upserted : {permissions_count}")
    print(f"  Roles upserted       : {roles_count}  (4 system + 2 custom)")
    print(f"  Users upserted       : {total_users}  (1 master + 10 testing)")
    print("-" * 58)
    print("  Master admin (permanent credentials):")
    print(f"    email    : {admin_info['email']}")
    if admin_info["password_rotated"]:
        print(f"    password : {admin_info['password']}")
    else:
        print("    password : not rotated (existing hash preserved)")
    print(f"    existed  : {admin_info['admin_existed']}")
    print("-" * 58)
    print(f"  Testing users  (shared password: {TESTING_PASSWORD})")
    print(f"  {'Label':<16} {'Email':<24} {'Role'}")
    print(f"  {'-'*15} {'-'*23} {'-'*22}")
    for label, email, role in _TESTING_USERS_TABLE:
        print(f"  {label:<16} {email:<24} {role}")
    print(sep + "\n")


# =========================================================
# MAIN
# =========================================================
def main() -> None:
    current_time = now_utc()
    client = None

    try:
        print("Connecting to MongoDB...")
        client = MongoClient(MONGO_URI)
        client.admin.command("ping")
        print("Connection OK.\n")

        db = client[DB_NAME]

        confirm_and_clear(db)
        drop_collection_indexes(db)

        print("Creating indexes...")
        ensure_indexes(db)
        print("Indexes created.\n")

        # STEP 1: Seed permissions FIRST
        print("Seeding permissions catalog...")
        permissions = build_permissions(current_time)
        permissions_count = upsert_many_by_id(db.permissions_catalog, permissions, current_time)
        print(f"  ✓ {permissions_count} permission(s) processed\n")

        # STEP 2: Seed roles SECOND (so they exist for users)
        print("Seeding roles (system + custom)...")
        roles = build_roles(current_time)
        roles_count = upsert_many_by_id(db.roles, roles, current_time)
        
        # Build a map of role_code -> role_id for user creation
        role_id_map = {}
        for role in roles:
            role_id_map[role["code"]] = role["_id"]
        print(f"  ✓ {roles_count} role(s) processed")
        print(f"  ✓ Role ID map built: {role_id_map}\n")

        # STEP 3: Seed master admin THIRD (now roles exist)
        print("Seeding master admin user...")
        admin_info = seed_bootstrap_admin(db, current_time, role_id_map)
        print("  ✓ Master admin processed\n")

        # STEP 4: Seed testing users LAST
        print("Seeding testing users...")
        testing_users = build_testing_users(current_time, role_id_map)
        testing_count = upsert_many_by_id(db.users, testing_users, current_time)
        print(f"  ✓ {testing_count} testing user(s) processed\n")

        # STEP 5: Verify everything
        verify_seed(db)

        # STEP 6: Print summary
        print_summary(db, admin_info, permissions_count, roles_count, 1 + testing_count)

    except PyMongoError as exc:
        print(f"MongoDB Error: {exc}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(f"Unexpected error: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        if client is not None:
            client.close()


if __name__ == "__main__":
    main()